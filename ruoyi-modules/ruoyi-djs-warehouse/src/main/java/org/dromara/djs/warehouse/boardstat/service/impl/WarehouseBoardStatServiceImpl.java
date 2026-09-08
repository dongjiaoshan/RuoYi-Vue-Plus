package org.dromara.djs.warehouse.boardstat.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.boardstat.domain.vo.BoardStatDetailVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.BoardStatProductRowVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.BoardStatUnitTotalVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.CategoryStatVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.CategoryUnitQtyRow;
import org.dromara.djs.warehouse.boardstat.domain.vo.CategoryUnitStatVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.WarehouseBoardStatVo;
import org.dromara.djs.warehouse.boardstat.mapper.WarehouseBoardStatMapper;
import org.dromara.djs.warehouse.boardstat.service.IWarehouseBoardStatService;
import org.dromara.djs.warehouse.flow.constant.FlowDisplayScope;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * mp 仓库统计（品类 × 单位 × 三指标 + 环比）与卡片下钻明细实现。
 *
 * <p>卡片顺序：猪肉 / 果蔬 / 蛋类 / 干货 / 其他产品。</p>
 *
 * <p><b>当月没有数据的东西一律不下发</b>（甲方 2026-09-08：「产品没有数据时，不显示对应的内容，
 * 即，没有时，就不显示任何内容」），<b>卡级与行级同规则、都只看当月</b>：</p>
 * <ul>
 *   <li><b>卡级</b>：本卡当月三指标在所有单位上都是 0 → 整卡不下发；全部品类都无数据时返回空列表，
 *       由 mp 渲染整页空态。</li>
 *   <li><b>行级</b>：某单位当月三指标全 0 → 该单位行不下发，<b>上月有数也不救它</b>
 *       （见 {@link #buildCard}）。</li>
 * </ul>
 * <p>两级必须同规则：只做卡级会让同一张卡里同时出现「整卡因当月无数据而消失」和「某行因上月有数
 * 而留下一排 0 与 −100.00%」，自相矛盾，后者正是甲方 row202 截图里圈的形态。口径与门店侧
 * {@code StoreManageServiceImpl}（卡级 + {@code buildRows} 行级）逐条对齐 —— 同一句甲方原话
 * 不允许两个页面各实现一套。</p>
 *
 * <p>mp 侧 {@code stat-rows.ts#metricCells} 是<b>另一层</b>（同一行内只渲染有数的指标格），
 * 与行级过滤不重复也不互相替代，保留。</p>
 *
 * <p>猪肉卡合并 {@code pork + white_bar}：白条是猪肉在燎毛 / 分割链路上的过程形态，
 * 分开成两张卡会让同一头猪的入库与生产被劈开读。</p>
 *
 * <p>卡内按<b>单位</b>分行：同品类下多种单位（猪肉 kg 原料 + 份装成品）各占一行，
 * 一行内三个指标同量纲。行序按单位名升序 —— 月月稳定，甲方逐月对读时行不会跳位。</p>
 *
 * <h3>下钻明细为什么和卡片必然对得上</h3>
 * <p>明细弹窗上方的合计 {@code totals} <b>不另写 SQL</b>，直接调卡片那两个聚合方法
 * （{@code selectInboundByCategoryUnit} / {@code selectProduceByCategoryUnit}），
 * 只是把品类白名单收窄到点开的那一张卡；明细行（V6-R193 起按<b>产品</b>聚合）与卡片聚合共用
 * mapper 里的 {@code *_FROM} / {@code *_WHERE} 片段，GROUP BY 只细一档。
 * 两条路径的筛选条件是同一份，改一处两边同时生效。</p>
 *
 * @author djs
 * @since V6-R178
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseBoardStatServiceImpl implements IWarehouseBoardStatService {

    private static final String DEFAULT_TENANT = "1001";

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 环比百分比小数位。 */
    private static final int RATIO_SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 单位缺失时的显示值（与卡片一致，否则明细合计的行头对不上卡片行头）。 */
    private static final String UNIT_UNKNOWN = "未标单位";

    /**
     * 卡片定义：categoryKey → (中文名, 归入该卡的 djs_belong_type 集合)。
     * LinkedHashMap 定死渲染顺序（猪肉 / 果蔬 / 蛋类 / 干货 / 其他产品）。
     *
     * <p>「其他产品」卡按 {@code belong_type='other'} 统计，三个指标与其余卡完全同一套 SQL 与口径
     * （甲方 2026-09-08：「增加【其他产品】的信息版块，统计逻辑和展示保持一致」），排在干货之后。</p>
     */
    private static final Map<String, CategoryDef> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("pork", new CategoryDef("猪肉产品", List.of("pork", "white_bar")));
        CATEGORIES.put("vegetable", new CategoryDef("果蔬产品", List.of("vegetable")));
        CATEGORIES.put("egg", new CategoryDef("蛋类产品", List.of("egg")));
        CATEGORIES.put("dry_good", new CategoryDef("干货产品", List.of("dry_good")));
        CATEGORIES.put("other", new CategoryDef("其他产品", List.of("other")));
    }

    /** 三条 SQL 共用的品类白名单（所有卡涉及的全部 belong_type）。 */
    private static final List<String> BELONG_TYPES = CATEGORIES.values().stream()
        .flatMap(d -> d.belongTypes().stream())
        .toList();

    /** belong_type → categoryKey 反查（pork / white_bar 都指向 pork 卡）。 */
    private static final Map<String, String> BELONG_TO_CATEGORY = new LinkedHashMap<>();

    static {
        CATEGORIES.forEach((key, def) -> def.belongTypes().forEach(b -> BELONG_TO_CATEGORY.put(b, key)));
    }

    private final WarehouseBoardStatMapper boardStatMapper;

    @Override
    public WarehouseBoardStatVo getCategoryStat(String month) {
        String tenantId = currentTenant();
        YearMonth ym = parseMonth(month);
        YearMonth prev = ym.minusMonths(1);

        WarehouseBoardStatVo vo = new WarehouseBoardStatVo();
        vo.setMonth(ym.format(MONTH_FMT));
        vo.setPrevMonth(prev.format(MONTH_FMT));

        // 单位展示原文：categoryKey → (合并键 → 原文)，**按品类隔离**（见 putLabel）。
        // 本月与上月共用一份：同一个单位在两个月里字面大小写不同也只留一行。
        Map<String, Map<String, String>> unitLabels = new HashMap<>();
        MonthMetrics cur = loadMonth(tenantId, ym, unitLabels);
        MonthMetrics pre = loadMonth(tenantId, prev, unitLabels);

        // 本月一点数都没有的品类整张卡不返回（甲方 2026-09-08「没有时，就不显示任何内容」）；
        // 上月数据只用来算环比，不能让一张本月空卡靠上月的量活下来。
        List<CategoryStatVo> cards = new ArrayList<>();
        CATEGORIES.forEach((key, def) -> {
            if (cur.hasAnyData(key)) {
                cards.add(buildCard(key, def, cur, pre, unitLabels.getOrDefault(key, Map.of())));
            }
        });
        vo.setCategories(cards);
        return vo;
    }

    @Override
    public BoardStatDetailVo getInboundDetail(String month, String belongType) {
        String tenantId = currentTenant();
        YearMonth ym = parseMonthStrict(month);
        YearMonth prev = ym.minusMonths(1);
        CategoryDef def = requireCategory(belongType);

        List<BoardStatProductRowVo> cur = boardStatMapper.selectInboundDetailByProduct(
            tenantId, def.belongTypes(), FlowDisplayScope.IN_EXCLUDED, ym.atDay(1), ym.plusMonths(1).atDay(1));
        List<BoardStatProductRowVo> pre = boardStatMapper.selectInboundDetailByProduct(
            tenantId, def.belongTypes(), FlowDisplayScope.IN_EXCLUDED, prev.atDay(1), prev.plusMonths(1).atDay(1));

        // 合计走卡片那条聚合 SQL，品类白名单收窄到本卡 —— 数字与卡片同源，不可能对不上
        List<BoardStatUnitTotalVo> totals = toTotals(boardStatMapper.selectInboundByCategoryUnit(
            tenantId, def.belongTypes(), FlowDisplayScope.IN_EXCLUDED, ym.atDay(1), ym.plusMonths(1).atDay(1)));

        return buildDetail(ym, prev, belongType, def, withRatio(cur, pre), totals);
    }

    @Override
    public BoardStatDetailVo getProductionDetail(String month, String belongType) {
        String tenantId = currentTenant();
        YearMonth ym = parseMonthStrict(month);
        YearMonth prev = ym.minusMonths(1);
        CategoryDef def = requireCategory(belongType);

        List<BoardStatProductRowVo> cur = boardStatMapper.selectProduceDetailByProduct(
            tenantId, def.belongTypes(), ym.atDay(1), ym.plusMonths(1).atDay(1));
        List<BoardStatProductRowVo> pre = boardStatMapper.selectProduceDetailByProduct(
            tenantId, def.belongTypes(), prev.atDay(1), prev.plusMonths(1).atDay(1));

        List<BoardStatUnitTotalVo> totals = toTotals(boardStatMapper.selectProduceByCategoryUnit(
            tenantId, def.belongTypes(), ym.atDay(1), ym.plusMonths(1).atDay(1)));

        return buildDetail(ym, prev, belongType, def, withRatio(cur, pre), totals);
    }

    /**
     * 本月产品行 ← 上月同口径行，逐产品算环比。
     *
     * <p>只列<b>本月有量</b>的产品（甲方 V6-R193「显示统计月所有入库/生产产品」）——
     * 上月有、本月没有的产品不补空行：那不是「这个月的产品」，塞进来会让弹窗里的行数
     * 与「本月一共上了几个产品」对不上。</p>
     *
     * <p>对齐键取 productId + 单位，不只取 productId：生产侧的单位可能落在生产记录上
     * （产品档案没填单位时 {@code PRODUCE_UNIT_EXPR} 退回 {@code pp.product_unit}），
     * 同一产品跨月换了单位时按 productId 硬配会把两种量纲的数相除。</p>
     *
     * @param cur 本月行（SQL 原样，unit / spec 尚未归一）
     * @param pre 上月行
     * @return 本月行（已归一 + 带 prevQty / ratio）
     */
    private List<BoardStatProductRowVo> withRatio(List<BoardStatProductRowVo> cur,
                                                  List<BoardStatProductRowVo> pre) {
        Map<String, BigDecimal> prevQty = new HashMap<>();
        if (pre != null) {
            for (BoardStatProductRowVo row : pre) {
                prevQty.merge(ratioKey(row), row.getQty() == null ? BigDecimal.ZERO : row.getQty(),
                    BigDecimal::add);
            }
        }
        List<BoardStatProductRowVo> rows = cur == null ? List.of() : cur;
        for (BoardStatProductRowVo row : rows) {
            row.setUnit(unitLabel(row.getUnit()));
            row.setProductSpec(row.getProductSpec() == null ? "" : row.getProductSpec().trim());
            if (row.getQty() == null) {
                row.setQty(BigDecimal.ZERO);
            }
            BigDecimal previous = prevQty.getOrDefault(ratioKey(row), BigDecimal.ZERO);
            row.setPrevQty(previous);
            row.setRatio(ratio(row.getQty(), previous));
        }
        return rows;
    }

    /**
     * 环比对齐键：产品 + 单位<b>合并键</b>（小写）。
     *
     * <p>走小写键而不是展示原文：同一产品跨月单位字面大小写不同（{@code Kg} / {@code kg}）时，
     * 按原文对齐会认成两个东西、环比全变 null。</p>
     */
    private String ratioKey(BoardStatProductRowVo row) {
        return row.getProductId() + "|" + unitKey(row.getUnit());
    }

    private BoardStatDetailVo buildDetail(YearMonth ym, YearMonth prev, String belongType, CategoryDef def,
                                          List<BoardStatProductRowVo> rows, List<BoardStatUnitTotalVo> totals) {
        BoardStatDetailVo vo = new BoardStatDetailVo();
        vo.setMonth(ym.format(MONTH_FMT));
        vo.setPrevMonth(prev.format(MONTH_FMT));
        vo.setBelongType(belongType);
        vo.setCategoryName(def.name());
        vo.setRows(rows);
        vo.setTotals(totals);
        return vo;
    }

    /**
     * 聚合行 → 明细弹窗顶部合计（按单位名升序，与卡片行序一致，甲方逐行对读时不会串行）。
     *
     * <p>入参恒是单张卡的聚合结果，同一单位可能因 pork / white_bar 两个 belong_type 各出一行，
     * 这里按单位再合并一次（与卡片 {@code index()} 的合并规则相同）。</p>
     *
     * @param rows 卡片聚合方法的原始行
     * @return 按单位合计
     */
    private List<BoardStatUnitTotalVo> toTotals(List<CategoryUnitQtyRow> rows) {
        Map<String, BigDecimal> merged = new TreeMap<>();
        Map<String, String> unitLabels = new HashMap<>();
        if (rows != null) {
            for (CategoryUnitQtyRow row : rows) {
                BigDecimal qty = row.getQty() == null ? BigDecimal.ZERO : row.getQty();
                String key = unitKey(row.getProductUnit());
                // 入参恒是单张卡的聚合结果 → 这份 map 天然按品类隔离；取字面走同一条确定性规则，
                // 卡片与本弹窗对同一个单位必然显示同一个字面（见 putLabel）
                putLabel(unitLabels, key, unitLabel(row.getProductUnit()));
                merged.merge(key, qty, BigDecimal::add);
            }
        }
        List<BoardStatUnitTotalVo> totals = new ArrayList<>(merged.size());
        merged.forEach((key, qty) -> {
            BoardStatUnitTotalVo vo = new BoardStatUnitTotalVo();
            vo.setUnit(unitLabels.getOrDefault(key, key));
            vo.setQty(qty);
            totals.add(vo);
        });
        return totals;
    }

    /**
     * 品类白名单校验：只认 {@link #CATEGORIES} 里的 key。
     *
     * <p>非法值直接 400 而不是返空列表 —— 返空会让前端以为「这个月真没数据」，
     * 而实际是链接拼错了，这种错静默下去没人能发现。</p>
     *
     * @param belongType 入参品类键
     * @return 品类定义
     */
    private CategoryDef requireCategory(String belongType) {
        CategoryDef def = belongType == null ? null : CATEGORIES.get(belongType.trim());
        if (def == null) {
            throw new ServiceException("品类不合法，只接受 " + CATEGORIES.keySet() + "：" + belongType, 400);
        }
        return def;
    }

    /**
     * 拉一个月的三份聚合，落成「categoryKey → 单位合并键 → 量」两级索引。
     *
     * @param tenantId   租户
     * @param ym         月份
     * @param unitLabels categoryKey → (单位合并键 → 展示原文)，就地填充，本月与上月共用一份
     * @return 三指标索引
     */
    private MonthMetrics loadMonth(String tenantId, YearMonth ym, Map<String, Map<String, String>> unitLabels) {
        LocalDate from = ym.atDay(1);
        LocalDate toExclusive = ym.plusMonths(1).atDay(1);
        return new MonthMetrics(
            index(boardStatMapper.selectInboundByCategoryUnit(
                tenantId, BELONG_TYPES, FlowDisplayScope.IN_EXCLUDED, from, toExclusive), unitLabels),
            index(boardStatMapper.selectProduceByCategoryUnit(
                tenantId, BELONG_TYPES, from, toExclusive), unitLabels),
            index(boardStatMapper.selectMaterialConsumeByCategoryUnit(
                tenantId, BELONG_TYPES, from, toExclusive), unitLabels));
    }

    /**
     * 投影行 → 「categoryKey → 单位 → 量」。
     *
     * <p>猪肉卡把 pork 与 white_bar 两个 belong_type 合到同一个 key，同单位的量在此相加。</p>
     */
    private Map<String, Map<String, BigDecimal>> index(List<CategoryUnitQtyRow> rows,
                                                       Map<String, Map<String, String>> unitLabels) {
        Map<String, Map<String, BigDecimal>> result = new LinkedHashMap<>();
        if (rows == null) {
            return result;
        }
        for (CategoryUnitQtyRow row : rows) {
            String categoryKey = BELONG_TO_CATEGORY.get(row.getBelongType());
            if (categoryKey == null) {
                continue;
            }
            String key = unitKey(row.getProductUnit());
            putLabel(unitLabels.computeIfAbsent(categoryKey, k -> new HashMap<>()),
                key, unitLabel(row.getProductUnit()));
            BigDecimal qty = row.getQty() == null ? BigDecimal.ZERO : row.getQty();
            result.computeIfAbsent(categoryKey, k -> new LinkedHashMap<>())
                .merge(key, qty, BigDecimal::add);
        }
        return result;
    }

    /**
     * 记录某个单位合并键的展示原文。
     *
     * <h3>两条要求，缺一个卡片与明细弹窗就会显示不同的字面</h3>
     * <ol>
     *   <li><b>按品类隔离</b>（调用方保证：传进来的 map 是某一张卡专属的）。共享一份会串味 ——
     *       实测 {@code Kg} 只有 {@code other} 品类的 2 个产品在用、其余 165 个产品都是小写 {@code kg}，
     *       共享时 {@code labels["kg"]} 被先到的 pork 占成小写，其他产品卡就显示 {@code kg}；
     *       而明细弹窗的 {@link #toTotals} 只吃这张卡自己的行、显示 {@code Kg} —— 同一个数两处字面不一样。</li>
     * </ol>
     *
     * <p><b>取值规则（确定性）</b>：同一个键有多种字面时，<b>优先取全小写那个</b>（即字面 == 合并键），
     * 都不是小写则取自然序最小的。<b>不是「先到先得」</b> —— 先到先得依赖 SQL 返回行序，
     * 而卡片走全品类那条聚合、明细走收窄品类的同一条聚合，两次行序 MySQL 不保证一致，
     * 那就又会两处显示不同的字面。</p>
     *
     * @param labels 该品类的「合并键 → 展示原文」（就地填充）
     * @param key    合并键（小写）
     * @param label  本行的展示原文
     */
    private static void putLabel(Map<String, String> labels, String key, String label) {
        labels.merge(key, label, (a, b) -> {
            if (a.equals(key) || b.equals(key)) {
                return a.equals(key) ? a : b;
            }
            return a.compareTo(b) <= 0 ? a : b;
        });
    }

    /**
     * 单位<b>合并键</b>：trim + 转小写；空 / 全空白统一成「未标单位」。
     *
     * <p>必须小写化：产品档案里同一个单位大小写混录（实测 {@code other} 品类既有 {@code Kg} 又可能有
     * {@code kg}），不归一会把同一张卡裂成两行、两行各自的量还都只是一半。与门店侧
     * {@code StoreManageServiceImpl#sumByUnit} 同一把尺子 —— 同一句甲方口径不允许两边各写一套。</p>
     *
     * @param unit 原始单位
     * @return 合并键（小写）
     */
    private String unitKey(String unit) {
        return unitLabel(unit).toLowerCase(Locale.ROOT);
    }

    /**
     * 单位<b>展示原文</b>：trim 后原样（保留大小写）；空 / 全空白统一成「未标单位」。
     *
     * <p>合并按 {@link #unitKey} 的小写键，展示原文由 {@link #putLabel} 按品类隔离地确定性选出
     * —— 键统一、字面不篡改。</p>
     *
     * @param unit 原始单位
     * @return 展示文案
     */
    private String unitLabel(String unit) {
        String u = unit == null ? "" : unit.trim();
        return u.isEmpty() ? UNIT_UNKNOWN : u;
    }

    /**
     * 单张品类卡：行 = <b>本月</b>出现过且三指标不全为 0 的单位，按单位名升序。
     *
     * <h3>行级也只看当月（D-0045，与卡级同一把尺子）</h3>
     * <p>甲方 2026-09-08「产品没有数据时，不显示对应的内容」落在两个层级：卡级（本卡当月三指标
     * 全 0 → 整卡不下发）与行级（本单位当月三指标全 0 → 该行不下发）。两级必须同规则 ——
     * 否则同一张卡里会同时出现「整卡因当月无数据而消失」和「某行因上月有数而留下一排 0 与 -100%」，
     * 自相矛盾，而后者正是甲方 row202 截图里圈的那个形态。口径与门店侧
     * {@code StoreManageServiceImpl#buildRows} 逐条对齐。</p>
     *
     * <h3>放弃了什么（完整版）</h3>
     * <p>上月有数、本月归零的单位行，连同它那个 -100.00% 的环比一起看不到了（卡若整个归零则整卡也看不到）。
     * 留下的行仍照常算环比。</p>
     *
     * <p>⚠️ 只做到这一步是<b>不够</b>的，而且会比不做更糟：某个指标的上月基数往往正好挂在被剔掉的那一行上
     * （实测干货生产量 8 月 13 份 → 9 月 0、其他产品生产量 8 月 7 只 → 9 月 0，都是真 −100%，
     * 而承载它的「份」/「只」行本月三指标全 0、被本方法剔除）。此时存活行没有该指标的上月基数、
     * {@code ratio} 为 null，mp 按约定把 null 渲染成 <b>0.00%</b> —— 屏幕上读起来是「与上月持平」。
     * 藏起来只是没信息，这个是<b>错信息</b>。</p>
     *
     * <p>所以行级过滤必须与 mp 侧 {@code stat-rows.ts#metricCells} 的 D-0050 改动<b>配套上线</b>：
     * 那边把「整列为零就一格都不渲染」落实之后，这些格子连同假 0.00% 一起消失，坑才算填上。
     * 两者是<b>不同层</b>、不互相替代：本方法决定「这一行出不出」，它决定「行里哪几格出」。</p>
     *
     * @param categoryKey 品类键
     * @param def         品类定义
     * @param cur         本月三指标
     * @param pre         上月三指标（只用来算环比，不参与「出不出行」的判断）
     * @param unitLabels  <b>本品类</b>的单位合并键 → 展示原文（见 {@link #putLabel}）
     * @return 品类卡
     */
    private CategoryStatVo buildCard(String categoryKey, CategoryDef def, MonthMetrics cur, MonthMetrics pre,
                                     Map<String, String> unitLabels) {
        CategoryStatVo card = new CategoryStatVo();
        card.setCategoryKey(categoryKey);
        card.setCategoryName(def.name());

        // 只取本月出现过的单位：只在上月出现的单位本月三项必为 0，下面那道过滤也会把它剔掉，
        // 这里不并进来，同时让「行级只看当月」在代码上一眼可见。
        // TreeSet 只为拿确定的单位序（合并键自然序），月月稳定、甲方逐月对读时行不会跳位。
        Set<String> units = new TreeSet<>(cur.unitsOf(categoryKey));

        List<CategoryUnitStatVo> rows = new ArrayList<>();
        for (String unit : units) {
            BigDecimal curIn = cur.inbound(categoryKey, unit);
            BigDecimal curProduce = cur.produce(categoryKey, unit);
            BigDecimal curMaterial = cur.material(categoryKey, unit);
            // D-0045 行级：只看当月三项，上月有数不构成显示理由
            if (curIn.signum() == 0 && curProduce.signum() == 0 && curMaterial.signum() == 0) {
                continue;
            }
            CategoryUnitStatVo row = new CategoryUnitStatVo();
            row.setUnit(unitLabels.getOrDefault(unit, unit));
            row.setInboundQty(curIn);
            row.setProduceQty(curProduce);
            row.setMaterialQty(curMaterial);
            row.setInboundRatio(ratio(curIn, pre.inbound(categoryKey, unit)));
            row.setProduceRatio(ratio(curProduce, pre.produce(categoryKey, unit)));
            row.setMaterialRatio(ratio(curMaterial, pre.material(categoryKey, unit)));
            rows.add(row);
        }
        card.setRows(rows);
        return card;
    }

    /**
     * 环比% = (本月 - 上月) / 上月 × 100。
     *
     * <p>上月为 0 / 无数据 → 返 null：除数为 0 算不出增幅，前端据此显示黑色 0.00%
     * （甲方 V6-R178 第 2~5 条明写「没有上个月数据的时候，比率显示为黑色的 0.00%」）。</p>
     */
    private BigDecimal ratio(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal cur = current == null ? BigDecimal.ZERO : current;
        return cur.subtract(previous)
            .multiply(HUNDRED)
            .divide(previous, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private String currentTenant() {
        try {
            String t = TenantHelper.getTenantId();
            return t == null || t.isEmpty() ? DEFAULT_TENANT : t;
        } catch (Exception e) {
            log.warn("[WarehouseBoardStat] 获取租户失败，回退默认租户", e);
            return DEFAULT_TENANT;
        }
    }

    /** 月份入参解析：yyyy-MM；空 / 非法格式回落当月（看板整屏不因入参脏就 500）。 */
    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_FMT);
        } catch (Exception e) {
            log.warn("[WarehouseBoardStat] 月份入参非法，回落当月：{}", month);
            return YearMonth.now();
        }
    }

    /**
     * 明细页的月份解析：空 = 当月，格式非法 → 400。
     *
     * <p>与看板整屏那条 {@link #parseMonth} 的宽松回落<b>刻意不同</b>：看板是「进页就有一屏数」，
     * 脏参数回落当月还看得下去；明细页是拿去和某张卡对数的，回落到别的月份等于给出一份
     * 看着合理、其实对不上的数，比直接报错危险得多。</p>
     *
     * @param month yyyy-MM
     * @return 年月
     */
    private YearMonth parseMonthStrict(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_FMT);
        } catch (Exception e) {
            throw new ServiceException("月份格式不合法，应为 yyyy-MM：" + month, 400);
        }
    }

    /** 品类卡定义。 */
    private record CategoryDef(String name, List<String> belongTypes) {
    }

    /** 单月三指标的两级索引（categoryKey → 单位 → 量）。 */
    private record MonthMetrics(Map<String, Map<String, BigDecimal>> inbound,
                                Map<String, Map<String, BigDecimal>> produce,
                                Map<String, Map<String, BigDecimal>> material) {

        BigDecimal inbound(String categoryKey, String unit) {
            return pick(inbound, categoryKey, unit);
        }

        BigDecimal produce(String categoryKey, String unit) {
            return pick(produce, categoryKey, unit);
        }

        BigDecimal material(String categoryKey, String unit) {
            return pick(material, categoryKey, unit);
        }

        /** 该品类在三个指标里出现过的全部单位。 */
        List<String> unitsOf(String categoryKey) {
            List<String> units = new ArrayList<>();
            units.addAll(inbound.getOrDefault(categoryKey, Map.of()).keySet());
            units.addAll(produce.getOrDefault(categoryKey, Map.of()).keySet());
            units.addAll(material.getOrDefault(categoryKey, Map.of()).keySet());
            return units;
        }

        /**
         * 该品类本月是否有任何数据 = 三个指标在任一单位上有非零量。
         *
         * <p>判「非零」而不是「有没有这一行」：产品档案在但整月没动的品类，SQL 也可能返回一行
         * {@code qty=0}（如某单位当月只有一笔量为 0 的生产记录），那仍然是「没有数据」。
         * 负数算有数——退库场景确实会是负的，与 mp 侧 {@code stat-rows.ts} 的 hasQty 同口径。</p>
         */
        boolean hasAnyData(String categoryKey) {
            return hasNonZero(inbound, categoryKey)
                || hasNonZero(produce, categoryKey)
                || hasNonZero(material, categoryKey);
        }

        private static boolean hasNonZero(Map<String, Map<String, BigDecimal>> src, String categoryKey) {
            return src.getOrDefault(categoryKey, Map.of()).values().stream()
                .anyMatch(v -> v != null && v.signum() != 0);
        }

        private static BigDecimal pick(Map<String, Map<String, BigDecimal>> src, String categoryKey, String unit) {
            return src.getOrDefault(categoryKey, Map.of()).getOrDefault(unit, BigDecimal.ZERO);
        }
    }
}
