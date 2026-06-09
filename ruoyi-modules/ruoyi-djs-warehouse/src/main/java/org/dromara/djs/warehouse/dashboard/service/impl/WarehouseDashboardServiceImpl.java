package org.dromara.djs.warehouse.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.dashboard.domain.vo.ChartSeriesItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.ChartTrendPointVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.LocationOverviewItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.ProductionMonthRowVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.ProductionStatItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.ProductionTrendPointVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardChartsVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseProductionStatVo;
import org.dromara.djs.warehouse.dashboard.mapper.WarehouseDashboardMapper;
import org.dromara.djs.warehouse.dashboard.service.IWarehouseDashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仓库看板聚合实现。
 *
 * <p>summary（3 KPI + 库位概览，mp 复用）+ charts（6 图 + 双横条 11 指标）。所有 null 用
 * 0 / 空列表兜底，折线按近 N 日补齐缺口日为 0。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseDashboardServiceImpl implements IWarehouseDashboardService {

    private static final String DEFAULT_TENANT = "1001";

    /** 折线近几日（生产趋势 / 损耗趋势）。 */
    private static final int TREND_DAYS = 7;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 4 业态值 → 中文标签（与字典 djs_demand_product_type 对齐）。 */
    private static final Map<String, String> DEMAND_TYPE_LABELS = new LinkedHashMap<>();

    /** 盘点结果 check_result_type → 中文标签（djs_check_result）。 */
    private static final Map<String, String> CHECK_RESULT_LABELS = new LinkedHashMap<>();

    static {
        DEMAND_TYPE_LABELS.put("white_bar", "白条");
        DEMAND_TYPE_LABELS.put("vegetable", "蔬菜");
        DEMAND_TYPE_LABELS.put("gift_box", "礼盒");
        DEMAND_TYPE_LABELS.put("other", "其他");

        CHECK_RESULT_LABELS.put("1", "正常");
        CHECK_RESULT_LABELS.put("2", "异常");
        CHECK_RESULT_LABELS.put("3", "计损");
    }

    private final WarehouseDashboardMapper dashboardMapper;

    @Override
    public WarehouseDashboardSummaryVo getSummary() {
        String tenantId = currentTenant();

        WarehouseDashboardSummaryVo vo = new WarehouseDashboardSummaryVo();

        vo.setTodayDemandQuantity(nzd(dashboardMapper.sumTodayWhiteBarDemand(tenantId)));
        vo.setTodayProductionCount(nz(dashboardMapper.countTodayProduction(tenantId)));
        vo.setStockCheckNormal(nz(dashboardMapper.countLatestCheckNormal(tenantId)));
        vo.setStockCheckAbnormal(nz(dashboardMapper.countLatestCheckAbnormal(tenantId)));
        vo.setStockCheckLoss(nz(dashboardMapper.countLatestCheckLoss(tenantId)));
        vo.setMonthAbnormalLocationCount(nz(dashboardMapper.countMonthAbnormalLocation(tenantId)));

        List<LocationOverviewItemVo> overview = dashboardMapper.selectLocationOverview(tenantId);
        vo.setLocationOverview(overview == null ? List.of() : overview);

        return vo;
    }

    @Override
    public WarehouseDashboardChartsVo getCharts() {
        String tenantId = currentTenant();
        WarehouseDashboardChartsVo vo = new WarehouseDashboardChartsVo();

        // 图① 需求饼：补齐 4 业态、映射中文标签
        vo.setDemandByType(buildDemandByType(dashboardMapper.selectDemandByType(tenantId)));

        // 图② 退货环：映射方向中文标签
        vo.setReturnByDirection(buildReturnByDirection(dashboardMapper.selectReturnByDirection(tenantId)));

        // 图③ 生产趋势折线：近 N 日补齐
        vo.setProductionTrend(fillTrend(dashboardMapper.selectProductionTrend(tenantId, TREND_DAYS)));

        // 图④ 盘点结果饼：映射 1/2/3 中文标签
        vo.setCheckResult(buildCheckResult(dashboardMapper.selectCheckResult(tenantId)));

        // 图⑤ 异常库位环：异常 vs 正常
        vo.setLocationHealth(buildLocationHealth(tenantId));

        // 图⑥ 损耗折线：近 N 日补齐
        vo.setLossTrend(fillTrend(dashboardMapper.selectLossTrend(tenantId, TREND_DAYS)));

        // 横条 1「今日需求」6 项
        vo.setTodayDemandWhiteBar(nzd(dashboardMapper.sumTodayDemandByType(tenantId, "white_bar")));
        vo.setTodayDemandVegetable(nzd(dashboardMapper.sumTodayDemandByType(tenantId, "vegetable")));
        vo.setTodayDemandGiftBox(nzd(dashboardMapper.sumTodayDemandByType(tenantId, "gift_box")));
        vo.setTodayDemandOther(nzd(dashboardMapper.sumTodayDemandByType(tenantId, "other")));
        vo.setTodayDemandOrderCount(nz(dashboardMapper.countTodayDemandOrders(tenantId)));
        vo.setTodayDemandTotal(nzd(dashboardMapper.sumTodayDemandTotal(tenantId)));

        // 横条 2「今日生产」5 项
        vo.setTodayProductionCount(nz(dashboardMapper.countTodayProduction(tenantId)));
        vo.setTodayProductionWeight(nzd(dashboardMapper.sumTodayProductionWeight(tenantId)));
        vo.setTodayInboundCount(nz(dashboardMapper.countTodayFlowByDirection(tenantId, "IN")));
        vo.setTodayOutboundCount(nz(dashboardMapper.countTodayFlowByDirection(tenantId, "OT")));
        vo.setTodayLossQuantity(nzd(dashboardMapper.sumTodayLoss(tenantId)));

        return vo;
    }

    @Override
    public WarehouseProductionStatVo getProduction(String tab) {
        String tenantId = currentTenant();
        String normalized = normalizeTab(tab);

        WarehouseProductionStatVo vo = new WarehouseProductionStatVo();
        vo.setTab(normalized);

        switch (normalized) {
            case "veg" -> {
                vo.setAnnualGroups(buildVegGroups(tenantId));
                vo.setTrend(fillMonthlyTrend(dashboardMapper.selectVegMonthlyTrend(tenantId), false));
            }
            case "ship" -> {
                vo.setAnnualGroups(buildShipGroups(tenantId));
                vo.setTrend(fillMonthlyTrend(dashboardMapper.selectShipMonthlyTrend(tenantId), false));
            }
            default -> {
                vo.setAnnualGroups(buildPigGroups(tenantId));
                vo.setTrend(fillMonthlyTrend(dashboardMapper.selectPigMonthlyTrend(tenantId), true));
            }
        }
        return vo;
    }

    /**
     * 猪肉 tab：年度屠宰指标组 + 年度分割指标组（对原型图 26）。
     *
     * @param tenantId 租户
     * @return 2 组指标
     */
    private List<WarehouseProductionStatVo.Group> buildPigGroups(String tenantId) {
        // 年度屠宰指标
        int burnCount = nz(dashboardMapper.countYearBurn(tenantId));
        BigDecimal avgArrive = nzd(dashboardMapper.avgYearBurnArriveWeight(tenantId));
        BigDecimal sumArrive = nzd(dashboardMapper.sumYearBurnArriveWeight(tenantId));
        BigDecimal sumBurn = nzd(dashboardMapper.sumYearBurnWeight(tenantId));
        BigDecimal sumBarIn = nzd(dashboardMapper.sumYearBarInWeight(tenantId));
        BigDecimal sumBarOut = nzd(dashboardMapper.sumYearBarOutWeight(tenantId));

        List<ProductionStatItemVo> slaughterItems = new ArrayList<>();
        slaughterItems.add(new ProductionStatItemVo("送宰头数", BigDecimal.valueOf(burnCount), "头"));
        slaughterItems.add(new ProductionStatItemVo("送宰均重", scale2(avgArrive), "kg"));
        slaughterItems.add(new ProductionStatItemVo("屠宰出品率", ratePercent(sumBurn, sumArrive), "%"));
        slaughterItems.add(new ProductionStatItemVo("白条出品率", ratePercent(sumBarOut, sumBarIn), "%"));

        // 年度分割指标
        int cutCount = nz(dashboardMapper.countYearCut(tenantId));
        BigDecimal cutPickup = nzd(dashboardMapper.sumYearCutPickupWeight(tenantId));
        BigDecimal cutProduct = nzd(dashboardMapper.sumYearCutProductWeight(tenantId));

        List<ProductionStatItemVo> cutItems = new ArrayList<>();
        cutItems.add(new ProductionStatItemVo("分割头数", BigDecimal.valueOf(cutCount), "头"));
        cutItems.add(new ProductionStatItemVo("分割总重", scale2(toTon(cutPickup)), "吨"));
        cutItems.add(new ProductionStatItemVo("分割品总重", scale2(toTon(cutProduct)), "吨"));
        cutItems.add(new ProductionStatItemVo("分割率", ratePercent(cutProduct, cutPickup), "%"));

        List<WarehouseProductionStatVo.Group> groups = new ArrayList<>();
        groups.add(new WarehouseProductionStatVo.Group("年度屠宰指标统计", slaughterItems));
        groups.add(new WarehouseProductionStatVo.Group("年度分割指标统计", cutItems));
        return groups;
    }

    /**
     * 果蔬 tab：年度果蔬处理指标组。
     *
     * @param tenantId 租户
     * @return 1 组指标
     */
    private List<WarehouseProductionStatVo.Group> buildVegGroups(String tenantId) {
        int batchCount = nz(dashboardMapper.countYearVeg(tenantId));
        BigDecimal sumWeight = nzd(dashboardMapper.sumYearVegWeight(tenantId));
        int kindCount = nz(dashboardMapper.countYearVegKind(tenantId));

        List<ProductionStatItemVo> items = new ArrayList<>();
        items.add(new ProductionStatItemVo("处理批次", BigDecimal.valueOf(batchCount), "次"));
        items.add(new ProductionStatItemVo("处理总重", scale2(toTon(sumWeight)), "吨"));
        items.add(new ProductionStatItemVo("处理品种", BigDecimal.valueOf(kindCount), "种"));

        List<WarehouseProductionStatVo.Group> groups = new ArrayList<>();
        groups.add(new WarehouseProductionStatVo.Group("年度果蔬处理指标统计", items));
        return groups;
    }

    /**
     * 发货 tab：年度发货指标组。
     *
     * @param tenantId 租户
     * @return 1 组指标
     */
    private List<WarehouseProductionStatVo.Group> buildShipGroups(String tenantId) {
        int shipCount = nz(dashboardMapper.countYearShipment(tenantId));
        BigDecimal shipQty = nzd(dashboardMapper.sumYearShipQuantity(tenantId));
        int delivered = nz(dashboardMapper.countYearDelivered(tenantId));

        List<ProductionStatItemVo> items = new ArrayList<>();
        items.add(new ProductionStatItemVo("发货单数", BigDecimal.valueOf(shipCount), "单"));
        items.add(new ProductionStatItemVo("发货总量", scale2(shipQty), "kg"));
        items.add(new ProductionStatItemVo("已送达", BigDecimal.valueOf(delivered), "单"));
        items.add(new ProductionStatItemVo("送达率", ratePercent(BigDecimal.valueOf(delivered), BigDecimal.valueOf(shipCount)), "%"));

        List<WarehouseProductionStatVo.Group> groups = new ArrayList<>();
        groups.add(new WarehouseProductionStatVo.Group("年度发货指标统计", items));
        return groups;
    }

    /**
     * 月度趋势补齐：mapper 仅返回有数据月份，这里补齐当年 1 月～当前月每月一点，缺月补 0。
     *
     * <p>{@code isPig=true} 时折算屠宰出品率（extraB/extraA）写入 {@code slaughterRate}；
     * 其余 tab 的 rate 字段保持 0（果蔬/发货无屠宰率概念）。</p>
     *
     * @param rows  mapper 月聚合行（name=月份数字, value=主计数, extraA/extraB=附加列）
     * @param isPig 是否猪肉 tab（决定是否折算屠宰出品率）
     * @return 当年逐月趋势点
     */
    private List<ProductionTrendPointVo> fillMonthlyTrend(List<ProductionMonthRowVo> rows, boolean isPig) {
        Map<String, ProductionMonthRowVo> raw = new LinkedHashMap<>();
        if (rows != null) {
            for (ProductionMonthRowVo r : rows) {
                if (r.getName() != null) {
                    raw.put(r.getName(), r);
                }
            }
        }
        int currentMonth = LocalDate.now().getMonthValue();
        List<ProductionTrendPointVo> result = new ArrayList<>();
        for (int m = 1; m <= currentMonth; m++) {
            String key = String.valueOf(m);
            String label = m + "月";
            ProductionMonthRowVo r = raw.get(key);
            if (r == null) {
                result.add(new ProductionTrendPointVo(label, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
                continue;
            }
            BigDecimal barValue = nzd(r.getValue());
            BigDecimal slaughterRate = isPig
                ? ratePercent(nzd(r.getExtraB()), nzd(r.getExtraA()))
                : BigDecimal.ZERO;
            result.add(new ProductionTrendPointVo(label, barValue,
                slaughterRate, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        return result;
    }

    /**
     * tab 归一：大小写不敏感，仅接受 pig/veg/ship，其余回退 pig。
     *
     * @param tab 原始 tab 入参
     * @return 归一后的 tab
     */
    private String normalizeTab(String tab) {
        if (tab == null) {
            return "pig";
        }
        String t = tab.trim().toLowerCase();
        return switch (t) {
            case "veg", "ship", "pig" -> t;
            default -> "pig";
        };
    }

    /**
     * 比率折算为百分数值（分子/分母*100，保留 2 位）；分母 ≤ 0 返 0。
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 百分数值（如 80.21），分母无效返 0
     */
    private BigDecimal ratePercent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() <= 0 || numerator == null) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(BigDecimal.valueOf(100))
            .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    /**
     * kg → 吨（/1000，保留 3 位精度供后续 scale）。
     *
     * @param kg 千克值
     * @return 吨值
     */
    private BigDecimal toTon(BigDecimal kg) {
        if (kg == null) {
            return BigDecimal.ZERO;
        }
        return kg.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    }

    /**
     * 保留 2 位小数（HALF_UP）。
     *
     * @param v 原值
     * @return 2 位小数值
     */
    private BigDecimal scale2(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 图① 需求饼：按固定 4 业态顺序补齐（无数据业态补 0），值映射成中文标签。
     *
     * @param rows mapper 原始行（name=product_type 值）
     * @return 4 项 series（中文标签 + 需求量）
     */
    private List<ChartSeriesItemVo> buildDemandByType(List<ChartSeriesItemVo> rows) {
        Map<String, BigDecimal> raw = toValueMap(rows);
        List<ChartSeriesItemVo> result = new ArrayList<>();
        for (Map.Entry<String, String> e : DEMAND_TYPE_LABELS.entrySet()) {
            result.add(new ChartSeriesItemVo(e.getValue(), raw.getOrDefault(e.getKey(), BigDecimal.ZERO)));
        }
        return result;
    }

    /**
     * 图② 退货环：映射退货方向为中文标签。
     *
     * @param rows mapper 原始行（name=return_direction 值）
     * @return series（中文标签 + 计数）
     */
    private List<ChartSeriesItemVo> buildReturnByDirection(List<ChartSeriesItemVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ChartSeriesItemVo> result = new ArrayList<>();
        for (ChartSeriesItemVo row : rows) {
            String label = switch (nullToEmpty(row.getName())) {
                case "store_to_warehouse" -> "门店退仓";
                case "warehouse_to_supplier" -> "仓退供应商";
                default -> "其他";
            };
            result.add(new ChartSeriesItemVo(label, nzd(row.getValue())));
        }
        return result;
    }

    /**
     * 图④ 盘点饼：映射 check_result_type（1/2/3）为中文标签。
     *
     * @param rows mapper 原始行（name=check_result_type 数值字符串）
     * @return series（中文标签 + 计数）
     */
    private List<ChartSeriesItemVo> buildCheckResult(List<ChartSeriesItemVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ChartSeriesItemVo> result = new ArrayList<>();
        for (ChartSeriesItemVo row : rows) {
            String label = CHECK_RESULT_LABELS.getOrDefault(nullToEmpty(row.getName()), "其他");
            result.add(new ChartSeriesItemVo(label, nzd(row.getValue())));
        }
        return result;
    }

    /**
     * 图⑤ 异常库位环：当月异常 vs 正常库位数（两值固定返回，便于前端环形对比）。
     *
     * @param tenantId 租户
     * @return [异常库位, 正常库位] 两项 series
     */
    private List<ChartSeriesItemVo> buildLocationHealth(String tenantId) {
        BigDecimal abnormal = BigDecimal.valueOf(nz(dashboardMapper.countMonthAbnormalLocation(tenantId)));
        BigDecimal normal = BigDecimal.valueOf(nz(dashboardMapper.countMonthNormalLocation(tenantId)));
        List<ChartSeriesItemVo> result = new ArrayList<>();
        result.add(new ChartSeriesItemVo("异常库位", abnormal));
        result.add(new ChartSeriesItemVo("正常库位", normal));
        return result;
    }

    /**
     * 折线近 N 日补齐：mapper 只返回有数据的日期，这里补齐 [今天-N+1, 今天] 每天一个点，缺口补 0。
     *
     * @param rows mapper 原始日聚合行
     * @return 连续 N 天的 date+value 点
     */
    private List<ChartTrendPointVo> fillTrend(List<ChartTrendPointVo> rows) {
        Map<String, BigDecimal> raw = new LinkedHashMap<>();
        if (rows != null) {
            for (ChartTrendPointVo p : rows) {
                if (p.getDate() != null) {
                    raw.put(p.getDate(), nzd(p.getValue()));
                }
            }
        }
        List<ChartTrendPointVo> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            String day = today.minusDays(i).format(DAY_FMT);
            result.add(new ChartTrendPointVo(day, raw.getOrDefault(day, BigDecimal.ZERO)));
        }
        return result;
    }

    /**
     * 把 mapper 行列表压成 name→value map（null-safe）。
     *
     * @param rows series 行
     * @return name→value map
     */
    private Map<String, BigDecimal> toValueMap(List<ChartSeriesItemVo> rows) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        if (rows != null) {
            for (ChartSeriesItemVo row : rows) {
                if (row.getName() != null) {
                    map.put(row.getName(), nzd(row.getValue()));
                }
            }
        }
        return map;
    }

    /**
     * null → 0（整数）。
     *
     * @param v 可空整数
     * @return 非空整数
     */
    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * null → 0（BigDecimal）。
     *
     * @param v 可空 BigDecimal
     * @return 非空 BigDecimal
     */
    private BigDecimal nzd(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * null → 空串。
     *
     * @param s 可空字符串
     * @return 非空字符串
     */
    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 取当前租户；V1 单租户场景或异常时回退 {@value #DEFAULT_TENANT}。
     *
     * @return 当前租户 ID
     */
    private String currentTenant() {
        try {
            String t = TenantHelper.getTenantId();
            return t == null || t.isEmpty() ? DEFAULT_TENANT : t;
        } catch (Exception e) {
            log.warn("[WarehouseDashboard] 获取租户失败，回退默认租户", e);
            return DEFAULT_TENANT;
        }
    }

}
