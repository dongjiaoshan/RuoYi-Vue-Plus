package org.dromara.djs.warehouse.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 仓库看板可视化 VO：6 张 ECharts 图 series + 双 KPI 横条 11 指标。
 *
 * <p>对齐养殖看板范式，本 VO 一次返回前端渲染所需的全部聚合结果，前端按 series 直接渲染，
 * 不再二次计算。无数据时各 series 返空列表 / 折线全 0、横条指标全 0，service 层 null-safe 兜底，
 * 不抛错。</p>
 *
 * <p>6 图数据口径：</p>
 * <ul>
 *   <li>① 果蔬需求饼 {@link #demandByType}：t_warehouse_demand_manage GROUP BY product_type（4 业态），SUM(demand_quantity)，近 30 日。</li>
 *   <li>② 退货环 {@link #returnByDirection}：t_warehouse_return_product GROUP BY return_direction，COUNT，近 30 日。</li>
 *   <li>③ 生产趋势折线 {@link #productionTrend}：t_warehouse_product_production 按 produce_date 日聚合 SUM(product_weight)，近 7 日补齐。</li>
 *   <li>④ 盘点结果饼 {@link #checkResult}：t_warehouse_check_record（仅明细行 is_header=0）GROUP BY check_result_type（正常/异常/计损），COUNT，最近一个盘点单。</li>
 *   <li>⑤ 异常库位环 {@link #locationHealth}：t_warehouse_check_record 当月 COUNT(DISTINCT location_id) 异常（diff_stock!=0）vs 正常。</li>
 *   <li>⑥ 损耗折线 {@link #lossTrend}：t_warehouse_stock_flow flow_type='loss' 按 flow_date 日聚合 SUM(change_quantity)，近 7 日补齐。</li>
 * </ul>
 *
 * @author djs
 */
@Data
public class WarehouseDashboardChartsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 图①：果蔬需求饼（按 4 业态 SUM 需求量）。 */
    private List<ChartSeriesItemVo> demandByType;

    /** 图②：退货环（按退货方向构成 COUNT）。 */
    private List<ChartSeriesItemVo> returnByDirection;

    /** 图③：生产趋势折线（近 7 日按日生产重量）。 */
    private List<ChartTrendPointVo> productionTrend;

    /** 图④：盘点结果饼（正常 / 异常 / 计损 COUNT，最近一个盘点单）。 */
    private List<ChartSeriesItemVo> checkResult;

    /** 图⑤：异常库位环（当月异常 vs 正常库位数）。 */
    private List<ChartSeriesItemVo> locationHealth;

    /** 图⑥：损耗折线（近 7 日按日损耗量）。 */
    private List<ChartTrendPointVo> lossTrend;

    // ====== 双 KPI 横条（11 指标） ======

    /** 横条 1「今日需求」：白条业态需求量（SUM demand_quantity，product_type='white_bar'）。 */
    private BigDecimal todayDemandWhiteBar;

    /** 横条 1：蔬菜业态需求量。 */
    private BigDecimal todayDemandVegetable;

    /** 横条 1：礼盒业态需求量。 */
    private BigDecimal todayDemandGiftBox;

    /** 横条 1：其他业态需求量。 */
    private BigDecimal todayDemandOther;

    /** 横条 1：今日需求单数（COUNT，去重 demand_no）。 */
    private Integer todayDemandOrderCount;

    /** 横条 1：今日需求总量（SUM 全业态 demand_quantity）。 */
    private BigDecimal todayDemandTotal;

    /** 横条 2「今日生产」：今日生产笔数（COUNT product_production）。 */
    private Integer todayProductionCount;

    /** 横条 2：今日生产重量（SUM product_weight）。 */
    private BigDecimal todayProductionWeight;

    /** 横条 2：今日入库笔数（stock_flow inout_type='IN'）。 */
    private Integer todayInboundCount;

    /** 横条 2：今日出库笔数（stock_flow inout_type='OT'）。 */
    private Integer todayOutboundCount;

    /** 横条 2：今日损耗量（stock_flow flow_type='loss' SUM change_quantity）。 */
    private BigDecimal todayLossQuantity;

    /**
     * 全空兜底实例（无任何数据时返回，避免前端空指针）。
     *
     * @return 6 图 series 空列表、11 指标全 0 的 VO
     */
    public static WarehouseDashboardChartsVo empty() {
        WarehouseDashboardChartsVo vo = new WarehouseDashboardChartsVo();
        vo.setDemandByType(List.of());
        vo.setReturnByDirection(List.of());
        vo.setProductionTrend(List.of());
        vo.setCheckResult(List.of());
        vo.setLocationHealth(List.of());
        vo.setLossTrend(List.of());
        vo.setTodayDemandWhiteBar(BigDecimal.ZERO);
        vo.setTodayDemandVegetable(BigDecimal.ZERO);
        vo.setTodayDemandGiftBox(BigDecimal.ZERO);
        vo.setTodayDemandOther(BigDecimal.ZERO);
        vo.setTodayDemandOrderCount(0);
        vo.setTodayDemandTotal(BigDecimal.ZERO);
        vo.setTodayProductionCount(0);
        vo.setTodayProductionWeight(BigDecimal.ZERO);
        vo.setTodayInboundCount(0);
        vo.setTodayOutboundCount(0);
        vo.setTodayLossQuantity(BigDecimal.ZERO);
        return vo;
    }

}
