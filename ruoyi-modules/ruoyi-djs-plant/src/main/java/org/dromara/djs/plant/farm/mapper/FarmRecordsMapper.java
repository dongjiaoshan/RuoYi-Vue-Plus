package org.dromara.djs.plant.farm.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.farm.domain.FarmRecords;
import org.dromara.djs.plant.farm.domain.vo.FarmRecordsVo;

import java.util.List;
import java.util.Map;

/**
 * 农事记录 Mapper（PLT-WORK-001）。
 *
 * @author djs
 * @since PLT-WORK-001
 */
public interface FarmRecordsMapper extends BaseMapperPlus<FarmRecords, FarmRecordsVo> {

    /**
     * 取当日已生成 record_no 中序号最大值（用于 inline 业务码生成）。
     *
     * <p>format: {@code FRyyyyMMddNNNN}（NNNN 4 位序号）。本 mapper 直接取最大 record_no 字符串，
     * 由 service 层提取后 4 位 + 1 拼下一个号。MySQL 字典排序对固定长度数字串正确。</p>
     *
     * @param prefix 期望 {@code FRyyyyMMdd}（service 拼好传入）
     * @return 当日最大 record_no；当日 0 行时返 null
     */
    @Select("SELECT MAX(record_no) FROM t_plant_farm_records WHERE tenant_id = #{tenantId} AND record_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxRecordNoByPrefix(@Param("tenantId") String tenantId, @Param("prefix") String prefix);

    /**
     * 按作物 + 农事类型聚合每个地块的「上次同类农事日期」（FIX-PLT-MP-WORK-BATCH-001 #2 多选页用）。
     *
     * <p>只读聚合，显式 {@code tenant_id='1001'} + {@code del_flag='0'}。返回 (plotId, lastFarmDate)
     * 行，service 端组装进 {@code FarmCropPlotVo.lastFarmDate / intervalDays}。</p>
     *
     * @param cropId   作物 id
     * @param farmType 农事类型
     * @return 每行 {@code {plotId, lastFarmDate}}（无记录的地块不在结果中，service 兜底 null）
     */
    @Select("""
        SELECT plot_id AS plotId, MAX(farm_date) AS lastFarmDate
          FROM t_plant_farm_records
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND crop_id = #{cropId}
           AND farm_type = #{farmType}
         GROUP BY plot_id
        """)
    List<Map<String, Object>> selectLastFarmDateByCropType(@Param("cropId") Long cropId, @Param("farmType") String farmType);

    /**
     * 按作物聚合每个地块的「累计移栽百分比」（FIX-PLT-MP-WORK-BATCH-001 移栽进度层）。
     *
     * <p>= SUM(transplant_percent) WHERE farm_type='transplant'。返回 (plotId, transplantedPercent)。
     * service 组装进 {@link org.dromara.djs.plant.farm.domain.vo.FarmCropPlotVo#getTransplantedPercent()}。</p>
     *
     * @param cropId 作物 id
     * @return 每行 {@code {plotId, transplantedPercent}}（无移栽记录的地块不在结果中，service 兜底 0）
     */
    @Select("""
        SELECT plot_id AS plotId, COALESCE(SUM(transplant_percent), 0) AS transplantedPercent
          FROM t_plant_farm_records
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND crop_id = #{cropId}
           AND farm_type = 'transplant'
         GROUP BY plot_id
        """)
    List<Map<String, Object>> selectTransplantedPercentByCrop(@Param("cropId") Long cropId);

    /**
     * 按 farm_type 聚合「当日已处理地块去重数」（FIX-PLT-MP-TILL-001 P7 dispatchSummary）。
     *
     * <p>{@code COUNT(DISTINCT plot_id) WHERE farm_date=今日 GROUP BY farm_type}——同地块当日多次记录
     * 仍算 1 块（邓博真机定义）。12 工种统一去重，不特判 tillage。缺的工种 service 端补 0。</p>
     *
     * @param farmDate 今日（service 传入）
     * @return 每行 {@code {farmType, plotCount}}（当日 0 记录的工种不在结果中，service 补 0）
     */
    @Select("""
        SELECT farm_type AS farmType, COUNT(DISTINCT plot_id) AS plotCount
          FROM t_plant_farm_records
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND farm_date = #{farmDate}
         GROUP BY farm_type
        """)
    List<Map<String, Object>> selectTodayProcessedPlotCount(@Param("farmDate") java.time.LocalDate farmDate);

    /**
     * 生长类工种「作物目标卡」聚合（FIX-PLT-MP-CROPSEL-001）。
     *
     * <p>只列已种植（{@code t_plant_plant_details} 有行）且 {@code plant_status='ongoing'} 的作物，按作物聚合
     * 可操作地块去重数 + 最近同工种农事日期。覆盖 6 生长工种（water_fertilize/irrigation/weed/pest_control/
     * pruning/harvest_activity）+ 灾害（disaster）。显式 {@code tenant_id='1001'} + {@code del_flag='0'}
     * （V1 单农场硬编码，不依赖租户拦截器）。</p>
     *
     * @param farmType 农事类型（用于 LEFT JOIN 取最近同工种农事日期）
     * @param zoneId   片区 id（可空，非空时只统计该片区下地块）
     * @param plotCode 地块编号（可空，非空时只统计该地块）
     * @return 每行 {@code {cropId, cropName, cropCode, plotCount, lastFarmDate}}
     */
    @Select("""
        SELECT d.crop_id AS cropId, c.crop_name AS cropName, c.crop_code AS cropCode,
               COUNT(DISTINCT d.plot_id) AS plotCount, MAX(fr.farm_date) AS lastFarmDate
          FROM t_plant_plant_details d
          JOIN t_plant_plot_info p ON p.id = d.plot_id AND p.del_flag = '0' AND p.tenant_id = '1001'
          JOIN t_plant_crop_info  c ON c.id = d.crop_id AND c.del_flag = '0' AND c.tenant_id = '1001'
          LEFT JOIN t_plant_farm_records fr
                 ON fr.crop_id = d.crop_id AND fr.farm_type = #{farmType}
                AND fr.del_flag = '0' AND fr.tenant_id = '1001'
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.plant_status = 'ongoing'
           AND (#{zoneId} IS NULL OR p.zone_id = #{zoneId})
           AND (#{plotCode} IS NULL OR p.plot_code = #{plotCode})
         GROUP BY d.crop_id, c.crop_name, c.crop_code
         ORDER BY c.crop_code ASC
        """)
    List<Map<String, Object>> selectCropTargetCardsForGrow(@Param("farmType") String farmType,
                                                            @Param("zoneId") Long zoneId,
                                                            @Param("plotCode") String plotCode);

    /**
     * 移栽工种「作物目标卡」聚合（FIX-PLT-MP-CROPSEL-001 P13）。
     *
     * <p>同 {@link #selectCropTargetCardsForGrow}，但额外要求地块为保育类型（{@code plot_type='nursery'}）——
     * 移栽 = 保育地块上、种植进行中的作物。「nursery」值由 FIX-PLT-PLOTTYPE-001 灌入 {@code djs_plot_type} 字典。</p>
     *
     * @param farmType 农事类型（transplant）
     * @param zoneId   片区 id（可空）
     * @param plotCode 地块编号（可空）
     * @return 每行 {@code {cropId, cropName, cropCode, plotCount, lastFarmDate}}
     */
    @Select("""
        SELECT d.crop_id AS cropId, c.crop_name AS cropName, c.crop_code AS cropCode,
               COUNT(DISTINCT d.plot_id) AS plotCount, MAX(fr.farm_date) AS lastFarmDate
          FROM t_plant_plant_details d
          JOIN t_plant_plot_info p ON p.id = d.plot_id AND p.del_flag = '0' AND p.tenant_id = '1001'
          JOIN t_plant_crop_info  c ON c.id = d.crop_id AND c.del_flag = '0' AND c.tenant_id = '1001'
          LEFT JOIN t_plant_farm_records fr
                 ON fr.crop_id = d.crop_id AND fr.farm_type = #{farmType}
                AND fr.del_flag = '0' AND fr.tenant_id = '1001'
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.plant_status = 'ongoing'
           AND p.plot_type = 'nursery'
           AND (#{zoneId} IS NULL OR p.zone_id = #{zoneId})
           AND (#{plotCode} IS NULL OR p.plot_code = #{plotCode})
         GROUP BY d.crop_id, c.crop_name, c.crop_code
         ORDER BY c.crop_code ASC
        """)
    List<Map<String, Object>> selectCropTargetCardsForTransplant(@Param("farmType") String farmType,
                                                                  @Param("zoneId") Long zoneId,
                                                                  @Param("plotCode") String plotCode);

    /**
     * 退茬工种「作物目标卡」聚合（FIX-PLT-MP-CROPSEL-001 P22）。
     *
     * <p>同 {@link #selectCropTargetCardsForGrow}，但状态口径用采摘状态 {@code harvest_status='completed'}
     * （采摘完成 {@code djs_pick_status}），与多选页 {@code listCropPlots} 退茬口径一致（T3）。</p>
     *
     * @param farmType 农事类型（rotation）
     * @param zoneId   片区 id（可空）
     * @param plotCode 地块编号（可空）
     * @return 每行 {@code {cropId, cropName, cropCode, plotCount, lastFarmDate}}
     */
    @Select("""
        SELECT d.crop_id AS cropId, c.crop_name AS cropName, c.crop_code AS cropCode,
               COUNT(DISTINCT d.plot_id) AS plotCount, MAX(fr.farm_date) AS lastFarmDate
          FROM t_plant_plant_details d
          JOIN t_plant_plot_info p ON p.id = d.plot_id AND p.del_flag = '0' AND p.tenant_id = '1001'
          JOIN t_plant_crop_info  c ON c.id = d.crop_id AND c.del_flag = '0' AND c.tenant_id = '1001'
          LEFT JOIN t_plant_farm_records fr
                 ON fr.crop_id = d.crop_id AND fr.farm_type = #{farmType}
                AND fr.del_flag = '0' AND fr.tenant_id = '1001'
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.harvest_status = 'completed'
           AND (#{zoneId} IS NULL OR p.zone_id = #{zoneId})
           AND (#{plotCode} IS NULL OR p.plot_code = #{plotCode})
         GROUP BY d.crop_id, c.crop_name, c.crop_code
         ORDER BY c.crop_code ASC
        """)
    List<Map<String, Object>> selectCropTargetCardsForRotation(@Param("farmType") String farmType,
                                                               @Param("zoneId") Long zoneId,
                                                               @Param("plotCode") String plotCode);
}
