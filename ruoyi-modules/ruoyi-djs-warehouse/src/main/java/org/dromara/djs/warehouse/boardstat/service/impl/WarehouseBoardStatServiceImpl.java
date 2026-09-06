package org.dromara.djs.warehouse.boardstat.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * mp 仓库统计（品类 × 单位 × 三指标 + 环比）实现。
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
        return u.isEmpty() ? "未标单位" : u;
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

    /** 月份入参解析：yyyy-MM；空 / 非法格式回落当月（看板不因入参脏就 500）。 */
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
