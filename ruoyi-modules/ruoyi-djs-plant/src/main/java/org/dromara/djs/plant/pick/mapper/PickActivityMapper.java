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
     * @param tenantId  租户编号
     * @param cropName  可选：作物名称模糊过滤
     * @param beginDate 可选：活动日期范围起（含）
     * @param endDate   可选：活动日期范围止（含）
     * @return 聚合行列表（按活动日期 DESC、作物 id ASC 排序）
     */
    @Select("""
        <script>
        SELECT
            t.crop_id        AS cropId,
            c.crop_name      AS cropName,
            t.activity_date  AS activityDate,
            COALESCE(p.plot_count, 0)     AS plotCount,
            t.today_pick_weight           AS todayPickWeight,
            COALESCE(p.expected_yield, 0) AS expectedYield,
            SUM(t.today_pick_weight) OVER (
                PARTITION BY t.crop_id ORDER BY t.activity_date
                ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
            ) AS cumulativePickWeight,
            t.sale_weight      AS saleWeight,
            t.veg_fresh_weight AS vegFreshWeight,
            t.platform_weight  AS platformWeight,
            t.loss_weight      AS lossWeight,
            t.feed_weight      AS feedWeight
          FROM (
            -- DENGBO-R4：以采摘活动 t_plant_plant_activity 按 (crop_id, activity_date) 为驱动行，
            -- 今日采摘重量 + 去向 5 列同源聚合（天然对齐，5 列之和 = 今日采摘重量）。
            -- 销售口径 = 显式 sale + 历史 NULL 去向（旧采摘录入无去向，视作销售）。
            SELECT
                pa.crop_id       AS crop_id,
                pa.activity_date AS activity_date,
                SUM(COALESCE(pa.pick_weight, pa.daily_weight)) AS today_pick_weight,
                COALESCE(SUM(CASE WHEN pa.pick_dest = 'sale' OR pa.pick_dest IS NULL THEN COALESCE(pa.pick_weight, pa.daily_weight) ELSE 0 END), 0) AS sale_weight,
                COALESCE(SUM(CASE WHEN pa.pick_dest = 'veg_fresh' THEN COALESCE(pa.pick_weight, pa.daily_weight) ELSE 0 END), 0) AS veg_fresh_weight,
                COALESCE(SUM(CASE WHEN pa.pick_dest = 'platform'  THEN COALESCE(pa.pick_weight, pa.daily_weight) ELSE 0 END), 0) AS platform_weight,
                COALESCE(SUM(CASE WHEN pa.pick_dest = 'loss'      THEN COALESCE(pa.pick_weight, pa.daily_weight) ELSE 0 END), 0) AS loss_weight,
                COALESCE(SUM(CASE WHEN pa.pick_dest = 'feed'      THEN COALESCE(pa.pick_weight, pa.daily_weight) ELSE 0 END), 0) AS feed_weight
              FROM t_plant_plant_activity pa
             WHERE pa.del_flag = '0'
               AND pa.tenant_id = #{tenantId}
             GROUP BY pa.crop_id, pa.activity_date
          ) t
          LEFT JOIN t_plant_crop_info c ON c.id = t.crop_id AND c.del_flag = '0'
          LEFT JOIN (
            -- 作物级（非按日）：活动地块数 + 预计总产量（取该作物全部地块，逐日行复用）
            SELECT d.crop_id AS crop_id,
                   COUNT(DISTINCT d.plot_id)          AS plot_count,
                   COALESCE(SUM(d.expected_yield), 0) AS expected_yield
              FROM t_plant_plant_details d
             WHERE d.del_flag = '0' AND d.tenant_id = #{tenantId}
             GROUP BY d.crop_id
          ) p ON p.crop_id = t.crop_id
         <where>
            <if test='cropName != null and cropName != ""'> AND c.crop_name LIKE CONCAT('%', #{cropName}, '%') </if>
            <if test='beginDate != null'>        AND t.activity_date &gt;= #{beginDate}  </if>
            <if test='endDate != null'>          AND t.activity_date &lt;= #{endDate}    </if>
         </where>
         ORDER BY t.activity_date DESC, t.crop_id ASC
        </script>
        """)
    List<PickActivityVo> selectDailyReport(
        @Param("tenantId") String tenantId,
        @Param("cropName") String cropName,
        @Param("beginDate") LocalDate beginDate,
        @Param("endDate") LocalDate endDate);
}
