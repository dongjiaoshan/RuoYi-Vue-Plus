package org.dromara.djs.plant.indicator.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Annual;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Daily;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Monthly;

import java.time.LocalDate;
import java.util.List;

/** 指标读写。租户列由租户拦截器或 V1 库默认值填充。 */
@Mapper
public interface PlantProductionIndicatorMapper {

    /** 先锁定年度行，同租户同年度的回刷按顺序取源数据及更新各统计期。 */
    @Insert("""
        INSERT INTO t_plant_production_indicator_annual (id, stat_year, create_time, update_time)
        VALUES (#{id}, #{year}, NOW(), NOW())
        ON DUPLICATE KEY UPDATE id = t_plant_production_indicator_annual.id
        """)
    void lockYear(@Param("id") long id, @Param("year") int year);

    @Insert("""
        INSERT INTO t_plant_production_indicator_daily (
            id, stat_date, planted_area, planted_crop_count, planted_plot_count, farm_work_count,
            farm_work_area, harvest_finished_plot_count, harvest_inbound_weight,
            harvest_inbound_crop_count, harvest_inbound_plot_count, harvest_crop_unmatched_count,
            rotation_plot_count, rotation_area, rotation_wait_days_sum, rotation_wait_valid_count,
            rotation_unmatched_count, disaster_plot_count, create_time, update_time
        ) VALUES (
            #{id}, #{row.statDate}, #{row.plantedArea}, #{row.plantedCropCount},
            #{row.plantedPlotCount}, #{row.farmWorkCount}, #{row.farmWorkArea},
            #{row.harvestFinishedPlotCount}, #{row.harvestInboundWeight},
            #{row.harvestInboundCropCount}, #{row.harvestInboundPlotCount},
            #{row.harvestCropUnmatchedCount}, #{row.rotationPlotCount}, #{row.rotationArea},
            #{row.rotationWaitDaysSum}, #{row.rotationWaitValidCount}, #{row.rotationUnmatchedCount},
            #{row.disasterPlotCount}, NOW(), NOW()
        )
        ON DUPLICATE KEY UPDATE
            planted_area = #{row.plantedArea},
            planted_crop_count = #{row.plantedCropCount},
            planted_plot_count = #{row.plantedPlotCount},
            farm_work_count = #{row.farmWorkCount},
            farm_work_area = #{row.farmWorkArea},
            harvest_finished_plot_count = #{row.harvestFinishedPlotCount},
            harvest_inbound_weight = #{row.harvestInboundWeight},
            harvest_inbound_crop_count = #{row.harvestInboundCropCount},
            harvest_inbound_plot_count = #{row.harvestInboundPlotCount},
            harvest_crop_unmatched_count = #{row.harvestCropUnmatchedCount},
            rotation_plot_count = #{row.rotationPlotCount},
            rotation_area = #{row.rotationArea},
            rotation_wait_days_sum = #{row.rotationWaitDaysSum},
            rotation_wait_valid_count = #{row.rotationWaitValidCount},
            rotation_unmatched_count = #{row.rotationUnmatchedCount},
            disaster_plot_count = #{row.disasterPlotCount},
            update_time = NOW()
        """)
    void upsertDaily(@Param("id") long id, @Param("row") Daily row);

    @Select("""
        SELECT
            stat_date, planted_area, planted_crop_count, planted_plot_count, farm_work_count,
            farm_work_area, harvest_finished_plot_count, harvest_inbound_weight,
            harvest_inbound_crop_count, harvest_inbound_plot_count, harvest_crop_unmatched_count,
            rotation_plot_count, rotation_area, rotation_wait_days_sum, rotation_wait_valid_count,
            rotation_unmatched_count, disaster_plot_count
          FROM t_plant_production_indicator_daily
         WHERE tenant_id = #{tenantId} AND del_flag = '0' AND del_unique = 0
           AND stat_date BETWEEN #{from} AND #{to}
         ORDER BY stat_date
        """)
    List<Daily> selectDaily(@Param("tenantId") String tenantId, @Param("from") LocalDate from,
                           @Param("to") LocalDate to);

