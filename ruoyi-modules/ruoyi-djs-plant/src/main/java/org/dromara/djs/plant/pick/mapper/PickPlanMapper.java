package org.dromara.djs.plant.pick.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.pick.domain.vo.PickPlanGroupVo;

import java.util.List;

/**
 * 采摘计划聚合 Mapper（PLT-PLAN-002）。
 *
 * <p>专做 GROUP BY (plant_id, crop_id) 聚合查询，配合
 * {@link org.dromara.djs.plant.plan.mapper.PlantDetailsMapper} 单行操作。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
public interface PickPlanMapper {

    /**
     * 按计划 × 作物聚合采摘计划行（用于 admin 列表 doc/10 §F-PLT-05）。
     *
     * <p>过滤：{@code details.del_flag='0' AND plan.del_flag='0' AND tenant_id=current}。</p>
     *
     * @param tenantId      租户编号
     * @param planYear      可选：年份过滤
     * @param planSeason    可选：季节过滤
     * @param cropId        可选：作物 id 过滤
     * @param harvestStatus 可选：details.harvest_status 过滤
     * @return 聚合行列表（按 plan_year DESC, plan_id DESC, crop_id ASC 排序）
     */
    @Select("""
        <script>
        SELECT
            d.plant_id        AS plantId,
            p.plan_no         AS planNo,
            p.plan_year       AS planYear,
            p.plan_season     AS planSeason,
            d.crop_id         AS cropId,
            c.crop_name       AS cropName,
            COUNT(DISTINCT d.plot_id) AS plotCount,
            MIN(d.earliest_harvestdate) AS planEarliest,
            MAX(d.last_harvestdate)     AS planLatest,
            MIN(d.begin_harvestdate)    AS actualBegin,
            MAX(d.end_harvestdate)      AS actualEnd,
            COALESCE(SUM(d.expected_yield), 0) AS expectedYield,
            COALESCE(SUM(d.actual_yield),   0) AS actualYield,
            SUM(CASE WHEN d.is_pick = 1 THEN 1 ELSE 0 END) AS activityPlotCount
          FROM t_plant_plant_details d
          INNER JOIN t_plant_plant_plan p ON p.id = d.plant_id AND p.del_flag='0'
          LEFT  JOIN t_plant_crop_info  c ON c.id = d.crop_id  AND c.del_flag='0'
         WHERE d.del_flag='0'
           AND d.tenant_id = #{tenantId}
           <if test='planYear != null'>      AND p.plan_year = #{planYear}     </if>
           <if test='planSeason != null and planSeason != ""'>
                                             AND p.plan_season = #{planSeason} </if>
           <if test='cropId != null'>        AND d.crop_id = #{cropId}         </if>
           <if test='harvestStatus != null and harvestStatus != ""'>
                                             AND d.harvest_status = #{harvestStatus} </if>
         GROUP BY d.plant_id, p.plan_no, p.plan_year, p.plan_season, d.crop_id, c.crop_name
         ORDER BY p.plan_year DESC, d.plant_id DESC, d.crop_id ASC
        </script>
        """)
    List<PickPlanGroupVo> aggregateByPlanCrop(
        @Param("tenantId") String tenantId,
        @Param("planYear") Integer planYear,
        @Param("planSeason") String planSeason,
        @Param("cropId") Long cropId,
        @Param("harvestStatus") String harvestStatus);
}
