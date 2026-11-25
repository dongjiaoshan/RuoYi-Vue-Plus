package org.dromara.djs.plant.indicator.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
               CASE WHEN COALESCE(d.transplant_adjusted, 0) <> 1 THEN p.plan_year END AS plan_year,
               d.plant_month, d.plant_id, d.begin_actualdate, d.expected_yield, d.actual_yield
          FROM t_plant_plant_details d
          LEFT JOIN t_plant_plant_plan p ON p.id = d.plant_id AND p.tenant_id = #{tenantId} AND p.del_flag = '0'
         WHERE d.tenant_id = #{tenantId} AND d.del_flag = '0'
           AND (p.plan_year = #{year} OR d.end_actualdate < #{until} OR d.end_harvestdate < #{until}
                OR d.begin_actualdate < #{until})
        """)
    List<Detail> selectDetails(@Param("tenantId") String tenantId, @Param("year") int year,
                               @Param("until") LocalDate until);

    @Select("""
        SELECT f.plot_id, f.farm_date, f.farm_type, p.plot_area, f.plant_detail_id,
               f.crop_id, f.plant_id, f.loss_rate, f.loss_yield
          FROM t_plant_farm_records f
          LEFT JOIN t_plant_plot_info p ON p.id = f.plot_id AND p.tenant_id = #{tenantId}
         WHERE f.tenant_id = #{tenantId} AND f.del_flag = '0'
           AND (f.farm_type IN ('rotation','transplant')
                OR (f.farm_date < #{until} AND f.farm_type = 'disaster')
                OR (f.farm_date >= #{from} AND f.farm_date < #{until} AND f.farm_type IN
        """ + WORK_TYPES + ")) ORDER BY f.farm_date, f.id")
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

    /** 月台到货与接收按相同的作物、地块、产品对账，保留跨年未收货的来源。 */
    @Select("""
        SELECT hr.handle_time, hr.plot_id, hr.crop_id, hr.product_id, hr.record_weight,
               CASE
                 WHEN hr.remark = '毛菜间出库' OR vh.planting_record_id IS NOT NULL THEN 'transfer'
                 WHEN vh.id IS NOT NULL AND vh.is_weighed = 2 AND vh.picked_weight = 0
                      AND vh.send_platform_weight = hr.record_weight THEN 'direct'
                 ELSE 'unknown'
               END AS origin
          FROM t_warehouse_handle_record hr
          LEFT JOIN t_warehouse_vegetable_handle vh ON vh.id = hr.handle_id
               AND vh.tenant_id = #{tenantId} AND vh.del_flag = '0'
         WHERE hr.tenant_id = #{tenantId} AND hr.del_flag = '0'
           AND hr.record_type = 2 AND hr.handle_target = 2 AND hr.record_weight > 0
           AND hr.handle_time < #{until}
        """)
    List<DockDispatch> selectDockDispatches(@Param("tenantId") String tenantId, @Param("until") LocalDate until);

    @Select("SELECT f.id, COALESCE(r.receive_time, f.flow_date), f.plot_id, COALESCE(r.crop_id, f.crop_id), "
        + " COALESCE(r.product_id, f.product_id), COALESCE(r.weight, " + INBOUND_WEIGHT + ", 0), " + """
          CASE WHEN r.is_finish = 1 THEN COALESCE(r.loss_weight, 0) ELSE 0 END
          FROM t_warehouse_stock_flow f
          LEFT JOIN t_warehouse_veg_receive r ON r.receive_no = f.flow_no AND r.plot_id = f.plot_id
               AND r.tenant_id = #{tenantId} AND r.del_flag = '0' AND r.receive_type = 1
         WHERE f.tenant_id = #{tenantId} AND f.del_flag = '0'
           AND f.flow_type = 'veg_receive_in' AND f.inout_type = 'IN'
           AND COALESCE(r.receive_time, f.flow_date) < #{until}
        """)
    List<DockReceipt> selectDockReceipts(@Param("tenantId") String tenantId, @Param("until") LocalDate until);

    /** 同一笔称重多班组平摊，采摘活动未指定班组时用同地块同作物的采收班组。 */
    @Select("""
        SELECT x.pick_date, x.team_id,
               SUM(x.weight * COALESCE(price.perf_price, 0) * x.perf_percent / (100 * x.team_count))
          FROM (
            SELECT DATE(r.handle_time) AS pick_date, r.crop_id, r.product_id, r.record_weight AS weight,
                   COALESCE(rt.team_id, r.team_id) AS team_id, COALESCE(rt.team_count, 1) AS team_count,
                   COALESCE(r.perf_percent, 100) AS perf_percent, 1 AS weighing
              FROM t_warehouse_handle_record r
              LEFT JOIN (
                  SELECT teams.record_id, teams.team_id, COUNT(*) OVER (PARTITION BY teams.record_id) AS team_count
                    FROM (
                      SELECT DISTINCT record_id, team_id FROM t_warehouse_handle_record_team
                       WHERE tenant_id = #{tenantId} AND del_flag = '0'
                    ) teams
              ) rt ON rt.record_id = r.id
             WHERE r.tenant_id = #{tenantId} AND r.del_flag = '0' AND r.record_type = 1
               AND r.record_weight > 0 AND r.handle_time >= #{from} AND r.handle_time < #{until}
            UNION ALL
            SELECT a.activity_date, a.crop_id, a.product_id, a.daily_weight, at.team_id, at.team_count, 100, 0
              FROM t_plant_plant_activity a
              JOIN LATERAL (
                  SELECT teams.team_id, teams.priority, MIN(teams.priority) OVER () AS preferred_priority,
                         COUNT(*) OVER (PARTITION BY teams.priority) AS team_count
                    FROM (
                      SELECT team_id, 1 AS priority
                        FROM t_plant_activity_team
                       WHERE activity_id = a.id AND tenant_id = #{tenantId} AND del_flag = '0'
                      UNION DISTINCT
                      SELECT dt.team_id, 2 AS priority
                        FROM t_plant_plant_details d
                        JOIN t_plant_details_team dt ON dt.detail_id = d.id AND dt.role = 'harvest'
                             AND dt.tenant_id = #{tenantId} AND dt.del_flag = '0'
                       WHERE d.plot_id = a.plot_id AND d.crop_id = a.crop_id
                         AND d.tenant_id = #{tenantId} AND d.del_flag = '0'
                    ) teams
              ) at ON at.priority = at.preferred_priority
             WHERE a.tenant_id = #{tenantId} AND a.del_flag = '0' AND a.pick_dest IS NOT NULL
               AND a.daily_weight > 0 AND a.activity_date >= #{from} AND a.activity_date < #{until}
          ) x
          LEFT JOIN LATERAL (
              SELECT cp.perf_price
                FROM (
                    SELECT cp.product_id, cp.perf_price, c.id AS active_crop_id,
                           ROW_NUMBER() OVER (ORDER BY cp.id) AS product_rank
                      FROM t_plant_crop_product cp
                      JOIN t_warehouse_product_info p ON p.id = cp.product_id
                           AND p.tenant_id = #{tenantId} AND p.del_flag = '0'
                      LEFT JOIN t_plant_crop_info c ON c.id = cp.crop_id
                           AND c.tenant_id = #{tenantId} AND c.del_flag = '0'
                     WHERE cp.tenant_id = #{tenantId} AND cp.del_flag = '0' AND cp.crop_id = x.crop_id
                ) cp
               WHERE (cp.product_id = x.product_id AND cp.perf_price IS NOT NULL)
                  OR (cp.product_rank = 1 AND
                     (x.product_id IS NULL OR (x.weighing = 1 AND cp.active_crop_id IS NOT NULL)))
               ORDER BY COALESCE(cp.product_id = x.product_id AND cp.perf_price IS NOT NULL, 0) DESC
               LIMIT 1
          ) price ON TRUE
         WHERE x.team_id IS NOT NULL
         GROUP BY x.pick_date, x.team_id
        """)
    List<DailyPerformance> selectDailyPerformance(@Param("tenantId") String tenantId, @Param("from") LocalDate from,
                                                  @Param("until") LocalDate until);

    @Select("""
        SELECT team_id, user_id, COALESCE(join_date, DATE(create_time)),
               CASE WHEN del_flag = '1' THEN DATE(update_time) END
          FROM t_plant_work_people
         WHERE tenant_id = #{tenantId} AND COALESCE(join_date, DATE(create_time)) < #{until}
           AND (del_flag = '0' OR (del_flag = '1' AND update_time >= #{from}))
        """)
    List<Member> selectTeamMembers(@Param("tenantId") String tenantId, @Param("from") LocalDate from,
                                   @Param("until") LocalDate until);

    @Select("""
        SELECT plot_id, idle_started_on, idle_ended_on, start_source
          FROM t_plant_plot_idle_interval
         WHERE tenant_id = #{tenantId} AND del_flag = '0' AND del_unique = 0
        """)
    List<IdleInterval> selectIdleIntervals(@Param("tenantId") String tenantId);

    /** 目标地块的开工日取移栽单日期，物化明细沿用的源育苗日期不参与回补。 */
    @Select("""
        SELECT CASE WHEN farm_type = 'rotation' THEN plot_id ELSE transplant_plot END AS plot_id,
               farm_date, farm_type = 'rotation' AS rotation
          FROM t_plant_farm_records
         WHERE tenant_id = #{tenantId} AND del_flag = '0' AND farm_date IS NOT NULL
           AND ((farm_type = 'rotation' AND plot_id IS NOT NULL)
                OR (farm_type = 'transplant' AND transplant_plot IS NOT NULL))
        UNION ALL
        SELECT plot_id, COALESCE(begin_actualdate, end_actualdate), 0 AS rotation
          FROM t_plant_plant_details
         WHERE tenant_id = #{tenantId} AND del_flag = '0' AND plot_id IS NOT NULL
           AND COALESCE(transplant_adjusted, 0) <> 1
           AND (begin_actualdate IS NOT NULL OR (plant_status = 'completed' AND end_actualdate IS NOT NULL))
        """)
    List<IdleTransition> selectIdleTransitions(@Param("tenantId") String tenantId);

    record Detail(long id, Long plotId, Long cropId, BigDecimal area, LocalDate plantEnd, LocalDate harvestEnd,
                  Integer planYear, Integer planMonth, Long planId, LocalDate plantBegin,
                  BigDecimal expectedYield, BigDecimal actualYield) {
    }

    record FarmEvent(Long plotId, LocalDate date, String type, BigDecimal area, Long detailId,
                     Long cropId, Long planId, BigDecimal lossRate, BigDecimal lossYield) {
    }

    record Inbound(LocalDate date, Long plotId, Long cropId, Long harvestBatchId, BigDecimal weight) {
    }

    record DockDispatch(LocalDateTime time, Long plotId, Long cropId, Long productId,
                        BigDecimal weight, String origin) {
    }

    record DockReceipt(long id, LocalDateTime time, Long plotId, Long cropId, Long productId,
                       BigDecimal weight, BigDecimal loss) {
    }

    record Performance(String statMonth, BigDecimal amount) {
    }

    record DailyPerformance(LocalDate date, Long teamId, BigDecimal amount) {
    }

    record Member(long teamId, long userId, LocalDate joinedOn, LocalDate leftOn) {
        public boolean onRoster(LocalDate date) {
            return !joinedOn.isAfter(date) && (leftOn == null || date.isBefore(leftOn));
        }
    }

    record IdleInterval(long plotId, LocalDate start, LocalDate end, String source) {
    }

    record IdleTransition(long plotId, LocalDate date, boolean rotation) {
    }
}