    @Insert("""
        INSERT INTO t_plant_production_indicator_monthly (
            id, stat_month, planted_area, planted_crop_count, planted_plot_count, plant_plan_count,
            plant_plan_completed_count, farm_work_count, farm_work_area, harvest_finished_plot_count,
            harvest_inbound_weight, harvest_inbound_crop_count, harvest_inbound_plot_count,
            harvest_crop_unmatched_count, rotation_plot_count, rotation_area,
            rotation_unmatched_count, harvest_performance_amount, create_time, update_time
        ) VALUES (
            #{id}, #{row.statMonth}, #{row.plantedArea}, #{row.plantedCropCount},
            #{row.plantedPlotCount}, #{row.plantPlanCount}, #{row.plantPlanCompletedCount},
            #{row.farmWorkCount}, #{row.farmWorkArea}, #{row.harvestFinishedPlotCount},
            #{row.harvestInboundWeight}, #{row.harvestInboundCropCount},
            #{row.harvestInboundPlotCount}, #{row.harvestCropUnmatchedCount},
            #{row.rotationPlotCount}, #{row.rotationArea}, #{row.rotationUnmatchedCount},
            #{row.harvestPerformanceAmount}, NOW(), NOW()
        )
        ON DUPLICATE KEY UPDATE
            planted_area = #{row.plantedArea},
            planted_crop_count = #{row.plantedCropCount},
            planted_plot_count = #{row.plantedPlotCount},
            plant_plan_count = #{row.plantPlanCount},
            plant_plan_completed_count = #{row.plantPlanCompletedCount},
            farm_work_count = #{row.farmWorkCount},
            farm_work_area = #{row.farmWorkArea},
            harvest_finished_plot_count = #{row.harvestFinishedPlotCount},
            harvest_inbound_weight = #{row.harvestInboundWeight},
            harvest_inbound_crop_count = #{row.harvestInboundCropCount},
            harvest_inbound_plot_count = #{row.harvestInboundPlotCount},
            harvest_crop_unmatched_count = #{row.harvestCropUnmatchedCount},
            rotation_plot_count = #{row.rotationPlotCount},
            rotation_area = #{row.rotationArea},
            rotation_unmatched_count = #{row.rotationUnmatchedCount},
            harvest_performance_amount = #{row.harvestPerformanceAmount},
            update_time = NOW()
        """)
    void upsertMonthly(@Param("id") long id, @Param("row") Monthly row);

    @Select("""
        SELECT
            stat_month, planted_area, planted_crop_count, planted_plot_count, plant_plan_count,
            plant_plan_completed_count, farm_work_count, farm_work_area, harvest_finished_plot_count,
            harvest_inbound_weight, harvest_inbound_crop_count, harvest_inbound_plot_count,
            harvest_crop_unmatched_count, rotation_plot_count, rotation_area,
            rotation_unmatched_count, harvest_performance_amount
          FROM t_plant_production_indicator_monthly
         WHERE tenant_id = #{tenantId} AND del_flag = '0' AND del_unique = 0
           AND stat_month BETWEEN #{from} AND #{to}
         ORDER BY stat_month
        """)
    List<Monthly> selectMonthly(@Param("tenantId") String tenantId, @Param("from") LocalDate from,
                           @Param("to") LocalDate to);

    @Insert("""
        INSERT INTO t_plant_production_indicator_annual (
            id, stat_year, planted_area, plant_plan_count, plant_plan_completed_count,
            harvest_inbound_weight, disaster_record_count, rotation_plot_count,
            harvest_performance_amount, performance_through_month, idle_days_sum, idle_interval_count, create_time, update_time
        ) VALUES (
            #{id}, #{row.statYear}, #{row.plantedArea}, #{row.plantPlanCount},
            #{row.plantPlanCompletedCount}, #{row.harvestInboundWeight}, #{row.disasterRecordCount},
            #{row.rotationPlotCount}, #{row.harvestPerformanceAmount},
            #{row.performanceThroughMonth}, #{row.idleDaysSum}, #{row.idleIntervalCount}, NOW(), NOW()
        )
        ON DUPLICATE KEY UPDATE
            planted_area = #{row.plantedArea},
            plant_plan_count = #{row.plantPlanCount},
            plant_plan_completed_count = #{row.plantPlanCompletedCount},
            harvest_inbound_weight = #{row.harvestInboundWeight},
            disaster_record_count = #{row.disasterRecordCount},
            rotation_plot_count = #{row.rotationPlotCount},
            harvest_performance_amount = #{row.harvestPerformanceAmount},
            performance_through_month = #{row.performanceThroughMonth},
            idle_days_sum = #{row.idleDaysSum},
            idle_interval_count = #{row.idleIntervalCount},
            update_time = NOW()
        """)
    void upsertAnnual(@Param("id") long id, @Param("row") Annual row);

    @Select("""
        SELECT
            stat_year, planted_area, plant_plan_count, plant_plan_completed_count,
            harvest_inbound_weight, disaster_record_count, rotation_plot_count,
            harvest_performance_amount, performance_through_month, idle_days_sum, idle_interval_count
          FROM t_plant_production_indicator_annual
         WHERE tenant_id = #{tenantId} AND del_flag = '0' AND del_unique = 0
           AND stat_year BETWEEN #{from} AND #{to}
         ORDER BY stat_year
        """)
    List<Annual> selectAnnual(@Param("tenantId") String tenantId, @Param("from") int from,
                           @Param("to") int to);
}
