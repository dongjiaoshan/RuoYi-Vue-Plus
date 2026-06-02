package org.dromara.djs.store.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 门店看板汇总 VO（STR-DASH-001，admin PC 门店首页）。
 *
 * <p>6 KPI + 当日产品结构 + 当月 TOP10 + 近 10 日趋势。数据 100% 来自
 * {@code t_store_sale_record}（STR-OP-001）+ {@code t_warehouse_demand_manage}
 * （WMS-DEMAND-001），纯只读聚合无新表。</p>
 *
 * <p>无数据时所有计数字段返 0 / 空列表（service {@code COALESCE} + null-safe 兜底），不抛错。</p>
 *
 * @author djs
 * @since STR-DASH-001
 */
@Data
public class StoreDashboardSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * KPI 1：今日销售额（{@code SUM(sale_amount)} WHERE sale_date=CURDATE()）。
     */
    private BigDecimal todaySaleAmount;

    /**
     * KPI 2：本月累计销售额（{@code SUM(sale_amount)} WHERE sale_date>=当月1号）。
     */
    private BigDecimal monthSaleAmount;

    /**
     * KPI 3：今日订单数（{@code COUNT(*)} WHERE sale_date=CURDATE()）。
     */
    private Integer todayOrderCount;

    /**
     * KPI 4：本月订单数（{@code COUNT(*)} WHERE sale_date>=当月1号）。
     */
    private Integer monthOrderCount;

    /**
     * KPI 5：待发货数（demand_status IN CONFIRMED / IN_PRODUCTION / PARTIAL_SHIPPED）。
     */
    private Integer pendingShipCount;

    /**
     * KPI 6：待采购数（demand_status = SUBMITTED）。
     */
    private Integer pendingPurchaseCount;

    /**
     * 当日产品结构（按业态 djs_demand_product_type 分组，key=业态 / value=需求量）。
     */
    private List<StoreGroupCountVo> productStructure;

    /**
     * 当月 TOP10 产品排行（按销售额倒序）。
     */
    private List<StoreProductRankItemVo> top10Products;

    /**
     * 近 10 日趋势（订单数 / 销售额 / 客单价）。
     */
    private List<StoreTrendPointVo> trend10Days;

    /**
     * 全空兜底实例（无任何数据时返回，避免前端空指针）。
     *
     * @return 计数字段全 0、列表全空的 VO
     */
    public static StoreDashboardSummaryVo empty() {
        StoreDashboardSummaryVo vo = new StoreDashboardSummaryVo();
        vo.setTodaySaleAmount(BigDecimal.ZERO);
        vo.setMonthSaleAmount(BigDecimal.ZERO);
        vo.setTodayOrderCount(0);
        vo.setMonthOrderCount(0);
        vo.setPendingShipCount(0);
        vo.setPendingPurchaseCount(0);
        vo.setProductStructure(List.of());
        vo.setTop10Products(List.of());
        vo.setTrend10Days(List.of());
        return vo;
    }

}
