package org.dromara.djs.warehouse.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.dashboard.domain.vo.ChartSeriesItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.ChartTrendPointVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.LocationOverviewItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardChartsVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;
import org.dromara.djs.warehouse.dashboard.mapper.WarehouseDashboardMapper;
import org.dromara.djs.warehouse.dashboard.service.IWarehouseDashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
