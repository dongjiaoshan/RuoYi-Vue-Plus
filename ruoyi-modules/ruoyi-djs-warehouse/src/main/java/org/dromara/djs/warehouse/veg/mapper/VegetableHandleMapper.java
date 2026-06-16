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
     * <p>仅展示「采摘开始 → 地块处理结束之前」的作物：当某作物所有汇总行都已 {@code handle_status='done'}
     * （全部地块处理完成）时整条作物从列表消失（HAVING 过滤，至少要有 1 条非 done 才展示）。</p>
     *
     * <p>菜名取同模块 {@code t_warehouse_planting_record.crop_name} 冗余快照（与 selectPendingList /
     * fillPlantingNames 同源）——vegetable_handle.crop_id 与 t_plant_crop_info.id 不在同一注册表、跨表
     * 关联取不到名；缩略图仍左联 t_plant_crop_info 尽力取（取不到走前端默认图兜底）。
     * 按最近采摘时间倒序，让"刚开工"的菜品排前面。</p>
     */
    @Select("SELECT vh.crop_id AS cropId, MAX(p.crop_name) AS cropName, MAX(c.image_oss_id) AS imageOssId,"
        + "       COALESCE(SUM(vh.picked_weight),0) AS harvestWeight,"
        + "       COALESCE(SUM(vh.feed_weight),0) AS feedWeight,"
        + "       COALESCE(SUM(vh.handled_weight),0) AS handledWeight,"
        + "       COALESCE(SUM(vh.loss_weight),0) AS lossWeight"
        + "  FROM t_warehouse_vegetable_handle vh"
        + "  LEFT JOIN t_warehouse_planting_record p ON p.id=vh.planting_record_id AND p.del_flag='0'"
        + "  LEFT JOIN t_plant_crop_info c ON c.id=vh.crop_id AND c.del_flag='0'"
        + " WHERE vh.tenant_id='1001' AND vh.del_flag='0'"
        + " GROUP BY vh.crop_id"
        + " HAVING SUM(CASE WHEN vh.handle_status <> 'done' THEN 1 ELSE 0 END) > 0"
        + " ORDER BY MAX(vh.pick_start_time) DESC")
    List<VegCropVo> selectCropAggList();

}
