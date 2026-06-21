package org.dromara.djs.warehouse.veg.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.veg.domain.PlantingRecord;
import org.dromara.djs.warehouse.veg.domain.vo.PendingPlantingRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegPlotDetailVo;

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

    /**
     * mp 某菜品下地块明细：种植记录左联 vegetable_handle（取累计入/出库 + 称重/处理状态）+ 地块（取编号/面积）。
     *
     * <p>weighStatus / processStatus 由汇总行 is_weighed / is_finish 派生（=1 → done，否则 pending）。
     * 纯 SQL 跨表 JOIN t_plant_plot_info，不引 plant 模块依赖。按 data_date DESC 排序。</p>
     */
    @Select("SELECT p.id AS plantingRecordId, p.plot_id AS plotId, pl.plot_code AS plotCode, pl.plot_area AS plotArea,"
        + "       COALESCE(h.picked_weight,0) AS harvestWeight,"
        + "       COALESCE(h.handled_weight,0) AS handledWeight,"
        + "       (COALESCE(h.picked_weight,0) - COALESCE(h.handled_weight,0)) AS remainWeight,"
        + "       COALESCE(p.expect_yield,0) AS expectYield,"
        + "       CASE WHEN c.related_product IS NOT NULL THEN 1 ELSE 0 END AS hasRelatedProduct,"
        + "       CASE WHEN h.is_weighed=1 THEN 'done' ELSE 'pending' END AS weighStatus,"
        + "       CASE WHEN h.is_finish=1 THEN 'done' ELSE 'pending' END AS processStatus,"
        + "       h.id AS handleId"
        + "  FROM t_warehouse_planting_record p"
        + "  LEFT JOIN t_warehouse_vegetable_handle h ON h.planting_record_id=p.id AND h.del_flag='0'"
        + "  LEFT JOIN t_plant_plot_info pl ON pl.id=p.plot_id AND pl.del_flag='0'"
        + "  LEFT JOIN t_plant_crop_info c ON c.id=p.crop_id AND c.del_flag='0'"
        + " WHERE p.tenant_id='1001' AND p.del_flag='0' AND p.crop_id=#{cropId}"
        + " ORDER BY p.data_date DESC, p.id DESC")
    List<VegPlotDetailVo> selectPlotDetailByCrop(@Param("cropId") Long cropId);

}
