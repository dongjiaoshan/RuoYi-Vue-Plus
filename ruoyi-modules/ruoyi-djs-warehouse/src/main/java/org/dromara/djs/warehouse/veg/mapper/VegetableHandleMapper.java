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
     * fillPlantingNames 同源）；缩略图取「产品配置中原材料的图」——按 {@code crop.related_product}
     * 关联 {@code t_warehouse_product_info(attr=2 果蔬原料)} 取其 {@code image_oss_id}，作物自身图作兜底
     * （{@code COALESCE(prod.image_oss_id, c.image_oss_id)}）；都取不到走前端默认图兜底。
     * 按最近 {@code data_date} 倒序，让"刚采摘完"的菜品排前面。</p>
     *
     * <p><b>预计产量扣灾害损失</b>（毛菜处理预计产量 = 可处理地块预计产量之和 − 灾害损失）：左联灾害农事记录
     * {@code t_plant_farm_records(farm_type='disaster')} 按 {@code crop_id} 预聚合的 {@code SUM(loss_yield)}
     * （子查询先按作物聚合成 1 行再左联，避免与父表多 planting_record 行扇出重复计数；与 expectedYield 同口径按
     * 作物维度展示）。{@code expectedYield = SUM(plot_area*predicted_per) − disasterLoss}，单列另透
     * {@code disasterLoss} 供 mp 展示扣减明细。底层灾害损失已按 plot_id+date 存于 loss_yield，按作物聚合即
     * 该作物所辖可处理地块的灾害损失之和。</p>
     */
    @Select("SELECT p.crop_id AS cropId, MAX(p.crop_name) AS cropName,"
        + "       MAX(COALESCE(prod.image_oss_id, c.image_oss_id)) AS imageOssId,"
        + "       COALESCE(SUM(h.picked_weight),0) AS harvestWeight,"
        + "       COALESCE(SUM(h.feed_weight),0) AS feedWeight,"
        + "       COALESCE(SUM(h.handled_weight),0) AS handledWeight,"
        + "       COALESCE(SUM(h.stock_in_weight),0) AS stockInWeight,"
        + "       COALESCE(SUM(h.send_platform_weight),0) AS sendPlatformWeight,"
        + "       COALESCE(SUM(h.loss_weight),0) AS lossWeight,"
        + "       COALESCE(MAX(dl.disaster_loss),0) AS disasterLoss,"
        + "       COALESCE(SUM(pl.plot_area * c.predicted_per),0) - COALESCE(MAX(dl.disaster_loss),0) AS expectedYield,"
        // 待处理地块数：每个 planting_record = 一个待处理地块，处理未完成 = 汇总行 is_finish != 1
        // （汇总行不存在 → 尚未处理 → 计入待处理；与地块明细页 processStatus!='done' 口径一致）。
        + "       SUM(CASE WHEN COALESCE(h.is_finish,0) <> 1 THEN 1 ELSE 0 END) AS pendingPlotCount"
        + "  FROM t_warehouse_planting_record p"
        // vegetable_handle 先按 planting_record_id 预聚合成 1 行再左联，避免某 record 有多条 handle 时
        // 父表表达式 SUM(plot_area*predicted_per) 被扇出重复计数（expectedYield 虚高）。
        + "  LEFT JOIN (SELECT planting_record_id,"
        + "                    SUM(picked_weight) AS picked_weight, SUM(feed_weight) AS feed_weight,"
        + "                    SUM(handled_weight) AS handled_weight, SUM(loss_weight) AS loss_weight,"
        + "                    SUM(stock_in_weight) AS stock_in_weight, SUM(send_platform_weight) AS send_platform_weight,"
        + "                    MAX(is_finish) AS is_finish"
        + "               FROM t_warehouse_vegetable_handle WHERE del_flag='0'"
        + "               GROUP BY planting_record_id) h ON h.planting_record_id=p.id"
        + "  LEFT JOIN t_plant_crop_info c ON c.id=p.crop_id AND c.del_flag='0'"
        // 图取「产品配置中原材料」：作物 related_product → 果蔬原料 product(attr=2) 的 image_oss_id（row18 口径）
        + "  LEFT JOIN t_warehouse_product_info prod ON prod.id=c.related_product"
        + "                AND prod.del_flag='0' AND prod.product_attr=2"
        + "  LEFT JOIN t_plant_plot_info pl ON pl.id=p.plot_id AND pl.del_flag='0'"
        // 灾害损失：灾害农事记录(farm_type='disaster') 按 crop_id 预聚合 SUM(loss_yield)，先聚合再左联避免扇出
        + "  LEFT JOIN (SELECT fr.crop_id AS crop_id, SUM(fr.loss_yield) AS disaster_loss"
        + "               FROM t_plant_farm_records fr"
        + "              WHERE fr.del_flag='0' AND fr.tenant_id='1001'"
        + "                AND fr.farm_type='disaster' AND fr.crop_id IS NOT NULL"
        + "              GROUP BY fr.crop_id) dl ON dl.crop_id=p.crop_id"
        + " WHERE p.tenant_id='1001' AND p.del_flag='0'"
        + " GROUP BY p.crop_id"
        + " HAVING SUM(CASE WHEN p.handle_status <> 'done' THEN 1 ELSE 0 END) > 0"
        + " ORDER BY MAX(p.data_date) DESC")
    List<VegCropVo> selectCropAggList();

}
