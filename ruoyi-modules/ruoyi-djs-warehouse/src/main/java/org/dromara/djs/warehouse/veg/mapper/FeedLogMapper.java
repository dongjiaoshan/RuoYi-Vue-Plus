package org.dromara.djs.warehouse.veg.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.veg.domain.FeedLog;
import org.dromara.djs.warehouse.veg.domain.bo.FeedRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.FeedDailyStatVo;
import org.dromara.djs.warehouse.veg.domain.vo.FeedDailyVo;
import org.dromara.djs.warehouse.veg.domain.vo.FeedLogVo;
import org.dromara.djs.warehouse.veg.domain.vo.FeedRecordVo;

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
        SELECT DATE(feed_date) AS feedDate,
               CAST(crop_id AS CHAR) AS cropId,
               MAX(crop_name) AS cropName,
               COALESCE(SUM(feed_weight), 0) AS totalWeight
        FROM t_warehouse_feed_log
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
        <if test="startDate != null"> AND feed_date &gt;= #{startDate} </if>
        <if test="endDate != null"> AND feed_date &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY) </if>
        GROUP BY DATE(feed_date), crop_id
        ORDER BY DATE(feed_date) DESC, totalWeight DESC
        </script>
        """)
    List<FeedDailyStatVo> selectDailyStat(@Param("tenantId") String tenantId,
                                          @Param("startDate") Date startDate,
                                          @Param("endDate") Date endDate);

    /**
     * 按产品取饲喂明细（库存查询详情「饲料饲喂记录」tab，行57；仅果蔬原材料产品显示该 tab）。
     *
     * <p>LEFT JOIN {@code t_warehouse_location_info} 回填库位名（饲喂位置）；自定义 @Select 的
     * WHERE 显式带 {@code tenant_id}（拦截器对自定义 SQL 不保证注入）。按饲喂时间倒序。</p>
     *
     * @param tenantId  租户（V1 固定 '1001'）
     * @param productId 产品 ID（FK → product_info.id）
     * @param dateFrom  起始时间（含，可空）
     * @param dateTo    截止时间（含，可空）
     * @return 该产品饲喂明细（按饲喂时间倒序）
     */
    @Select("""
        <script>
        SELECT fl.id, fl.feed_date AS feedDate, fl.product_id AS productId,
               fl.location_id AS locationId,
               COALESCE(l.location_name, CASE fl.feed_type WHEN 'veg_handle' THEN '毛菜间' END) AS locationName,
               fl.feed_weight AS feedWeight, fl.operator_id AS operatorId, fl.remark
        FROM t_warehouse_feed_log fl
        LEFT JOIN t_warehouse_location_info l
          ON l.id = fl.location_id AND l.del_flag = '0'
        WHERE fl.del_flag = '0' AND fl.tenant_id = #{tenantId} AND fl.product_id = #{productId}
        <if test="dateFrom != null"> AND fl.feed_date &gt;= #{dateFrom} </if>
        <if test="dateTo != null"> AND fl.feed_date &lt; DATE_ADD(#{dateTo}, INTERVAL 1 DAY) </if>
        ORDER BY fl.feed_date DESC, fl.id DESC
        </script>
        """)
    List<FeedLogVo> selectByProduct(@Param("tenantId") String tenantId,
                                    @Param("productId") Long productId,
                                    @Param("dateFrom") Date dateFrom,
                                    @Param("dateTo") Date dateTo);

    /**
     * 有机饲喂记录分页列表（WMS-FEED-RECORD-001，仓库-admin 行21「有机饲喂记录」只读菜单）。
     *
     * <p>over {@code t_warehouse_feed_log} 全量，覆盖两类来源（毛菜处理间 veg_handle + 仓库领用 warehouse）。
     * 「作物名称」三级兜底：{@code feed_log.crop_name} 优先 → {@code crop.crop_name} → {@code product.product_name}
     * （仓库领用饲喂来源 crop_id 恒空、product_id 有值，取领用原材料名展示，row92）。作物图 ossId 仅作物来源有
     * （crop_image_url 首图 → image_oss_id 兜底，仓库来源留空占位）。LEFT JOIN {@code t_warehouse_location_info} 取饲喂位置名。
     * 自定义 @Select 含 JOIN，WHERE 显式带 {@code tenant_id}（拦截器对自定义 SQL 不保证注入）。按饲喂时间倒序。</p>
     *
     * @param page     分页对象
     * @param tenantId 租户（V1 固定 '1001'）
     * @param query    查询条件（作物名模糊 / 提供位置精确 / 日期范围）
     * @return 有机饲喂记录分页（按 feed_date 倒序）
     */
    @Select("""
        <script>
        SELECT fl.feed_date AS feedDate,
               fl.crop_id AS cropId,
               COALESCE(NULLIF(fl.crop_name, ''), c.crop_name, p.product_name) AS cropName,
               COALESCE(NULLIF(SUBSTRING_INDEX(c.crop_image_url, ',', 1), ''), c.image_oss_id) AS cropImageOssId,
               fl.feed_weight AS feedWeight,
               fl.feed_type AS feedType,
               fl.location_id AS locationId,
               l.location_name AS locationName,
               fl.operator_id AS operatorId
        FROM t_warehouse_feed_log fl
        LEFT JOIN t_plant_crop_info c
          ON c.id = fl.crop_id AND c.del_flag = '0'
        LEFT JOIN t_warehouse_product_info p
          ON p.id = fl.product_id AND p.del_flag = '0'
        LEFT JOIN t_warehouse_location_info l
          ON l.id = fl.location_id AND l.del_flag = '0'
        WHERE fl.del_flag = '0' AND fl.tenant_id = #{tenantId}
        <if test="query.cropName != null and query.cropName != ''">
            AND COALESCE(NULLIF(fl.crop_name, ''), c.crop_name, p.product_name) LIKE CONCAT('%', #{query.cropName}, '%')
        </if>
        <if test="query.feedType != null and query.feedType != ''">
            AND fl.feed_type = #{query.feedType}
        </if>
        <if test="query.dateFrom != null"> AND fl.feed_date &gt;= #{query.dateFrom} </if>
        <if test="query.dateTo != null"> AND fl.feed_date &lt; DATE_ADD(#{query.dateTo}, INTERVAL 1 DAY) </if>
        ORDER BY fl.feed_date DESC, fl.id DESC
        </script>
        """)
    IPage<FeedRecordVo> selectRecordPage(IPage<FeedRecordVo> page,
                                         @Param("tenantId") String tenantId,
                                         @Param("query") FeedRecordQuery query);

    /**
     * 有机饲喂记录不分页列表（导出用，行21 导出）。
     *
     * <p>同 {@link #selectRecordPage} 口径，仅不分页。自定义 @Select 含 JOIN，WHERE 显式带
     * {@code tenant_id}。按饲喂时间倒序。</p>
     *
     * @param tenantId 租户（V1 固定 '1001'）
     * @param query    查询条件（作物名模糊 / 提供位置精确 / 日期范围）
     * @return 有机饲喂记录全量列表（按 feed_date 倒序）
     */
    @Select("""
        <script>
        SELECT fl.feed_date AS feedDate,
               fl.crop_id AS cropId,
               COALESCE(NULLIF(fl.crop_name, ''), c.crop_name, p.product_name) AS cropName,
               COALESCE(NULLIF(SUBSTRING_INDEX(c.crop_image_url, ',', 1), ''), c.image_oss_id) AS cropImageOssId,
               fl.feed_weight AS feedWeight,
               fl.feed_type AS feedType,
               fl.location_id AS locationId,
               l.location_name AS locationName,
               fl.operator_id AS operatorId
        FROM t_warehouse_feed_log fl
        LEFT JOIN t_plant_crop_info c
          ON c.id = fl.crop_id AND c.del_flag = '0'
        LEFT JOIN t_warehouse_product_info p
          ON p.id = fl.product_id AND p.del_flag = '0'
        LEFT JOIN t_warehouse_location_info l
          ON l.id = fl.location_id AND l.del_flag = '0'
        WHERE fl.del_flag = '0' AND fl.tenant_id = #{tenantId}
        <if test="query.cropName != null and query.cropName != ''">
            AND COALESCE(NULLIF(fl.crop_name, ''), c.crop_name, p.product_name) LIKE CONCAT('%', #{query.cropName}, '%')
        </if>
        <if test="query.feedType != null and query.feedType != ''">
            AND fl.feed_type = #{query.feedType}
        </if>
        <if test="query.dateFrom != null"> AND fl.feed_date &gt;= #{query.dateFrom} </if>
        <if test="query.dateTo != null"> AND fl.feed_date &lt; DATE_ADD(#{query.dateTo}, INTERVAL 1 DAY) </if>
        ORDER BY fl.feed_date DESC, fl.id DESC
        </script>
        """)
    List<FeedRecordVo> selectRecordList(@Param("tenantId") String tenantId,
                                        @Param("query") FeedRecordQuery query);

    /**
     * 有机饲喂**按日汇总**分页（admin 行199 列表 / mp 行268 卡片共用）。
     *
     * <p>一天一行：{@code GROUP BY DATE(feed_date)} 把当日全部来源（毛菜间 + 仓库）的重量求和，
     * 再 LEFT JOIN 日确认表带出框数 / 确认人 / 确认时间（mp 卡片「处理日期」）。</p>
     *
     * <p>⚠️ 日确认表按 {@code feed_date} 关联，两边都是 DATE 粒度；feed_log 的 {@code feed_date} 是
     * DATETIME，故左联条件用 {@code DATE(fl.feed_date)} 而不是裸列，否则带时分秒的行永远联不上。
     * 这里把聚合放子查询、再与确认表联，避免 JOIN 后再聚合导致重量被确认表行数放大。</p>
     *
     * @param page     分页
     * @param tenantId 租户（V1 固定 '1001'）
     * @param query    查询条件（本汇总只用 dateFrom / dateTo，作物名与提供位置属明细维度不参与）
     * @return 每日一行的汇总（按日期倒序）
     */
    @Select("""
        <script>
        SELECT d.feedDate       AS feedDate,
               d.totalWeight    AS totalWeight,
               d.detailCount    AS detailCount,
               fc.box_count     AS boxCount,
               fc.confirm_user_id AS confirmUserId,
               fc.confirm_time  AS confirmTime
        FROM (
            SELECT DATE(fl.feed_date)  AS feedDate,
                   SUM(fl.feed_weight) AS totalWeight,
                   COUNT(*)            AS detailCount
            FROM t_warehouse_feed_log fl
            WHERE fl.del_flag = '0' AND fl.tenant_id = #{tenantId}
            <if test="query.dateFrom != null"> AND fl.feed_date &gt;= #{query.dateFrom} </if>
            <if test="query.dateTo != null"> AND fl.feed_date &lt; DATE_ADD(#{query.dateTo}, INTERVAL 1 DAY) </if>
            GROUP BY DATE(fl.feed_date)
        ) d
        LEFT JOIN t_warehouse_feed_daily_confirm fc
          ON fc.feed_date = d.feedDate AND fc.del_flag = '0' AND fc.tenant_id = #{tenantId}
        ORDER BY d.feedDate DESC
        </script>
        """)
    IPage<FeedDailyVo> selectDailyPage(IPage<FeedDailyVo> page,
                                       @Param("tenantId") String tenantId,
                                       @Param("query") FeedRecordQuery query);

}
