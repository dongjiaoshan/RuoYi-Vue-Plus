package org.dromara.djs.plant.pick.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.pick.domain.vo.PickActivityVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 采摘活动只读聚合报表 Mapper。
 *
 * <p>对 {@code t_plant_plant_details} 按「作物 + 活动(采摘)日期」聚合，
 * 逐行一条「某作物某天」的采摘汇总。仅查询，无写操作。</p>
 *
 * @author djs
 */
public interface PickActivityMapper {

    /**
     * 每日采摘聚合报表（GROUP BY crop_id, end_harvestdate）。
     *
     * <p>每行字段：</p>
     * <ul>
     *   <li>{@code plotCount} = 当日 COUNT(DISTINCT plot_id)</li>
     *   <li>{@code todayPickWeight} = 当日 SUM(actual_yield)</li>
     *   <li>{@code expectedYield} = 当日 SUM(expected_yield)</li>
     *   <li>{@code cumulativePickWeight} = 同作物截至该日累计 SUM(actual_yield)（窗口函数）</li>
     * </ul>
     *
     * <p>口径：{@code end_harvestdate IS NOT NULL}（已采摘完成才计入活动日期）、
     * {@code del_flag='0'}、租户过滤。</p>
     *
     * @param tenantId     租户编号
     * @param cropId       可选：作物 id 过滤
     * @param activityDate 可选：活动日期（单日）过滤
     * @return 聚合行列表（按活动日期 DESC、作物 id ASC 排序）
     */
    @Select("""
        <script>
        SELECT
            t.crop_id        AS cropId,
            t.crop_name      AS cropName,
            t.activity_date  AS activityDate,
            t.plot_count     AS plotCount,
            t.today_pick_weight AS todayPickWeight,
            t.expected_yield AS expectedYield,
            SUM(t.today_pick_weight) OVER (
                PARTITION BY t.crop_id ORDER BY t.activity_date
                ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
            ) AS cumulativePickWeight
          FROM (
            SELECT
                d.crop_id          AS crop_id,
                MAX(c.crop_name)   AS crop_name,
                d.end_harvestdate  AS activity_date,
                COUNT(DISTINCT d.plot_id)          AS plot_count,
                COALESCE(SUM(d.actual_yield), 0)   AS today_pick_weight,
                COALESCE(SUM(d.expected_yield), 0) AS expected_yield
              FROM t_plant_plant_details d
              LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.del_flag = '0'
             WHERE d.del_flag = '0'
               AND d.tenant_id = #{tenantId}
               AND d.end_harvestdate IS NOT NULL
               <if test='cropId != null'>       AND d.crop_id = #{cropId}              </if>
             GROUP BY d.crop_id, d.end_harvestdate
          ) t
         <where>
            <if test='activityDate != null'>     t.activity_date = #{activityDate}     </if>
         </where>
         ORDER BY t.activity_date DESC, t.crop_id ASC
        </script>
        """)
    List<PickActivityVo> selectDailyReport(
        @Param("tenantId") String tenantId,
        @Param("cropId") Long cropId,
        @Param("activityDate") LocalDate activityDate);
}
