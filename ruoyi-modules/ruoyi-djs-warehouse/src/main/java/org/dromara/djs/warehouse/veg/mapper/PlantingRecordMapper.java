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
     * <p>过滤 handle_status != 'done' 且 del_flag='0'，按 data_date DESC 限制 50 条。
     * 排除采摘活动地块（plot+crop 命中 is_pick=1 明细）：活动产量走采摘活动去向链
     * （veg_fresh 直入库 / platform 直写处理流水），不进毛菜处理过磅，避免同批量双录（row12）。</p>
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
        + "   AND NOT EXISTS (SELECT 1 FROM t_plant_plant_details d"
        + "        WHERE d.plot_id = p.plot_id AND d.crop_id = p.crop_id"
        + "          AND d.is_pick = 1 AND d.del_flag = '0')"
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
     *
     * <p><b>V6 row102 两个重量口径</b>：</p>
     * <ul>
     *   <li>{@code handledWeight}（果蔬处理重量）= {@code h.handled_weight}
     *       = 带该地块标识的产品<b>从毛菜间出库</b>的总重量（月台 + 有机饲喂，见
     *       {@code VegetableHandleServiceImpl#submitProcess}）。历史行保留它当时写下的值，
     *       甲方要的「历史记录按当时口径展示」天然满足。</li>
     *   <li>{@code remainWeight}（剩余重量）= <b>这条种植记录</b>采下来、还躺在毛菜间的实时库存，
     *       不再用 {@code picked − handled − feed} 减出来 —— 采摘录入即入毛菜保鲜库之后，
     *       库存才是唯一真相（作物没配产品时采摘不建篮、毛菜间出库 / 盘点也会动库存，减法必然漂移）。</li>
     * </ul>
     *
     * <p><b>剩余重量子查询按 {@code s.source_biz_id = p.id} 收窄到本条种植记录</b>，不按
     * {@code (地块, 作物名下产品)} 算。后者是第一版的写法，它与 planting_record 无关：同一地块上的
     * 两条种植记录（两季 / 补录）会各自读到同一份库存、都显示 65kg，mp 头卡 {@code Σ remainWeight}
     * 因此翻倍成 130；采摘活动直送毛菜保鲜室的货也会被算成某条种植记录的剩余。
     * 换成来源键之后，这三种串味一次消失，且与处理录入的出库上限、分产品剩余完全同源
     * （{@code LocationStockMapper#sumPlotProductStock} / {@code #selectPlotProductStocks} 同样带此条件）。
     * {@code third_phase=0} 排除三期篮（三期不按地块管理）。</p>
     */
    @Select("SELECT p.id AS plantingRecordId, p.plot_id AS plotId, pl.plot_code AS plotCode, pl.plot_area AS plotArea,"
        + "       COALESCE(h.picked_weight,0) AS harvestWeight,"
        + "       COALESCE(h.handled_weight,0) AS handledWeight,"
        + "       COALESCE(h.feed_weight,0) AS feedWeight,"
        + "       (SELECT COALESCE(SUM(s.product_stock),0)"
        + "          FROM t_warehouse_location_stock s"
        + "          JOIN t_warehouse_location_info li ON li.id = s.location_id"
        + "           AND li.location_code = 'L0006' AND li.del_flag='0' AND li.tenant_id='1001'"
        + "         WHERE s.source_biz_id = p.id AND s.product_id IS NOT NULL"
        + "           AND s.third_phase = 0 AND s.del_flag='0' AND s.tenant_id='1001') AS remainWeight,"
        + "       GREATEST(COALESCE(pl.plot_area * c.predicted_per,0) - COALESCE(dl.disaster_loss,0),0) AS expectYield,"
        + "       COALESCE(dl.disaster_loss,0) AS disasterLoss,"
        + "       CASE WHEN c.related_product IS NOT NULL THEN 1 ELSE 0 END AS hasRelatedProduct,"
        + "       CASE WHEN h.is_weighed=1 THEN 'done' ELSE 'pending' END AS weighStatus,"
        + "       CASE WHEN h.is_finish=1 THEN 'done' ELSE 'pending' END AS processStatus,"
        + "       h.id AS handleId"
        + "  FROM t_warehouse_planting_record p"
        + "  LEFT JOIN t_warehouse_vegetable_handle h ON h.planting_record_id=p.id AND h.del_flag='0'"
        + "  LEFT JOIN t_plant_plot_info pl ON pl.id=p.plot_id AND pl.del_flag='0'"
        + "  LEFT JOIN t_plant_crop_info c ON c.id=p.crop_id AND c.del_flag='0'"
        // 小程序 row260：地块级预计产量同样要扣灾害损失（作物级聚合早已扣，见 VegetableHandleMapper，
        // 地块级漏扣 → 同一作物「头卡预计产量」与「地块卡预计产量」两个口径打架）。
        // 按 plot_id + crop_id 预聚合再左联（先聚合避免与父表多 planting_record 行扇出重复计数）；
        // GREATEST(...,0) 兜底：灾害损失大于理论产量时归 0，不显负数。
        + "  LEFT JOIN (SELECT fr.plot_id AS plot_id, fr.crop_id AS crop_id, SUM(fr.loss_yield) AS disaster_loss"
        + "               FROM t_plant_farm_records fr"
        + "              WHERE fr.del_flag='0' AND fr.tenant_id='1001'"
        + "                AND fr.farm_type='disaster' AND fr.crop_id IS NOT NULL AND fr.plot_id IS NOT NULL"
        + "              GROUP BY fr.plot_id, fr.crop_id) dl"
        + "    ON dl.plot_id=p.plot_id AND dl.crop_id=p.crop_id"
        + " WHERE p.tenant_id='1001' AND p.del_flag='0' AND p.crop_id=#{cropId}"
        + " ORDER BY p.data_date DESC, p.id DESC")
    List<VegPlotDetailVo> selectPlotDetailByCrop(@Param("cropId") Long cropId);

}
