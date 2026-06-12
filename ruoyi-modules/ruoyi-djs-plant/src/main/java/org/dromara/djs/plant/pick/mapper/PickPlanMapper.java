package org.dromara.djs.plant.pick.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.pick.domain.vo.PickPlanGroupVo;

import java.util.List;

/**
 * 采摘计划聚合 Mapper。
 *
 * <p>按作物聚合 {@code t_plant_plant_details}（对齐原型「采摘计划列表」按作物维度），
 * 配合 {@link org.dromara.djs.plant.plan.mapper.PlantDetailsMapper} 单行操作。</p>
 *
 * @author djs
 */
public interface PickPlanMapper {

    /**
     * 按作物聚合采摘计划列表（对齐原型按作物维度）。
     *
     * <p>聚合字段：</p>
     * <ul>
     *   <li>{@code plotCount} = COUNT(DISTINCT plot_id)</li>
     *   <li>{@code totalAcreage} = SUM(plot_area)（当前种植亩数，亩）</li>
     *   <li>{@code expectedYield} = SUM(expected_yield)</li>
     *   <li>{@code actualYield} = 当年 SUM(actual_yield)（当年已采摘量，按 earliest_harvestdate 年份过滤）</li>
     *   <li>{@code disasterLoss} = SUM(loss_yield)（预计灾害损失量）</li>
     *   <li>{@code plotTotalCount} = 当年 COUNT(DISTINCT plot_id)（当年种植地块总数）</li>
     *   <li>{@code activityPlotCount} = SUM(is_pick=1)</li>
     * </ul>
     *
     * <p>{@code imageOssId} 透传作物主图 L1，由 Service enrich 转 public URL 写 {@code cropImageUrl}。
     * {@code demandQty}（预计需求量）暂无来源，由 Service 置 NULL（前端 - 兜底）。</p>
     *
     * @param tenantId      租户编号
     * @param currentYear   当年（用于「当年已采摘量 / 当年种植地块总数」过滤）
     * @param cropId        可选：作物 id 过滤
     * @param harvestStatus 可选：details.harvest_status 过滤
     * @return 按作物聚合行（按作物名 ASC、作物 id ASC 排序）
     */
    @Select("""
        <script>
        SELECT
            d.crop_id        AS cropId,
            MAX(c.crop_name) AS cropName,
            MAX(c.image_oss_id) AS imageOssId,
            COUNT(DISTINCT d.plot_id) AS plotCount,
            MIN(d.earliest_harvestdate) AS planEarliest,
            MAX(d.last_harvestdate)     AS planLatest,
            COALESCE(SUM(d.plot_area),      0) AS totalAcreage,
            COALESCE(SUM(d.expected_yield), 0) AS expectedYield,
            COALESCE(SUM(CASE WHEN YEAR(d.earliest_harvestdate) = #{currentYear} THEN d.actual_yield ELSE 0 END), 0) AS actualYield,
            COALESCE(SUM(d.loss_yield),     0) AS disasterLoss,
            COUNT(DISTINCT CASE WHEN YEAR(d.earliest_harvestdate) = #{currentYear} THEN d.plot_id END) AS plotTotalCount,
            SUM(CASE WHEN d.is_pick = 1 THEN 1 ELSE 0 END) AS activityPlotCount
          FROM t_plant_plant_details d
          LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.del_flag = '0'
         WHERE d.del_flag = '0'
           AND d.tenant_id = #{tenantId}
           <if test='cropId != null'>        AND d.crop_id = #{cropId}                </if>
           <if test='harvestStatus != null and harvestStatus != ""'>
                                             AND d.harvest_status = #{harvestStatus}   </if>
         GROUP BY d.crop_id
         ORDER BY cropName ASC, d.crop_id ASC
        </script>
        """)
    List<PickPlanGroupVo> aggregateByCrop(
        @Param("tenantId") String tenantId,
        @Param("currentYear") int currentYear,
        @Param("cropId") Long cropId,
        @Param("harvestStatus") String harvestStatus);
}
