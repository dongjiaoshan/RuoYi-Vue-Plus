package org.dromara.djs.warehouse.veg.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.veg.domain.VegetableHandle;
import org.dromara.djs.warehouse.veg.domain.vo.VegCropVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegetableHandleVo;

import java.util.List;

/**
 * 毛菜处理汇总 Mapper（WMS-VEG-001）。
 *
 * @author djs
 * @since WMS-VEG-001
 */
public interface VegetableHandleMapper extends BaseMapperPlus<VegetableHandle, VegetableHandleVo> {

    /**
     * 按上游 planting_record_id 查关联的 vegetable_handle 行（每个 planting_record 最多对应 1 行汇总）。
     *
     * <p>service 进入 submitHandleRecord 后：找到 → 用此行聚合；找不到 → 首次采收 INSERT 新行。
     * MP 在事务内调用：底层 MySQL InnoDB 在 {@code REPEATABLE_READ} 下对索引行加 NEXT-KEY LOCK，
     * 已具备防止重复 INSERT 的能力。</p>
     */
    @Select("SELECT * FROM t_warehouse_vegetable_handle "
        + " WHERE planting_record_id = #{plantingRecordId} AND del_flag = '0' LIMIT 1")
    VegetableHandle selectByPlantingRecordId(@Param("plantingRecordId") Long plantingRecordId);

    /**
     * mp 毛菜处理菜品列表：按 crop_id 聚合 4 个重量（采摘 / 饲喂 / 处理后 / 损耗）。
     *
     * <p>左联 t_plant_crop_info 取菜名 + 缩略图（纯 SQL 跨表，不引 plant 模块依赖）。
     * 按最近采摘时间倒序，让"刚开工"的菜品排前面。</p>
     */
    @Select("SELECT vh.crop_id AS cropId, c.crop_name AS cropName, c.crop_image_preview AS thumbUrl,"
        + "       COALESCE(SUM(vh.picked_weight),0) AS harvestWeight,"
        + "       COALESCE(SUM(vh.feed_weight),0) AS feedWeight,"
        + "       COALESCE(SUM(vh.handled_weight),0) AS handledWeight,"
        + "       COALESCE(SUM(vh.loss_weight),0) AS lossWeight"
        + "  FROM t_warehouse_vegetable_handle vh"
        + "  LEFT JOIN t_plant_crop_info c ON c.id=vh.crop_id AND c.del_flag='0'"
        + " WHERE vh.tenant_id='1001' AND vh.del_flag='0'"
        + " GROUP BY vh.crop_id, c.crop_name, c.crop_image_preview"
        + " ORDER BY MAX(vh.pick_start_time) DESC")
    List<VegCropVo> selectCropAggList();

}
