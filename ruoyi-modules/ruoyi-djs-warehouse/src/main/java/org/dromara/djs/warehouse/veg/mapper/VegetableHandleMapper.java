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
     * <p><b>以 {@code t_warehouse_planting_record} 为驱动表</b>（"待处理菜品"权威源，采摘完成由
     * CROSS-FLOW-002 listener 自动建 {@code handle_status='pending'} 待办），左联 {@code vegetable_handle}
     * 取累计重量（待办尚未录重量 → 汇总行不存在 → 重量 COALESCE 兜 0）。<b>不可</b>以 vegetable_handle 为
     * 驱动表——汇总行首次录重量才懒建，纯待办（pending 无汇总）作物会整条丢失，永远进不了列表去录第一笔重量
     * （鸡生蛋）。</p>
     *
     * <p>仅展示「采摘完成 → 地块处理结束之前」的作物：当某作物所有 planting_record 都已
     * {@code handle_status='done'}（全部地块处理完成）时整条作物从列表消失（HAVING 过滤，至少 1 条非 done 才展示）。
     * 重量 SUM 含已 done 批次（口径不变，累计展示）。</p>
     *
     * <p>菜名取 {@code t_warehouse_planting_record.crop_name} 冗余快照（与 selectPendingList /
     * fillPlantingNames 同源）；缩略图左联 t_plant_crop_info 尽力取（取不到走前端默认图兜底）。
     * 按最近 {@code data_date} 倒序，让"刚采摘完"的菜品排前面。</p>
     */
    @Select("SELECT p.crop_id AS cropId, MAX(p.crop_name) AS cropName, MAX(c.image_oss_id) AS imageOssId,"
        + "       COALESCE(SUM(h.picked_weight),0) AS harvestWeight,"
        + "       COALESCE(SUM(h.feed_weight),0) AS feedWeight,"
        + "       COALESCE(SUM(h.handled_weight),0) AS handledWeight,"
        + "       COALESCE(SUM(h.loss_weight),0) AS lossWeight,"
        + "       COALESCE(SUM(p.expect_yield),0) AS expectedYield"
        + "  FROM t_warehouse_planting_record p"
        + "  LEFT JOIN t_warehouse_vegetable_handle h ON h.planting_record_id=p.id AND h.del_flag='0'"
        + "  LEFT JOIN t_plant_crop_info c ON c.id=p.crop_id AND c.del_flag='0'"
        + " WHERE p.tenant_id='1001' AND p.del_flag='0'"
        + " GROUP BY p.crop_id"
        + " HAVING SUM(CASE WHEN p.handle_status <> 'done' THEN 1 ELSE 0 END) > 0"
        + " ORDER BY MAX(p.data_date) DESC")
    List<VegCropVo> selectCropAggList();

}
