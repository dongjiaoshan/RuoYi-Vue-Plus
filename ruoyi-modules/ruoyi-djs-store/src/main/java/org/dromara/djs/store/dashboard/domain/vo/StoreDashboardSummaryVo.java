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
     * KPI 7：会员总数（{@code COUNT(*)} t_store_member，原型「客户/会员信息组」）。
     */
    private Long totalMembers;

    /**
     * KPI 8：今日新增会员数（{@code create_time} 落在今天，原型「会员信息组」）。
     */
    private Long todayNewMembers;

    /**
     * KPI 9：老客复购数（{@code t_store_member_consumption} 当月下单 ≥ 2 次的会员数，原型「会员信息组」）。
     */
    private Long repeatCustomer;

    /**
     * KPI 10：本月客单价（{@code monthSaleAmount / monthOrderCount}，订单数 0 时为 0，原型「会员信息组」）。
     */
    private BigDecimal monthAvgPrice;

    /**
     * 当日产品结构（按业态 djs_demand_product_type 分组，key=业态 / value=需求量）。
     */
    private List<StoreGroupCountVo> productStructure;

    /**
     * 当月订单产品结构（原型「当月订单产品结构」饼图，key=产品名 / value=当月订单数）。
     */
    private List<StoreGroupCountVo> monthProductStructure;

    /**
     * 当日热销产品 TOP10（按销售额倒序，原型「当日热销产品 TOP10」环形/横条）。
     */
    private List<StoreProductRankItemVo> top10Products;

    /**
     * 当月热门产品排行 TOP10（按订单数倒序，原型「当月热门产品排行 TOP10（按订单数）」横向柱）。
     */
    private List<StoreProductRankItemVo> monthTop10ByOrder;

    /**
     * 近 10 日趋势（订单数 / 销售额 / 客单价 / 销售量 / 退货量）。
     */
    private List<StoreTrendPointVo> trend10Days;

    /**
     * 近 10 日新增会员趋势（原型「近十日订单数与新会员趋势」竖柱，按日补 0 至 10 个点）。
     */
    private List<StoreMemberGrowthPointVo> memberGrowth10Days;

    /**
     * 本月逐日趋势（原型「销售额与客单价趋势」竖柱=销售额 + 折线=客单价，横轴本月每天）。
     */
    private List<StoreTrendPointVo> monthDailyTrend;

    /**
     * 4 业态当日销售分布（猪肉 / 蔬菜 / 礼盒 / 其他，固定 4 行；各含销售额 + 订单数 + 当月累计）。
     */
    private List<StoreSaleCategoryVo> saleByCategory;

    /**
     * 今日客单价（{@code todaySaleAmount / todayOrderCount}，订单数 0 时为 0）。
     */
    private BigDecimal avgPriceToday;

    /**
     * 今日销售额同比 %（vs 上月同期，上期为 0 时返 null）。
     */
    private BigDecimal todaySaleAmountYoy;

    /**
     * 今日订单数同比 %（vs 上月同期，上期为 0 时返 null）。
     */
    private BigDecimal todayOrderCountYoy;

    /**
     * 今日客单价同比 %（vs 上月同期客单价，上期为 0 时返 null）。
     */
    private BigDecimal avgPriceYoy;

    /**
     * 今日充值额（V1 无充值数据源 → 始终 null，前端显示"—"；V2 接充值流水）。
     */
    private BigDecimal todayRechargeAmount;

    /**
     * 今日充值额同比 %（V1 无数据源 → 始终 null）。
     */
    private BigDecimal todayRechargeAmountYoy;

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
        vo.setTotalMembers(0L);
        vo.setTodayNewMembers(0L);
        vo.setRepeatCustomer(0L);
        vo.setMonthAvgPrice(BigDecimal.ZERO);
        vo.setProductStructure(List.of());
        vo.setMonthProductStructure(List.of());
        vo.setTop10Products(List.of());
        vo.setMonthTop10ByOrder(List.of());
        vo.setTrend10Days(List.of());
        vo.setMemberGrowth10Days(List.of());
        vo.setMonthDailyTrend(List.of());
        vo.setSaleByCategory(List.of());
        vo.setAvgPriceToday(BigDecimal.ZERO);
        vo.setTodaySaleAmountYoy(null);
        vo.setTodayOrderCountYoy(null);
        vo.setAvgPriceYoy(null);
        vo.setTodayRechargeAmount(null);
        vo.setTodayRechargeAmountYoy(null);
        return vo;
    }

}
