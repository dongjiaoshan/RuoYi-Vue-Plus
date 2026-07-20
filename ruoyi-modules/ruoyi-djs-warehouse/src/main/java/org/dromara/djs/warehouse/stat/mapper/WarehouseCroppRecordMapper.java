package org.dromara.djs.warehouse.stat.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.stat.domain.WarehouseCroppRecord;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseCroppRecordVo;

import java.util.List;

/**
 * 作物日数据记录 Mapper（WMS-STAT-001，表 {@code t_warehouse_cropp_record}）。
 *
 * <p>列表查询走自定义 @Select JOIN {@code t_plant_crop_info} 取 crop_code/crop_name/image，
 * 雪花 crop_id CAST AS CHAR 防精度丢失，WHERE 显式带 {@code tenant_id}（聚合不保证拦截器注入）。</p>
 *
 * @author djs
 * @since WMS-STAT-001
 */
public interface WarehouseCroppRecordMapper extends BaseMapperPlus<WarehouseCroppRecord, WarehouseCroppRecordVo> {

    /**
     * 作物日报表列表（JOIN crop_info 取展示列），按日期范围过滤，日期倒序 + crop_id 升序。
     *
     * @param tenantId 租户（V1 固定 '1001'）
     * @param dateFrom 起始日期（含，可空，yyyy-MM-dd）
     * @param dateTo   截止日期（含，可空，yyyy-MM-dd）
     * @return 作物日报表行（含作物编码/名称/图）
     */
    @Select("""
        <script>
        SELECT r.stat_date AS statDate,
               CAST(r.crop_id AS CHAR) AS cropId,
               c.crop_code AS cropCode,
               c.crop_name AS cropName,
               COALESCE(c.image_oss_id, c.crop_image_preview) AS imageOssId,
               r.pick_weight AS pickWeight,
               r.feed_weight AS feedWeight,
               r.veg_handle_rate AS vegHandleRate,
               r.receive_weight AS receiveWeight,
               r.send_platform_weight AS sendPlatformWeight,
               r.transport_loss_rate AS transportLossRate,
               r.out_weight AS outWeight,
               r.net_veg_loss_rate AS netVegLossRate
        FROM t_warehouse_cropp_record r
        LEFT JOIN t_plant_crop_info c ON c.id = r.crop_id AND c.del_flag = '0'
        WHERE r.del_flag = '0' AND r.tenant_id = #{tenantId}
        <if test="dateFrom != null and dateFrom != ''"> AND r.stat_date &gt;= #{dateFrom} </if>
        <if test="dateTo != null and dateTo != ''"> AND r.stat_date &lt;= #{dateTo} </if>
        ORDER BY r.stat_date DESC, r.crop_id ASC
        </script>
        """)
    List<WarehouseCroppRecordVo> selectCroppList(@Param("tenantId") String tenantId,
                                                 @Param("dateFrom") String dateFrom,
                                                 @Param("dateTo") String dateTo);
}
