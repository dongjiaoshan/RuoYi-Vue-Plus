package org.dromara.djs.plant.plan.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.plan.domain.PlantPlan;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanSummaryVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanVo;

/**
 * 种植计划主表 Mapper（PLT-PLAN-001）。
 *
 * @author djs
 * @since PLT-PLAN-001
 */
public interface PlantPlanMapper extends BaseMapperPlus<PlantPlan, PlantPlanVo> {

    /**
     * 重算主表 4 个聚合字段（total_area / total_plot / earliest_harvestdate / last_harvestdate）。
     *
     * <p>实现：直接 UPDATE plan 用子查询聚合 details。details.del_flag='0' 过滤软删行。</p>
     *
     * <p>调用时机：</p>
     * <ul>
     *   <li>create：INSERT details 后</li>
     *   <li>update：details 增删改后</li>
     * </ul>
     *
     * @param planId 主表 id
     * @return 受影响行数（1 或 0）
     */
    @Update("UPDATE t_plant_plant_plan p "
        + "LEFT JOIN ("
        + "  SELECT plant_id, "
        + "         SUM(plot_area) AS area, "
        + "         COUNT(1) AS cnt, "
        + "         MIN(earliest_harvestdate) AS min_h, "
        + "         MAX(last_harvestdate) AS max_h "
        + "  FROM t_plant_plant_details "
        + "  WHERE plant_id = #{planId} AND del_flag = '0' "
        + "  GROUP BY plant_id"
        + ") d ON p.id = d.plant_id "
        + "SET p.total_area = COALESCE(d.area, 0), "
        + "    p.total_plot = COALESCE(d.cnt, 0), "
        + "    p.earliest_harvestdate = d.min_h, "
        + "    p.last_harvestdate = d.max_h, "
        + "    p.update_time = NOW() "
        + "WHERE p.id = #{planId} AND p.del_flag = '0'")
    int recalcAggregates(@Param("planId") Long planId);

    /**
     * 跨模块只读：聚合"进行中"种植计划给需求确认 SummaryBar 用
     * （DJS-FIX-ADMIN-W22-003）。
     *
     * <p>"进行中"语义：{@code plant_status IN ('pending','ongoing')}。聚合方式：</p>
     * <ul>
     *   <li>{@code plotCount} = COUNT(DISTINCT d.plot_id)（同地块在多月/多期只算一次）</li>
     *   <li>{@code expectedYieldKg} = SUM(COALESCE(d.expected_yield,0)) - SUM(COALESCE(d.actual_yield,0))，
     *       负值兜 0</li>
     *   <li>{@code earliestPickDate} = MIN(d.earliest_harvestdate)（NULL safe）</li>
     * </ul>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户）。
     * 没有数据时返一个零值 VO 而非 null（service 层兜底）。</p>
     */
    @Select("""
        SELECT
            COUNT(DISTINCT d.plot_id) AS plotCount,
            GREATEST(
                COALESCE(SUM(d.expected_yield), 0) - COALESCE(SUM(d.actual_yield), 0),
                0
            ) AS expectedYieldKg,
            MIN(d.earliest_harvestdate) AS earliestPickDate
          FROM t_plant_plant_plan p
          JOIN t_plant_plant_details d
            ON d.plant_id = p.id
           AND d.del_flag = '0'
           AND d.tenant_id = p.tenant_id
         WHERE p.del_flag = '0'
           AND p.tenant_id = '1001'
           AND p.plant_status IN ('pending','ongoing')
        """)
    PlantPlanSummaryVo selectDemandSummary();
}
