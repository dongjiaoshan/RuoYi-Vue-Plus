package org.dromara.djs.plant.indicator.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.IdleTotals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 批量读取统计源及配对所需历史，日期窗口右开。 */
@Mapper
public interface ProductionIndicatorSourceMapper {

    // 农事单的游客采摘独立存为 harvest_activity，正常采收 harvest 也不属于十类农事。
    String WORK_TYPES = "('tillage_break','tillage_prepare','fertilize','transplant','water_fertilize',"
        + "'irrigation','weed','pruning','pest_control','rotation')";

    String INBOUND_WEIGHT = "COALESCE(f.change_quantity, f.change_num)";
    String INBOUND_FILTER = " AND f.del_flag = '0' AND f.inout_type = 'IN' AND f.flow_type = 'veg_stock_in'"
        + " AND " + INBOUND_WEIGHT + " > 0 ";

    @Select("""
        SELECT d.id, d.plot_id, d.crop_id, d.plot_area,
               CASE WHEN d.plant_status = 'completed' THEN d.end_actualdate END AS plant_end,
               CASE WHEN d.harvest_status = 'completed' THEN d.end_harvestdate END AS harvest_end,
               p.plan_year, d.plant_month
          FROM t_plant_plant_details d
          LEFT JOIN t_plant_plant_plan p ON p.id = d.plant_id AND p.tenant_id = #{tenantId} AND p.del_flag = '0'
         WHERE d.tenant_id = #{tenantId} AND d.del_flag = '0'
           AND (p.plan_year = #{year} OR d.end_actualdate < #{until} OR d.end_harvestdate < #{until})
        """)
    List<Detail> selectDetails(@Param("tenantId") String tenantId, @Param("year") int year,
                               @Param("until") LocalDate until);

    @Select("""
        SELECT f.plot_id, f.farm_date, f.farm_type, p.plot_area, f.plant_detail_id
          FROM t_plant_farm_records f
          LEFT JOIN t_plant_plot_info p ON p.id = f.plot_id AND p.tenant_id = #{tenantId}
         WHERE f.tenant_id = #{tenantId} AND f.del_flag = '0'
           AND (f.farm_type = 'rotation'
                OR (f.farm_date >= #{from} AND f.farm_date < #{until}
                    AND (f.farm_type = 'disaster' OR f.farm_type IN
        """ + WORK_TYPES + "))) ORDER BY f.farm_date, f.id")
    List<FarmEvent> selectFarmEvents(@Param("tenantId") String tenantId, @Param("from") LocalDate from,
                                    @Param("until") LocalDate until);

    @Select("SELECT DATE(f.flow_date), f.plot_id, COALESCE(f.crop_id, b.crop_id), f.harvest_batch_id, "
        + INBOUND_WEIGHT + """
          FROM t_warehouse_stock_flow f
          LEFT JOIN t_warehouse_planting_record b ON b.id = f.harvest_batch_id AND b.tenant_id = #{tenantId}
         WHERE f.tenant_id = #{tenantId} AND f.flow_date >= #{from} AND f.flow_date < #{until}
        """ + INBOUND_FILTER)
    List<Inbound> selectInbound(@Param("tenantId") String tenantId, @Param("from") LocalDate from,
                                @Param("until") LocalDate until);

    @Select("""
        SELECT stat_month, SUM(performance_amount)
          FROM t_plant_work_performance
         WHERE tenant_id = #{tenantId} AND del_flag = '0' AND performance_amount IS NOT NULL
           AND stat_month BETWEEN #{fromMonth} AND #{toMonth}
         GROUP BY stat_month
        """)
    List<Performance> selectPerformance(@Param("tenantId") String tenantId,
                                        @Param("fromMonth") String fromMonth, @Param("toMonth") String toMonth);

    /** 区间右开，只有与报表窗口相交至少一天的区间才计一次。 */
    @Select("""
        SELECT COALESCE(SUM(DATEDIFF(LEAST(COALESCE(idle_ended_on, #{until}), #{until}),
                                     GREATEST(idle_started_on, #{from}))), 0),
               COUNT(*)
          FROM t_plant_plot_idle_interval
         WHERE tenant_id = #{tenantId} AND del_flag = '0' AND del_unique = 0
           AND idle_started_on < #{until}
           AND COALESCE(idle_ended_on, #{until}) > GREATEST(idle_started_on, #{from})
        """)
    IdleTotals selectIdleTotals(@Param("tenantId") String tenantId, @Param("from") LocalDate from,
                               @Param("until") LocalDate until);

    record Detail(long id, Long plotId, Long cropId, BigDecimal area, LocalDate plantEnd, LocalDate harvestEnd,
                  Integer planYear, Integer planMonth) {
    }

    record FarmEvent(Long plotId, LocalDate date, String type, BigDecimal area, Long detailId) {
    }

    record Inbound(LocalDate date, Long plotId, Long cropId, Long harvestBatchId, BigDecimal weight) {
    }

    record Performance(String statMonth, BigDecimal amount) {
    }
}
