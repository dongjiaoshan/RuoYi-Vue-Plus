package org.dromara.djs.store.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.store.dashboard.domain.vo.StoreGroupCountVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreProductRankItemVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreTrendPointVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门店看板聚合查询 Mapper（STR-DASH-001，纯只读聚合）。
 *
 * <p>本 mapper 不绑定单表实体；只提供 dashboard service 调用的原始聚合 SQL。
 * 数据源：{@code t_store_sale_record}（STR-OP-001）+ {@code t_warehouse_demand_manage}
 * （WMS-DEMAND-001）+ {@code t_warehouse_product_info}（业态 belong_type 过滤）。</p>
 *
 * <p><b>关键约定</b>：dashboard 聚合不走 BaseMapperPlus 自动 tenant 注入，故所有 SQL
 * 显式 {@code WHERE tenant_id = #{tenantId} AND del_flag = '0'}（参照 W22-006
 * WarehouseDashboardMapper 范式）。所有聚合 {@code COALESCE(...,0)} 兜底，无数据返 0 / 空列表。</p>
 *
 * <p>{@code storeId} 可选过滤：传 null 时按租户范围全量聚合，非 null 时限定门店
 * （{@code (#{storeId} IS NULL OR store_id = #{storeId})}）。</p>
 *
 * @author djs
 * @since STR-DASH-001
 */
@Mapper
public interface StoreDashboardMapper {

    /**
     * KPI 1：今日销售额合计。
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空，null 时全量）
     * @return SUM(sale_amount)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(sale_amount), 0) "
        + "  FROM t_store_sale_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND sale_date = CURDATE() "
        + "   AND (#{storeId} IS NULL OR store_id = #{storeId})")
    BigDecimal sumTodaySaleAmount(@Param("tenantId") String tenantId, @Param("storeId") Long storeId);

    /**
     * KPI 2：本月累计销售额合计。
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return SUM(sale_amount)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(sale_amount), 0) "
        + "  FROM t_store_sale_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND sale_date >= DATE_FORMAT(NOW(), '%Y-%m-01') "
        + "   AND (#{storeId} IS NULL OR store_id = #{storeId})")
    BigDecimal sumMonthSaleAmount(@Param("tenantId") String tenantId, @Param("storeId") Long storeId);

    /**
     * KPI 3：今日订单数。
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return COUNT(*)，无记录返 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_store_sale_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND sale_date = CURDATE() "
        + "   AND (#{storeId} IS NULL OR store_id = #{storeId})")
    Integer countTodayOrders(@Param("tenantId") String tenantId, @Param("storeId") Long storeId);

    /**
     * KPI 4：本月订单数。
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return COUNT(*)，无记录返 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_store_sale_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND sale_date >= DATE_FORMAT(NOW(), '%Y-%m-01') "
        + "   AND (#{storeId} IS NULL OR store_id = #{storeId})")
    Integer countMonthOrders(@Param("tenantId") String tenantId, @Param("storeId") Long storeId);

    /**
     * KPI 5：待发货数（CONFIRMED / IN_PRODUCTION / PARTIAL_SHIPPED）。
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return COUNT(*)，无记录返 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_demand_manage "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND demand_status IN ('CONFIRMED', 'IN_PRODUCTION', 'PARTIAL_SHIPPED') "
        + "   AND (#{storeId} IS NULL OR store_id = #{storeId})")
    Integer countPendingShip(@Param("tenantId") String tenantId, @Param("storeId") Long storeId);

    /**
     * KPI 6：待采购数（SUBMITTED）。
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return COUNT(*)，无记录返 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_demand_manage "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND demand_status = 'SUBMITTED' "
        + "   AND (#{storeId} IS NULL OR store_id = #{storeId})")
    Integer countPendingPurchase(@Param("tenantId") String tenantId, @Param("storeId") Long storeId);

    /**
     * 当日产品结构（按业态 product_type 分组的需求量合计）。
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return 业态:需求量 列表，无记录返空
     */
    @Select("SELECT product_type AS `key`, COALESCE(SUM(demand_quantity), 0) AS `value` "
        + "  FROM t_warehouse_demand_manage "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND demand_date = CURDATE() "
        + "   AND (#{storeId} IS NULL OR store_id = #{storeId}) "
        + " GROUP BY product_type")
    List<StoreGroupCountVo> selectProductStructure(@Param("tenantId") String tenantId, @Param("storeId") Long storeId);

    /**
     * 当月 TOP10 产品排行（按销售额倒序）。
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return TOP10 排行列表，无记录返空
     */
    @Select("SELECT product_id AS productId, product_name AS productName, "
        + "       COALESCE(SUM(sale_amount), 0) AS saleAmount, "
        + "       COALESCE(SUM(sale_qty), 0) AS saleQty "
        + "  FROM t_store_sale_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND sale_date >= DATE_FORMAT(NOW(), '%Y-%m-01') "
        + "   AND (#{storeId} IS NULL OR store_id = #{storeId}) "
        + " GROUP BY product_id, product_name "
        + " ORDER BY SUM(sale_amount) DESC "
        + " LIMIT 10")
    List<StoreProductRankItemVo> selectTop10Products(@Param("tenantId") String tenantId, @Param("storeId") Long storeId);

    /**
     * 近 10 日趋势（每日订单数 + 销售额；客单价 service 计算）。
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return 近 10 日趋势点列表（按日期升序），无记录返空
     */
    @Select("SELECT sale_date AS `date`, COUNT(*) AS orderCount, "
        + "       COALESCE(SUM(sale_amount), 0) AS saleAmount "
        + "  FROM t_store_sale_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND sale_date >= DATE_SUB(CURDATE(), INTERVAL 9 DAY) "
        + "   AND (#{storeId} IS NULL OR store_id = #{storeId}) "
        + " GROUP BY sale_date "
        + " ORDER BY sale_date")
    List<StoreTrendPointVo> selectTrend10Days(@Param("tenantId") String tenantId, @Param("storeId") Long storeId);

    /**
     * mp 单业态当日速览：销售额 + 订单数（按 belong_type 列表过滤）。
     *
     * <p>JOIN {@code t_warehouse_product_info} 取 belong_type（猪肉 = pork/white_bar，
     * 果蔬 = vegetable）。belong_type 列表由 service 传入常量集合。</p>
     *
     * @param tenantId    租户
     * @param storeId     门店 ID（可空）
     * @param belongTypes 业态列表（非空，service 传 PORK_TYPES / VEG_TYPES）
     * @return 单行：salesAmount + orderCount（无记录由 COALESCE 兜 0）
     */
    @Select("<script>"
        + "SELECT COALESCE(SUM(r.sale_amount), 0) AS `value`, "
        + "       CAST(COUNT(*) AS SIGNED) AS orderCount "
        + "  FROM t_store_sale_record r "
        + "  JOIN t_warehouse_product_info p ON r.product_id = p.id "
        + " WHERE r.tenant_id = #{tenantId} "
        + "   AND r.del_flag = '0' "
        + "   AND p.del_flag = '0' "
        + "   AND r.sale_date = CURDATE() "
        + "   AND (#{storeId} IS NULL OR r.store_id = #{storeId}) "
        + "   AND p.belong_type IN "
        + "   <foreach collection='belongTypes' item='bt' open='(' separator=',' close=')'>#{bt}</foreach>"
        + "</script>")
    DailyCategoryRow selectDailyByBelongTypes(@Param("tenantId") String tenantId,
                                              @Param("storeId") Long storeId,
                                              @Param("belongTypes") List<String> belongTypes);

    /**
     * mp 需求统计 Tab：本店 demand 按 demand_status 计数。
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return 状态:笔数 列表，无记录返空
     */
    @Select("SELECT demand_status AS `key`, COUNT(*) AS `value` "
        + "  FROM t_warehouse_demand_manage "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND (#{storeId} IS NULL OR store_id = #{storeId}) "
        + " GROUP BY demand_status")
    List<StoreGroupCountVo> countDemandByStatus(@Param("tenantId") String tenantId, @Param("storeId") Long storeId);

    /**
     * mp 发货统计 Tab：demand_status IN PARTIAL_SHIPPED / COMPLETED 按状态计数。
     *
     * <p>优先用 demand 表状态聚合，避免跨实体 JOIN t_warehouse_shipment。</p>
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return 状态:笔数 列表，无记录返空
     */
    @Select("SELECT demand_status AS `key`, COUNT(*) AS `value` "
        + "  FROM t_warehouse_demand_manage "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND demand_status IN ('PARTIAL_SHIPPED', 'COMPLETED') "
        + "   AND (#{storeId} IS NULL OR store_id = #{storeId}) "
        + " GROUP BY demand_status")
    List<StoreGroupCountVo> countShipByStatus(@Param("tenantId") String tenantId, @Param("storeId") Long storeId);

    /**
     * mp 单业态当日速览原始行（销售额 + 订单数；客单价 service 计算）。
     *
     * @author djs
     * @since STR-DASH-001
     */
    class DailyCategoryRow {

        /** 当日销售额（元）。 */
        private BigDecimal value;

        /** 当日订单数。 */
        private Integer orderCount;

        public BigDecimal getValue() {
            return value;
        }

        public void setValue(BigDecimal value) {
            this.value = value;
        }

        public Integer getOrderCount() {
            return orderCount;
        }

        public void setOrderCount(Integer orderCount) {
            this.orderCount = orderCount;
        }

    }

}
