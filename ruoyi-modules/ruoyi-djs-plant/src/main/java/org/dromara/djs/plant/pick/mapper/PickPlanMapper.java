package org.dromara.djs.plant.pick.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.pick.domain.vo.PickPlanGroupVo;

import java.time.LocalDate;
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
     *   <li>{@code totalAcreage} = SUM(plot_area)（计划种植亩数，亩）</li>
     *   <li>{@code currentPlantedArea} = SUM(plot_area WHERE begin_actualdate IS NOT NULL)（当前已种植亩数，亩）</li>
     *   <li>{@code expectedYield} = SUM(expected_yield)（预计产量）</li>
     *   <li>{@code actualYield} = 当年 SUM(actual_yield)（当年已采摘量，按 earliest_harvestdate 年份过滤）</li>
     *   <li>{@code disasterLoss} = 灾害农事记录 t_plant_farm_records(farm_type='disaster') 按作物 SUM(loss_yield)
     *       （预计灾害损失量，直接取灾害源头，不依赖 plant_details.loss_yield 累加副作用）</li>
     *   <li>{@code netExpectedYield} = max(0, expectedYield − disasterLoss)（预计净产量，row185 灾害扣减后钳 0）</li>
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
     * @param cropName      可选：作物名称模糊过滤
     * @param harvestStatus 可选：details.harvest_status 过滤
     * @param beginEarliest 可选：最早开始时间范围起（含，过滤 MIN(earliest_harvestdate)）
     * @param endEarliest   可选：最早开始时间范围止（含，过滤 MIN(earliest_harvestdate)）
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
            COALESCE(SUM(CASE WHEN d.begin_actualdate IS NOT NULL THEN d.plot_area ELSE 0 END), 0) AS currentPlantedArea,
            COALESCE(SUM(d.expected_yield), 0) AS expectedYield,
            COALESCE(SUM(CASE WHEN YEAR(d.earliest_harvestdate) = #{currentYear} THEN d.actual_yield ELSE 0 END), 0) AS actualYield,
            COALESCE(MAX(dl.disasterLoss), 0) AS disasterLoss,
            -- row185：预计净产量 = max(0, 预计产量 − 预计灾害损失量)，钳 0 防灾害>预计时出负
            GREATEST(COALESCE(SUM(d.expected_yield), 0) - COALESCE(MAX(dl.disasterLoss), 0), 0) AS netExpectedYield,
            COUNT(DISTINCT CASE WHEN YEAR(d.earliest_harvestdate) = #{currentYear} THEN d.plot_id END) AS plotTotalCount,
            SUM(CASE WHEN d.is_pick = 1 THEN 1 ELSE 0 END) AS activityPlotCount
          FROM t_plant_plant_details d
          LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.del_flag = '0'
          LEFT JOIN (
            -- 预计灾害损失量：直接取灾害农事记录（t_plant_farm_records farm_type='disaster'）
            -- 按作物聚合的 SUM(loss_yield)，不依赖 plant_details.loss_yield 累加副作用
            -- （历史/导入数据未触发累加时该字段恒 0，故改取灾害源头）。
            SELECT fr.crop_id AS crop_id, SUM(fr.loss_yield) AS disasterLoss
              FROM t_plant_farm_records fr
             WHERE fr.del_flag = '0'
               AND fr.tenant_id = #{tenantId}
               AND fr.farm_type = 'disaster'
               AND fr.crop_id IS NOT NULL
             GROUP BY fr.crop_id
          ) dl ON dl.crop_id = d.crop_id
         WHERE d.del_flag = '0'
           AND d.tenant_id = #{tenantId}
           <if test='cropId != null'>        AND d.crop_id = #{cropId}                </if>
           <if test='cropName != null and cropName != ""'>
                                             AND c.crop_name LIKE CONCAT('%', #{cropName}, '%') </if>
           <if test='harvestStatus != null and harvestStatus != ""'>
                                             AND d.harvest_status = #{harvestStatus}   </if>
         GROUP BY d.crop_id
         <trim prefix="HAVING" prefixOverrides="AND">
            <if test='beginEarliest != null'> AND MIN(d.earliest_harvestdate) &gt;= #{beginEarliest} </if>
            <if test='endEarliest != null'>   AND MIN(d.earliest_harvestdate) &lt;= #{endEarliest}   </if>
         </trim>
         ORDER BY cropName ASC, d.crop_id ASC
        </script>
        """)
    List<PickPlanGroupVo> aggregateByCrop(
        @Param("tenantId") String tenantId,
        @Param("currentYear") int currentYear,
        @Param("cropId") Long cropId,
        @Param("cropName") String cropName,
        @Param("harvestStatus") String harvestStatus,
        @Param("beginEarliest") LocalDate beginEarliest,
        @Param("endEarliest") LocalDate endEarliest);
}
