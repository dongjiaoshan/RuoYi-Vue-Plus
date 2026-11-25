package org.dromara.djs.store.stat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.store.stat.domain.StoreIndicatorPeriod;
import org.dromara.djs.store.stat.domain.StoreProductionIndicatorDaily;
import org.dromara.djs.store.stat.domain.StoreProductionIndicatorVo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface StoreProductionIndicatorMapper extends BaseMapper<StoreProductionIndicatorDaily> {

    record StoreQuantity(Long storeId, BigDecimal quantity) {}
    record DemandQuantity(Long demandId, BigDecimal quantity) {}
    record VegetableReceiptTotals(Long storeId, BigDecimal portions, BigDecimal kgWeight) {}
    record ReturnTotals(Long storeId, BigDecimal porkWeight, BigDecimal vegPortions, BigDecimal vegKgWeight) {}
    record StoreStatus(Long id, String delFlag) {}

    /** 同租户刷新先按主键锁门店行，串行化定时及手动重跑的删除、插入。 */
    @Select("SELECT id, del_flag FROM t_md_store WHERE tenant_id = #{tenantId} ORDER BY id FOR UPDATE")
    List<StoreStatus> lockStores(@Param("tenantId") String tenantId);

    @Delete("DELETE FROM t_store_production_indicator_daily WHERE tenant_id = #{tenantId} AND stat_date = #{date}")
    int deleteDay(@Param("tenantId") String tenantId, @Param("date") LocalDate date);

    /** 6：收货日内已发批次归收货日，收货次日起的补发批次归发货日。 */
    @Select("""
        SELECT s.store_id AS storeId, SUM(s.ship_quantity) AS quantity
        FROM t_warehouse_shipment s
        JOIN t_warehouse_demand_manage d ON d.id = s.demand_id AND d.tenant_id = s.tenant_id
            AND d.store_id = s.store_id AND d.del_flag = '0'
        WHERE s.tenant_id = #{tenantId} AND s.del_flag = '0' AND s.product_type = 'white_bar'
          AND (#{storeId} IS NULL OR s.store_id = #{storeId})
          AND ((d.received_time >= #{date} AND d.received_time < DATE_ADD(#{date}, INTERVAL 1 DAY)
                AND s.ship_date <= #{date})
            OR (s.ship_date = #{date} AND d.received_time < #{date}))
        GROUP BY s.store_id
        """)
    List<StoreQuantity> selectWhiteBarReceipts(@Param("tenantId") String tenantId,
                                               @Param("date") LocalDate date,
                                               @Param("storeId") Long storeId);

    String VEG_RECEIPT_IS_KG = "LOWER(TRIM(pp.product_unit)) IN ('kg','公斤','千克')";

    String VEG_RECEIPT_SOURCE = """
        FROM t_warehouse_demand_manage d
        JOIN t_warehouse_product_production pp ON pp.demand_id = d.id AND pp.tenant_id = d.tenant_id
            AND pp.del_flag = '0' AND pp.is_delivery_check = 1
        JOIN t_warehouse_product_info p ON p.id = pp.product_id AND p.tenant_id = pp.tenant_id
            AND p.del_flag = '0' AND p.belong_type = 'vegetable'
        WHERE d.tenant_id = #{tenantId} AND d.del_flag = '0'
          AND d.product_type = 'vegetable' AND d.store_id IS NOT NULL
          AND d.received_time IS NOT NULL
          AND (pp.deliver_dest IS NULL OR pp.deliver_dest NOT IN ('mail', 'gift', 'warehouse_out'))
          AND (pp.deliver_type IS NULL OR pp.deliver_type != 2)
        """;

    /** 5/16/29：收货日内的批次归收货日，之后的补发按 delivery_check_time 的日期。标损行计入。 */
    @Select("SELECT d.store_id AS storeId, "
        + "SUM(CASE WHEN " + VEG_RECEIPT_IS_KG + " THEN 1 ELSE pp.demand_deduct_qty END) AS portions, "
        + "SUM(CASE WHEN " + VEG_RECEIPT_IS_KG + " THEN COALESCE(pp.product_weight, 0) ELSE 0 END) AS kgWeight "
        + VEG_RECEIPT_SOURCE + """
          AND pp.demand_deduct_qty > 0
          AND ((d.received_time >= #{date} AND d.received_time < DATE_ADD(#{date}, INTERVAL 1 DAY)
                AND (pp.delivery_check_time IS NULL OR pp.delivery_check_time < DATE_ADD(#{date}, INTERVAL 1 DAY)))
            OR (d.received_time < #{date} AND pp.delivery_check_time >= #{date}
                AND pp.delivery_check_time < DATE_ADD(#{date}, INTERVAL 1 DAY)))
          GROUP BY d.store_id
        """)
    List<VegetableReceiptTotals> selectVegetableReceipts(@Param("tenantId") String tenantId,
                                                         @Param("date") LocalDate date);

    /** 门店需求页到店量按需求单位累加收货时已清点的抵扣量。 */
    @Select("<script>SELECT d.id AS demandId, COALESCE(SUM(pp.demand_deduct_qty), 0) AS quantity "
        + VEG_RECEIPT_SOURCE
        + " AND (pp.delivery_check_time IS NULL OR pp.delivery_check_time &lt;= d.received_time)"
        + " AND d.id IN "
        + "<foreach collection='demandIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
        + " GROUP BY d.id</script>")
    List<DemandQuantity> selectVegetableReceiptsByDemand(@Param("tenantId") String tenantId,
                                                         @Param("demandIds") List<Long> demandIds);

    /** 11/12：猪肉 2 斤装每份折 1 kg；果蔬按计件、公斤分别汇总登记量。 */
    @Select("SELECT r.store_id AS storeId, "
        + "SUM(CASE WHEN " + StoreReturnStatSql.PORK + " THEN r.return_quantity ELSE 0 END) AS porkWeight, "
        + "SUM(CASE WHEN " + StoreReturnStatSql.VEGETABLE_PORTION + " THEN r.return_quantity ELSE 0 END) AS vegPortions, "
        + "SUM(CASE WHEN " + StoreReturnStatSql.VEGETABLE_KG + " THEN r.return_quantity ELSE 0 END) AS vegKgWeight "
        + StoreReturnStatSql.FROM + StoreReturnStatSql.WHERE
        + " AND r.return_date >= #{date} AND r.return_date < DATE_ADD(#{date}, INTERVAL 1 DAY)"
        + " AND (" + StoreReturnStatSql.PORK + " OR " + StoreReturnStatSql.VEGETABLE + ")"
        + " GROUP BY r.store_id")
    List<ReturnTotals> selectReturns(@Param("tenantId") String tenantId, @Param("date") LocalDate date);

    /** 日/月/年均按店聚合指标基数；汇总后才计算两个比率。 */
    @Select("""
        <script>
        SELECT store_id AS storeId,
          <choose>
            <when test="period.name() == 'MONTH'">CAST(DATE_FORMAT(stat_date, '%Y-%m-01') AS DATE)</when>
            <when test="period.name() == 'YEAR'">MAKEDATE(YEAR(stat_date), 1)</when>
            <otherwise>stat_date</otherwise>
          </choose> AS periodStart,
          SUM(veg_received_portion_count) AS vegReceivedPortionCount,
          SUM(veg_receive_kg_weight) AS vegReceiveKgWeight,
          SUM(white_bar_received_weight) AS whiteBarReceivedWeight,
          SUM(split_product_weight) AS splitProductWeight,
          SUM(split_loss_weight) AS splitLossWeight,
          SUM(pork_return_weight_kg) AS porkReturnWeightKg,
          SUM(veg_return_portion_count) AS vegReturnPortionCount,
          SUM(veg_return_kg_weight) AS vegReturnKgWeight
        FROM t_store_production_indicator_daily
        WHERE tenant_id = #{tenantId} AND del_flag = '0'
          AND stat_date &gt;= #{from} AND stat_date &lt; #{nextStart}
          <if test="storeIds != null">
            AND store_id IN
            <foreach collection="storeIds" item="id" open="(" separator="," close=")">#{id}</foreach>
          </if>
        GROUP BY store_id, periodStart
        ORDER BY periodStart, store_id
        </script>
        """)
    List<StoreProductionIndicatorVo> selectPeriod(@Param("tenantId") String tenantId,
                                                   @Param("storeIds") List<Long> storeIds,
                                                   @Param("from") LocalDate from,
                                                   @Param("nextStart") LocalDate nextStart,
                                                   @Param("period") StoreIndicatorPeriod period);
}
