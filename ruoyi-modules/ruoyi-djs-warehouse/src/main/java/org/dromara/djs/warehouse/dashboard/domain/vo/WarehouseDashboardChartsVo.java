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

    /** 图①：果蔬产品当日需求分布饼（近 30 日 vegetable 业态按产品名 SUM 需求量，对齐原型）。 */
    private List<ChartSeriesItemVo> demandByType;

    /** 图②：退货环（按退货方向构成 COUNT，兼容旧口径）。 */
    private List<ChartSeriesItemVo> returnByDirection;

    /** 图②（对齐原型）：产品退货分布·猪肉（pork belong_type 按产品名 COUNT）。 */
    private List<ChartSeriesItemVo> returnPork;

    /** 图②（对齐原型）：产品退货分布·果蔬（vegetable belong_type 按产品名 COUNT，前端「猪肉/果蔬」切换）。 */
    private List<ChartSeriesItemVo> returnVegetable;

    /** 图③：生产趋势折线（近 7 日按日生产重量，兼容旧口径）。 */
    private List<ChartTrendPointVo> productionTrend;

    /** 图③（对齐原型组合图）：近 7 日白条头数（柱）。 */
    private List<ChartTrendPointVo> productionWhiteBarHeadTrend;

    /** 图③（对齐原型组合图）：近 7 日猪肉产品重量（线）。 */
    private List<ChartTrendPointVo> productionPorkWeightTrend;

    /** 图③（对齐原型组合图）：近 7 日果蔬产品重量（线）。 */
    private List<ChartTrendPointVo> productionVegWeightTrend;

    /** 图④：盘点结果饼（正常 / 异常 / 计损 COUNT，最近一个盘点单）。 */
    private List<ChartSeriesItemVo> checkResult;

    /** 图⑤：异常库位环（当月异常 vs 正常库位数，兼容旧口径）。 */
    private List<ChartSeriesItemVo> locationHealth;

    /** 图⑤（对齐原型）：当月盘点异常库位分布环（按库位名，雪花 ID 已映射名称）。 */
    private List<ChartSeriesItemVo> abnormalLocationByName;

    /** 图⑥：损耗折线（近 7 日按日损耗量，兼容旧口径）。 */
    private List<ChartTrendPointVo> lossTrend;

    /** 图⑥（对齐原型多系列）：近 7 日猪肉产品损耗量（线）。 */
    private List<ChartTrendPointVo> lossPorkTrend;

    /** 图⑥（对齐原型多系列）：近 7 日果蔬产品损耗量（线）。 */
    private List<ChartTrendPointVo> lossVegTrend;

    // ====== 双 KPI 横条（今日需求 8 项 + 今日生产 8 项，对齐原型） ======

    /** 横条 1「今日需求」1：白条需求（头，product_type='white_bar' SUM demand_quantity 计头）。 */
    private BigDecimal todayDemandWhiteBar;

    /** 横条 1「今日需求」2：猪肉产品需求量（kg，belong_type='pork'）。 */
    private BigDecimal todayDemandPork;

    /** 横条 1「今日需求」3：红白脏产品需求量（kg）。V1 无对应 belong_type 数据源，默认 0。 */
    private BigDecimal todayDemandOffal;

    /** 横条 1「今日需求」4：礼盒需求量（份，product_type='gift_box'）。 */
    private BigDecimal todayDemandGiftBox;

    /** 横条 1「今日需求」5：果蔬需求品类数（种，belong_type='vegetable' 去重产品数）。 */
    private Integer todayDemandVegetableKinds;

    /** 横条 1「今日需求」6：果蔬需求量（kg，belong_type='vegetable' SUM demand_quantity）。 */
    private BigDecimal todayDemandVegetable;

    /** 横条 1「今日需求」7：鸡蛋需求量（个，belong_type='egg'）。 */
    private BigDecimal todayDemandEgg;

    /** 横条 1「今日需求」8：干货需求量（kg，belong_type='dry_good'）。 */
    private BigDecimal todayDemandDryGood;

    /** 横条 1（兼容旧口径）：其他业态需求量。 */
    private BigDecimal todayDemandOther;

    /** 横条 1（兼容旧口径）：今日需求单数（COUNT，去重 demand_no）。 */
    private Integer todayDemandOrderCount;

    /** 横条 1（兼容旧口径）：今日需求总量（SUM 全业态 demand_quantity）。 */
    private BigDecimal todayDemandTotal;

    /** 横条 2「今日生产」1：送宰猪只（头，今日燎毛记录数）。 */
    private Integer todaySlaughterPigCount;

    /** 横条 2「今日生产」2：白条总重（kg，今日燎毛 burn_weight 合计）。 */
    private BigDecimal todayWhiteBarWeight;

    /** 横条 2「今日生产」3：分割白条（头，今日分割记录数）。 */
    private Integer todayCutBarCount;

    /** 横条 2「今日生产」4：分割猪只产品总重（kg，今日 ear_no 来源生产记录重量）。 */
    private BigDecimal todayCutProductWeight;

    /** 横条 2「今日生产」5：果蔬接收品种（种，今日收货去重地块/产品数）。 */
    private Integer todayVegReceiveKinds;

    /** 横条 2「今日生产」6：果蔬接收总重（kg，今日收货重量合计）。 */
    private BigDecimal todayVegReceiveWeight;

    /** 横条 2「今日生产」7：果蔬产品种类（种，今日 plot_id 来源生产记录去重产品数）。 */
    private Integer todayVegProductKinds;

    /** 横条 2「今日生产」8：果蔬产品总重（kg，今日 plot_id 来源生产记录重量）。 */
    private BigDecimal todayVegProductWeight;

    /** 横条 2（兼容旧口径）：今日生产笔数（COUNT product_production）。 */
    private Integer todayProductionCount;

    /** 横条 2（兼容旧口径）：今日生产重量（SUM product_weight）。 */
    private BigDecimal todayProductionWeight;

    /** 横条 2（兼容旧口径）：今日入库笔数（stock_flow inout_type='IN'）。 */
    private Integer todayInboundCount;

    /** 横条 2（兼容旧口径）：今日出库笔数（stock_flow inout_type='OT'）。 */
    private Integer todayOutboundCount;

    /** 横条 2（兼容旧口径）：今日损耗量（stock_flow flow_type='loss' SUM change_quantity）。 */
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
        vo.setReturnPork(List.of());
        vo.setReturnVegetable(List.of());
        vo.setProductionTrend(List.of());
        vo.setProductionWhiteBarHeadTrend(List.of());
        vo.setProductionPorkWeightTrend(List.of());
        vo.setProductionVegWeightTrend(List.of());
        vo.setCheckResult(List.of());
        vo.setLocationHealth(List.of());
        vo.setAbnormalLocationByName(List.of());
        vo.setLossTrend(List.of());
        vo.setLossPorkTrend(List.of());
        vo.setLossVegTrend(List.of());
        // 今日需求 8 项 + 兼容旧 3 项
        vo.setTodayDemandWhiteBar(BigDecimal.ZERO);
        vo.setTodayDemandPork(BigDecimal.ZERO);
        vo.setTodayDemandOffal(BigDecimal.ZERO);
        vo.setTodayDemandGiftBox(BigDecimal.ZERO);
        vo.setTodayDemandVegetableKinds(0);
        vo.setTodayDemandVegetable(BigDecimal.ZERO);
        vo.setTodayDemandEgg(BigDecimal.ZERO);
        vo.setTodayDemandDryGood(BigDecimal.ZERO);
        vo.setTodayDemandOther(BigDecimal.ZERO);
        vo.setTodayDemandOrderCount(0);
        vo.setTodayDemandTotal(BigDecimal.ZERO);
        // 今日生产 8 项 + 兼容旧 5 项
        vo.setTodaySlaughterPigCount(0);
        vo.setTodayWhiteBarWeight(BigDecimal.ZERO);
        vo.setTodayCutBarCount(0);
        vo.setTodayCutProductWeight(BigDecimal.ZERO);
        vo.setTodayVegReceiveKinds(0);
        vo.setTodayVegReceiveWeight(BigDecimal.ZERO);
        vo.setTodayVegProductKinds(0);
        vo.setTodayVegProductWeight(BigDecimal.ZERO);
        vo.setTodayProductionCount(0);
        vo.setTodayProductionWeight(BigDecimal.ZERO);
        vo.setTodayInboundCount(0);
        vo.setTodayOutboundCount(0);
        vo.setTodayLossQuantity(BigDecimal.ZERO);
        return vo;
    }

}
