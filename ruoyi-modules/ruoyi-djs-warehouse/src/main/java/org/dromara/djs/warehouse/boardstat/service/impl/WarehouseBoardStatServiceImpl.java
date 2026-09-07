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
import java.util.Map;
import java.util.TreeMap;

/**
 * mp 仓库统计（品类 × 单位 × 三指标 + 环比）与卡片下钻明细实现。
 *
 * <p>四张卡固定：猪肉 / 果蔬 / 蛋类 / 干货。<b>不出「其他产品」卡</b> —— 甲方 V6-R178 第 6 条
 * 「其他产品不显示」。</p>
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
     * LinkedHashMap 定死渲染顺序（猪肉 / 果蔬 / 蛋类 / 干货）。
     */
    private static final Map<String, CategoryDef> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("pork", new CategoryDef("猪肉产品", List.of("pork", "white_bar")));
        CATEGORIES.put("vegetable", new CategoryDef("果蔬产品", List.of("vegetable")));
        CATEGORIES.put("egg", new CategoryDef("蛋类产品", List.of("egg")));
        CATEGORIES.put("dry_good", new CategoryDef("干货产品", List.of("dry_good")));
    }

    /** 三条 SQL 共用的品类白名单（4 张卡涉及的全部 belong_type）。 */
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

        MonthMetrics cur = loadMonth(tenantId, ym);
        MonthMetrics pre = loadMonth(tenantId, prev);

        List<CategoryStatVo> cards = new ArrayList<>();
        CATEGORIES.forEach((key, def) -> cards.add(buildCard(key, def, cur, pre)));
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
            row.setUnit(normalizeUnit(row.getUnit()));
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

    /** 环比对齐键：产品 + 单位（单位在归一前后都参与，故这里统一走 normalizeUnit）。 */
    private String ratioKey(BoardStatProductRowVo row) {
        return row.getProductId() + "|" + normalizeUnit(row.getUnit());
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
        if (rows != null) {
            for (CategoryUnitQtyRow row : rows) {
                BigDecimal qty = row.getQty() == null ? BigDecimal.ZERO : row.getQty();
                merged.merge(normalizeUnit(row.getProductUnit()), qty, BigDecimal::add);
            }
        }
        List<BoardStatUnitTotalVo> totals = new ArrayList<>(merged.size());
        merged.forEach((unit, qty) -> {
            BoardStatUnitTotalVo vo = new BoardStatUnitTotalVo();
            vo.setUnit(unit);
            vo.setQty(qty);
            totals.add(vo);
        });
        return totals;
    }

    /**
     * 品类白名单校验：只认 4 张卡的 key。
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

    /** 拉一个月的三份聚合，落成「categoryKey → 单位 → 量」两级索引。 */
    private MonthMetrics loadMonth(String tenantId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate toExclusive = ym.plusMonths(1).atDay(1);
        return new MonthMetrics(
            index(boardStatMapper.selectInboundByCategoryUnit(
                tenantId, BELONG_TYPES, FlowDisplayScope.IN_EXCLUDED, from, toExclusive)),
            index(boardStatMapper.selectProduceByCategoryUnit(
                tenantId, BELONG_TYPES, from, toExclusive)),
            index(boardStatMapper.selectMaterialConsumeByCategoryUnit(
                tenantId, BELONG_TYPES, from, toExclusive)));
    }

    /**
     * 投影行 → 「categoryKey → 单位 → 量」。
     *
     * <p>猪肉卡把 pork 与 white_bar 两个 belong_type 合到同一个 key，同单位的量在此相加。</p>
     */
    private Map<String, Map<String, BigDecimal>> index(List<CategoryUnitQtyRow> rows) {
        Map<String, Map<String, BigDecimal>> result = new LinkedHashMap<>();
        if (rows == null) {
            return result;
        }
        for (CategoryUnitQtyRow row : rows) {
            String categoryKey = BELONG_TO_CATEGORY.get(row.getBelongType());
            if (categoryKey == null) {
                continue;
            }
            String unit = normalizeUnit(row.getProductUnit());
            BigDecimal qty = row.getQty() == null ? BigDecimal.ZERO : row.getQty();
            result.computeIfAbsent(categoryKey, k -> new LinkedHashMap<>())
                .merge(unit, qty, BigDecimal::add);
        }
        return result;
    }

    /** 单位归一：空 / 全空白统一成「未标单位」，否则 trim 后原文（大小写保留，档案里 kg 与 Kg 都有）。 */
    private String normalizeUnit(String unit) {
        String u = unit == null ? "" : unit.trim();
        return u.isEmpty() ? UNIT_UNKNOWN : u;
    }

    /** 单张品类卡：行 = 本月与上月出现过的单位并集，按单位名升序。 */
    private CategoryStatVo buildCard(String categoryKey, CategoryDef def, MonthMetrics cur, MonthMetrics pre) {
        CategoryStatVo card = new CategoryStatVo();
        card.setCategoryKey(categoryKey);
        card.setCategoryName(def.name());

        // TreeMap 只为拿确定的单位序（String 自然序）；本月单位 + 上月单位取并集，
        // 否则「本月这个单位归零」的行会整行消失，看不出是跌到 0 还是根本没这行。
        Map<String, Boolean> units = new TreeMap<>();
        cur.unitsOf(categoryKey).forEach(u -> units.put(u, Boolean.TRUE));
        pre.unitsOf(categoryKey).forEach(u -> units.put(u, Boolean.TRUE));

        List<CategoryUnitStatVo> rows = new ArrayList<>();
        for (String unit : units.keySet()) {
            CategoryUnitStatVo row = new CategoryUnitStatVo();
            row.setUnit(unit);

            BigDecimal curIn = cur.inbound(categoryKey, unit);
            BigDecimal curProduce = cur.produce(categoryKey, unit);
            BigDecimal curMaterial = cur.material(categoryKey, unit);
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

        private static BigDecimal pick(Map<String, Map<String, BigDecimal>> src, String categoryKey, String unit) {
            return src.getOrDefault(categoryKey, Map.of()).getOrDefault(unit, BigDecimal.ZERO);
        }
    }
}
