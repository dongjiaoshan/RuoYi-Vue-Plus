package org.dromara.djs.store.stat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.store.stat.domain.StoreIndicatorPeriod;
import org.dromara.djs.store.stat.domain.StoreOrderCandidate;
import org.dromara.djs.store.stat.domain.StoreProductionIndicatorDaily;
import org.dromara.djs.store.stat.domain.StoreProductionIndicatorVo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface StoreProductionIndicatorMapper extends BaseMapper<StoreProductionIndicatorDaily> {

    record StoreQuantity(Long storeId, BigDecimal quantity) {}
    record DemandQuantity(Long demandId, BigDecimal quantity) {}
    record VegetableReceiptTotals(Long storeId, BigDecimal portions, BigDecimal kgWeight) {}
    record ReturnTotals(Long storeId, BigDecimal porkWeight, BigDecimal vegPortions, BigDecimal vegKgWeight) {}
    record StoreStatus(Long id, String delFlag) {}
    record DamageTotals(Long storeId, BigDecimal registeredPortions, BigDecimal receivedPortions) {}

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
            AND p.belong_type = 'vegetable'
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
        + " AND p.del_flag = '0'"
        + " AND (pp.delivery_check_time IS NULL OR pp.delivery_check_time &lt;= d.received_time)"
        + " AND d.id IN "
        + "<foreach collection='demandIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
        + " GROUP BY d.id</script>")
    List<DemandQuantity> selectVegetableReceiptsByDemand(@Param("tenantId") String tenantId,
                                                         @Param("demandIds") List<Long> demandIds);

    /** 成品的清点时间由发车操作写入。成品行没有 shipment_id，按该行发车时间判定补发。 */
    String PACKED_RECEIPT_DATE = """
        (CASE WHEN pp.delivery_check_time > d.received_time THEN DATE(pp.delivery_check_time)
              ELSE DATE(d.received_time) END)
        """;

    String PACKED_RECEIPT_SOURCE = """
        FROM t_warehouse_demand_manage d
        JOIN t_warehouse_product_production pp ON pp.demand_id = d.id AND pp.tenant_id = d.tenant_id
            AND pp.del_flag = '0' AND pp.is_delivery_check = 1
            AND (pp.store_id IS NULL OR pp.store_id = d.store_id)
        JOIN t_warehouse_product_info p ON p.id = pp.product_id AND p.tenant_id = pp.tenant_id
        WHERE d.tenant_id = #{tenantId} AND d.del_flag = '0'
          AND d.store_id IS NOT NULL AND d.received_time IS NOT NULL
          AND (d.product_id IS NULL OR d.product_id = pp.product_id)
          AND (pp.deliver_dest IS NULL OR pp.deliver_dest NOT IN ('mail', 'gift', 'warehouse_out'))
          AND (pp.deliver_type IS NULL OR pp.deliver_type != 2)
          AND pp.product_weight > 0
        """;

    /** 4/15/28：散装和计件的猪肉成品都取发货实称 kg，白条只在白条流水中计一次。 */
    @Select("SELECT d.store_id AS storeId, SUM(pp.product_weight) AS quantity " + PACKED_RECEIPT_SOURCE
        + " AND p.belong_type = 'pork' AND " + PACKED_RECEIPT_DATE + " = #{date} GROUP BY d.store_id")
    List<StoreQuantity> selectPorkFinishReceipts(@Param("tenantId") String tenantId, @Param("date") LocalDate date);

    /** 7：历史盘点及外售流水仍可通过已软删的白条部位档案识别原料。 */
    @Select("""
        <script>
        SELECT id FROM t_warehouse_product_info
        WHERE tenant_id = #{tenantId} AND product_id IN
        <foreach collection="codes" item="code" open="(" separator="," close=")">#{code}</foreach>
        </script>
        """)
    List<Long> selectWhiteBarCutProductIds(@Param("tenantId") String tenantId, @Param("codes") List<String> codes);

    /** 7：与白条同收货日汇合的同原料外售成品，按门店实称重扣减。 */
    @Select("<script>SELECT d.store_id AS storeId, SUM(pp.product_weight) AS quantity " + PACKED_RECEIPT_SOURCE
        + " AND p.is_material_sold = 1 AND " + PACKED_RECEIPT_DATE + " = #{date}"
        + " AND (#{storeId} IS NULL OR d.store_id = #{storeId})"
        + " AND p.product_material IN "
        + "<foreach collection='materialIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
        + " GROUP BY d.store_id</script>")
    List<StoreQuantity> selectMaterialSoldReceipts(@Param("tenantId") String tenantId, @Param("date") LocalDate date,
                                                  @Param("storeId") Long storeId,
                                                  @Param("materialIds") Set<Long> materialIds);

    String VEG_DAMAGE_SOURCE = VEG_RECEIPT_SOURCE + """
          AND (pp.store_id IS NULL OR pp.store_id = d.store_id)
          AND pp.demand_deduct_qty > 0
          AND pp.is_damaged = 1
          AND pp.damage_time >= GREATEST(d.received_time, COALESCE(pp.delivery_check_time, d.received_time))
        """;

    /** 13/23/38/39：标损记录逐件计份，同时写登记日与对应收货日两套基数。 */
    @Select("SELECT d.store_id AS storeId, "
        + "SUM(CASE WHEN DATE(pp.damage_time) = #{date} THEN 1 ELSE 0 END) AS registeredPortions, "
        + "SUM(CASE WHEN " + PACKED_RECEIPT_DATE + " = #{date} THEN 1 ELSE 0 END) AS receivedPortions "
        + VEG_DAMAGE_SOURCE
        + " AND (DATE(pp.damage_time) = #{date} OR " + PACKED_RECEIPT_DATE + " = #{date}) GROUP BY d.store_id")
    List<DamageTotals> selectDamageTotals(@Param("tenantId") String tenantId, @Param("date") LocalDate date);

    /** 3/14/30：同一次取数覆盖需求日，以及实际首次下单日可能落在当日的单据。 */
    @Select("""
        SELECT d.store_id AS storeId, d.demand_date AS demandDate, d.demand_status AS demandStatus,
               d.create_time AS createTime, d.audit_history AS auditHistory
        FROM t_warehouse_demand_manage d
        WHERE d.tenant_id = #{tenantId} AND d.del_flag = '0' AND d.store_id IS NOT NULL
          AND COALESCE(d.demand_type, 'store') = 'store' AND d.demand_status != 'DRAFT'
          AND (d.demand_date = #{date}
            OR (d.create_time >= #{date} AND d.create_time < DATE_ADD(#{date}, INTERVAL 1 DAY))
            OR (d.audit_history IS NOT NULL AND (d.create_time IS NULL OR d.create_time < #{date})
                AND (d.update_time IS NULL OR d.update_time >= #{date})
                AND JSON_CONTAINS(d.audit_history, '{"event":"SUBMIT"}')))
        """)
    List<StoreOrderCandidate> selectOrderCandidates(@Param("tenantId") String tenantId, @Param("date") LocalDate date);

    /**
     * 对账窗口外的损坏基数。来源贡献为正，已存快照为负，逐店逐日相抵后只重算有差异的日期。
     * 编辑凭证可能覆盖登记时间，旧桶即使已无对应源记录也会被清理。
     */
    @Select("""
        SELECT DISTINCT delta.stat_date
        FROM (
            SELECT sd.store_id, sd.stat_date, -sd.veg_damage_portion_count AS registered,
                   -sd.veg_damage_received_portion_count AS received
            FROM t_store_production_indicator_daily sd
            WHERE sd.tenant_id = #{tenantId} AND sd.del_flag = '0' AND sd.stat_date < #{from}
              AND (sd.veg_damage_portion_count > 0 OR sd.veg_damage_received_portion_count > 0)
            UNION ALL
            SELECT damage.store_id, CASE WHEN bucket.by_receipt = 1 THEN damage.receipt_day ELSE damage.damage_day END,
                   1 - bucket.by_receipt, bucket.by_receipt
            FROM (
                SELECT d.store_id, DATE(pp.damage_time) AS damage_day,
        """ + PACKED_RECEIPT_DATE + " AS receipt_day " + VEG_DAMAGE_SOURCE + """
                  AND d.received_time < #{from}
            ) damage
            CROSS JOIN (SELECT 0 AS by_receipt UNION ALL SELECT 1) bucket
            WHERE CASE WHEN bucket.by_receipt = 1 THEN damage.receipt_day ELSE damage.damage_day END < #{from}
        ) delta
        GROUP BY delta.store_id, delta.stat_date
        HAVING SUM(delta.registered) != 0 OR SUM(delta.received) != 0
        """)
    List<LocalDate> selectHistoricalDamageRefreshDates(@Param("tenantId") String tenantId,
                                                      @Param("from") LocalDate from);

    /** 补确认或修正为窗口外的收货日时，重算该回填日期。 */
    @Select("""
        SELECT DISTINCT DATE(d.received_time)
        FROM t_warehouse_demand_manage d
        WHERE d.tenant_id = #{tenantId} AND d.del_flag = '0' AND d.store_id IS NOT NULL
          AND d.received_time < #{from} AND d.update_time >= #{from} AND d.update_time < #{next}
        """)
    List<LocalDate> selectBackdatedReceiptDates(@Param("tenantId") String tenantId,
                                               @Param("from") LocalDate from, @Param("next") LocalDate next);

    /** 新确认收货跨窗口时，原白条发货日也回刷。 */
    @Select("""
        SELECT DISTINCT s.ship_date
        FROM t_warehouse_shipment s
        JOIN t_warehouse_demand_manage d ON d.id = s.demand_id AND d.tenant_id = s.tenant_id
            AND d.store_id = s.store_id AND d.del_flag = '0'
        WHERE s.tenant_id = #{tenantId} AND s.del_flag = '0' AND s.product_type = 'white_bar'
          AND s.ship_date < #{from} AND d.received_time >= #{from} AND d.received_time < #{next}
        """)
    List<LocalDate> selectPriorWhiteBarShipmentDates(@Param("tenantId") String tenantId,
                                                    @Param("from") LocalDate from, @Param("next") LocalDate next);

    /** 新确认收货跨窗口时，原成品发车日也回刷。 */
    @Select("""
        SELECT DISTINCT DATE(pp.delivery_check_time)
        FROM t_warehouse_demand_manage d
        JOIN t_warehouse_product_production pp ON pp.demand_id = d.id AND pp.tenant_id = d.tenant_id
            AND pp.del_flag = '0' AND pp.is_delivery_check = 1
            AND (pp.store_id IS NULL OR pp.store_id = d.store_id)
        WHERE d.tenant_id = #{tenantId} AND d.del_flag = '0' AND d.store_id IS NOT NULL
          AND d.received_time >= #{from} AND d.received_time < #{next} AND pp.delivery_check_time < #{from}
          AND (pp.deliver_dest IS NULL OR pp.deliver_dest NOT IN ('mail', 'gift', 'warehouse_out'))
          AND (pp.deliver_type IS NULL OR pp.deliver_type != 2)
        """)
    List<LocalDate> selectPriorPackedDispatchDates(@Param("tenantId") String tenantId,
                                                  @Param("from") LocalDate from, @Param("next") LocalDate next);

    /** 11/12：猪肉 2 斤装每份折 1 kg；果蔬按计件、公斤分别汇总登记量。 */
    @Select("SELECT r.store_id AS storeId, "
        + "SUM(CASE WHEN " + StoreReturnStatSql.PORK + " THEN r.return_quantity ELSE 0 END) AS porkWeight, "
        + "SUM(CASE WHEN " + StoreReturnStatSql.VEGETABLE_PORTION + " THEN r.return_quantity ELSE 0 END) AS vegPortions, "
        + "SUM(CASE WHEN " + StoreReturnStatSql.VEGETABLE_KG + " THEN r.return_quantity ELSE 0 END) AS vegKgWeight "
        + StoreReturnStatSql.FROM + StoreReturnStatSql.HISTORICAL_WHERE
        + " AND r.return_date >= #{date} AND r.return_date < DATE_ADD(#{date}, INTERVAL 1 DAY)"
        + " AND (" + StoreReturnStatSql.PORK + " OR " + StoreReturnStatSql.VEGETABLE + ")"
        + " GROUP BY r.store_id")
    List<ReturnTotals> selectReturns(@Param("tenantId") String tenantId, @Param("date") LocalDate date);

    /** 按店聚合自然日/月/年基数，及时天数按报表日截断。 */
    @Select("""
        <script>
        SELECT store_id AS storeId,
          <choose>
            <when test="period.name() == 'MONTH'">CAST(DATE_FORMAT(stat_date, '%Y-%m-01') AS DATE)</when>
            <when test="period.name() == 'YEAR'">MAKEDATE(YEAR(stat_date), 1)</when>
            <otherwise>stat_date</otherwise>
          </choose> AS periodStart,
          <if test="period.name() == 'DAY'">
            MAX(order_timely) AS orderTimely,
            MAX(split_yield_rate) AS splitYieldRate,
            MAX(split_loss_rate) AS splitLossRate,
          </if>
          SUM(CASE WHEN order_timely = 1 AND stat_date &lt;= #{reportDate} THEN 1 ELSE 0 END) AS timelyDayCount,
          MIN(CASE WHEN order_placed = 1 THEN stat_date END) AS firstOrderDate,
          SUM(pork_received_weight_kg) AS porkReceivedWeightKg,
          <choose>
            <when test="period.name() == 'MONTH'">SUM(veg_damage_received_portion_count)</when>
            <otherwise>SUM(veg_damage_portion_count)</otherwise>
          </choose> AS vegDamagePortionCount,
          SUM(veg_damage_received_portion_count) AS vegDamageReceivedPortionCount,
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
                                                   @Param("period") StoreIndicatorPeriod period,
                                                   @Param("reportDate") LocalDate reportDate);
}
