package org.dromara.djs.warehouse.veg.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.veg.domain.PlantingRecord;
import org.dromara.djs.warehouse.veg.domain.vo.PendingPlantingRecordVo;

import java.util.List;

/**
 * 仓库视角种植记录 Mapper（WMS-VEG-001 minimal 只读 + handle_status 推进）。
 *
 * @author djs
 * @since WMS-VEG-001
 */
public interface PlantingRecordMapper extends BaseMapperPlus<PlantingRecord, PlantingRecord> {

    /**
     * mp 待处理列表：左联 vegetable_handle 取已累计字段（如未开工则 NULL）。
     *
     * <p>过滤 handle_status != 'done' 且 del_flag='0'，按 data_date DESC 限制 50 条。</p>
     */
    @Select("SELECT p.id AS plantingRecordId, p.plot_id AS plotId, p.plot_name AS plotName,"
        + "       p.crop_id AS cropId, p.crop_name AS cropName,"
        + "       p.harvest_date AS harvestDate, p.harvest_weight AS harvestWeight,"
        + "       p.handle_status AS handleStatus, p.data_date AS dataDate,"
        + "       h.id AS handleId, h.handled_weight AS handledWeight,"
        + "       h.feed_weight AS feedWeight, h.stock_in_weight AS stockInWeight "
        + "  FROM t_warehouse_planting_record p "
        + "  LEFT JOIN t_warehouse_vegetable_handle h "
        + "         ON h.planting_record_id = p.id AND h.del_flag='0'"
        + " WHERE p.del_flag='0' AND p.handle_status <> 'done'"
        + " ORDER BY p.data_date DESC, p.id DESC LIMIT 50")
    List<PendingPlantingRecordVo> selectPendingList();

    /**
     * 推进 planting_record.handle_status（pending → processing / processing → done）。
     *
     * <p>乐观锁：WHERE handle_status = #{fromStatus}。</p>
     */
    @Update("UPDATE t_warehouse_planting_record"
        + "    SET handle_status = #{toStatus}, update_by = #{userId}, update_time = NOW()"
        + "  WHERE id = #{id} AND handle_status = #{fromStatus} AND del_flag='0'")
    int advanceHandleStatus(@Param("id") Long id,
                            @Param("fromStatus") String fromStatus,
                            @Param("toStatus") String toStatus,
                            @Param("userId") Long userId);

}
