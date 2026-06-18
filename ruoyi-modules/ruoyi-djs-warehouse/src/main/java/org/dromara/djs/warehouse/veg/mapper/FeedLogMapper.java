package org.dromara.djs.warehouse.veg.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.veg.domain.FeedLog;
import org.dromara.djs.warehouse.veg.domain.vo.FeedDailyStatVo;

import java.util.Date;
import java.util.List;

/**
 * 饲料饲喂台账 Mapper（WMS-VEG-FEED-LOG-001）。
 *
 * @author djs
 * @since WMS-VEG-FEED-LOG-001
 */
public interface FeedLogMapper extends BaseMapperPlus<FeedLog, FeedLog> {

    /**
     * 按自然日 × 作物品类统计饲喂重量（spec 步8「每日统计饲喂作物品类及对应重量」）。
     *
     * <p>{@code SUM(feed_weight) GROUP BY feed_date, crop_id}，按日期倒序、当日重量倒序。
     * crop_id 显式 CAST 为 CHAR 以 String 返回前端，避免雪花精度丢失。
     * 自定义 @Select 含聚合，WHERE 显式带 tenant_id（多租户拦截器对聚合不保证注入）。</p>
     *
     * @param tenantId 租户（V1 固定 '1001'）
     * @param startDate 起始日期（含，可空）
     * @param endDate 截止日期（含，可空）
     * @return 每日每作物的饲喂重量合计
     */
    @Select("""
        <script>
        SELECT feed_date AS feedDate,
               CAST(crop_id AS CHAR) AS cropId,
               MAX(crop_name) AS cropName,
               COALESCE(SUM(feed_weight), 0) AS totalWeight
        FROM t_warehouse_feed_log
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
        <if test="startDate != null"> AND feed_date &gt;= #{startDate} </if>
        <if test="endDate != null"> AND feed_date &lt;= #{endDate} </if>
        GROUP BY feed_date, crop_id
        ORDER BY feed_date DESC, totalWeight DESC
        </script>
        """)
    List<FeedDailyStatVo> selectDailyStat(@Param("tenantId") String tenantId,
                                          @Param("startDate") Date startDate,
                                          @Param("endDate") Date endDate);

}
