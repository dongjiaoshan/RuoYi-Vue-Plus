package org.dromara.djs.plant.farm.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
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
     * 取某 (作物, 地块) 的累计移栽百分比（移栽校验 + 状态机判定共用，避免口径漂移）。
     *
     * <p>= {@code SUM(transplant_percent) WHERE farm_type='transplant'}。空（无移栽记录）返 0。
     * {@code submitTransplant} 提交前用此校验「历史累计 + 本次 ≤ 100」，INSERT 后用此重查判定是否跨过
     * 100% 触发自动状态机（采摘完成 + 地块置空）。</p>
     *
     * @param cropId 作物 id
     * @param plotId 源地块 id
     * @return 累计移栽百分比（0-N，可能 >100 仅在历史脏数据下）
     */
    @Select("""
        SELECT COALESCE(SUM(transplant_percent), 0)
          FROM t_plant_farm_records
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND farm_type = 'transplant'
           AND crop_id = #{cropId}
           AND plot_id = #{plotId}
        """)
    int sumTransplantedPercent(@Param("cropId") Long cropId, @Param("plotId") Long plotId);

    /**
     * 取某 (作物, 地块, 种植批次) 的累计灾害损失率（灾害多次录入累计校验用）。
     *
     * <p>= {@code SUM(loss_rate) WHERE farm_type='disaster'}，按 (crop, plot, plant) 三元组定位——
     * 与 {@code computeLossYield}/{@code accumulateLossYield} 的 loss_yield 累加口径一致（同一种植批次内），
     * 换茬复种（新 plant_id）不掺旧茬历史。空（无灾害记录）返 0。
     * {@code submitDisaster}/{@code submitDisasterBatch} 提交前用此校验「历史累计 + 本次 ≤ 100%」。</p>
     *
     * @param cropId  作物 id
     * @param plotId  地块 id
     * @param plantId 种植批次 id
     * @return 累计损失率（0-100，历史脏数据下可能 >100）
     */
    @Select("""
        SELECT COALESCE(SUM(loss_rate), 0)
          FROM t_plant_farm_records
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND farm_type = 'disaster'
           AND crop_id = #{cropId}
           AND plot_id = #{plotId}
           AND plant_id = #{plantId}
        """)
    java.math.BigDecimal sumDisasterLossRate(@Param("cropId") Long cropId, @Param("plotId") Long plotId,
        @Param("plantId") Long plantId);

    /**
     * 按地块 + 农事类型聚合「30 天内上次同工种日期」（row14 空地类列表卡用）。
     *
     * <p>只读聚合，显式 {@code tenant_id='1001'} + {@code del_flag='0'}。仅取近 30 天内
     * （{@code farm_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)}）该工种最近一次日期。
     * 返回 (plotId, lastDate)，无近 30 天记录的地块不在结果中（service 兜底 null）。</p>
     *
     * @param plotIds  地块 id 集合（非空）
     * @param farmType 农事类型（翻耕/整地/施肥）
     * @return 每行 {@code {plotId, lastDate}}
     */
    @Select("""
        <script>
        SELECT plot_id AS plotId, MAX(farm_date) AS lastDate
          FROM t_plant_farm_records
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND farm_type = #{farmType}
           AND farm_date &gt;= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
           AND plot_id IN
           <foreach collection="plotIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
         GROUP BY plot_id
        </script>
        """)
    List<Map<String, Object>> selectLastFarmDateByPlotType(@Param("plotIds") List<Long> plotIds,
                                                            @Param("farmType") String farmType);

    /**
     * 取保育（{@code plot_type='nursery'}）且累计移栽未满（{@code SUM(transplant_percent) < 100}）的地块 id 集合
     * （row15②/row16 移栽候选地块判定·一处定义）。
     *
     * <p>从已种植明细（产出期 {@code plant_status='completed' AND harvest_status<>'completed'}）出发，
     * JOIN 保育地块，LEFT JOIN 移栽累计，过滤掉累计已满 100% 的地块。
     * 列表卡 COUNT 与详情逐行同此候选集，保证数字与点进去行数一致。
     * 显式 {@code tenant_id='1001'} + {@code del_flag='0'}（V1 单农场硬编码）。</p>
     *
     * @param cropId 作物 id
     * @return 候选地块 id 列表（按 plot_id 升序）
     */
    @Select("""
        SELECT d.plot_id
          FROM t_plant_plant_details d
          JOIN t_plant_plot_info p ON p.id = d.plot_id AND p.del_flag = '0' AND p.tenant_id = '1001'
          LEFT JOIN (
                SELECT plot_id, COALESCE(SUM(transplant_percent), 0) AS moved
                  FROM t_plant_farm_records
                 WHERE del_flag = '0' AND tenant_id = '1001' AND farm_type = 'transplant'
                   AND crop_id = #{cropId}
                 GROUP BY plot_id
          ) tp ON tp.plot_id = d.plot_id
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.crop_id = #{cropId}
           AND d.plant_status = 'completed'
           AND d.harvest_status <> 'completed'
           AND p.plot_type = 'nursery'
           AND COALESCE(tp.moved, 0) < 100
         GROUP BY d.plot_id
         ORDER BY d.plot_id ASC
        """)
    List<Long> selectTransplantCandidatePlotIds(@Param("cropId") Long cropId);

    /**
     * 批量取多地块「上次农事记录的班组」（r65 默认班组回填）。
     *
     * <p>每个地块取其最近一条农事记录（{@code farm_date DESC, id DESC}）的 {@code farm_by}（班组 id）。
     * 用窗口子查询取每地块 farm_by 不为空的最新一条。显式 {@code tenant_id='1001'} + {@code del_flag='0'}
     * （V1 单农场硬编码，不依赖租户拦截器）。返回 {@code {plotId, lastTeamId}}，无农事记录的地块不出行。</p>
     *
     * @param plotIds 地块 id 集合（非空）
     * @return 每行 {@code {plotId, lastTeamId}}
     */
    @Select("""
        <script>
        SELECT t.plot_id AS plotId, t.farm_by AS lastTeamId
          FROM (
            SELECT plot_id, farm_by,
                   ROW_NUMBER() OVER (PARTITION BY plot_id ORDER BY farm_date DESC, id DESC) AS rn
              FROM t_plant_farm_records
             WHERE del_flag = '0'
               AND tenant_id = '1001'
               AND farm_by IS NOT NULL
               AND plot_id IN
               <foreach collection="plotIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
          ) t
         WHERE t.rn = 1
        </script>
        """)
    List<Map<String, Object>> selectLastFarmTeamByPlot(@Param("plotIds") List<Long> plotIds);

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
     * <p>只列已种植（{@code t_plant_plant_details} 有行）且处于产出期（{@code plant_status='completed' AND
     * harvest_status<>'completed'}，即种植完成但未采完）的作物，按作物聚合
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
                AND fr.farm_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.plant_status = 'completed'
           AND d.harvest_status <> 'completed'
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
     * 移栽 = 保育地块上、处于产出期（{@code plant_status='completed' AND harvest_status<>'completed'}，
     * 即种植完成但未采完）的作物。「nursery」值由 FIX-PLT-PLOTTYPE-001 灌入 {@code djs_plot_type} 字典。</p>
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
                AND fr.farm_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
          LEFT JOIN (
                SELECT crop_id, plot_id, COALESCE(SUM(transplant_percent), 0) AS moved
                  FROM t_plant_farm_records
                 WHERE del_flag = '0' AND tenant_id = '1001' AND farm_type = 'transplant'
                 GROUP BY crop_id, plot_id
          ) tp ON tp.crop_id = d.crop_id AND tp.plot_id = d.plot_id
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.plant_status = 'completed'
           AND d.harvest_status <> 'completed'
           AND p.plot_type = 'nursery'
           AND COALESCE(tp.moved, 0) < 100
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
     * （采摘完成 {@code djs_pick_status}），与多选页 {@code listCropPlots} 退茬口径一致（T3）。
     * 额外要求 {@code p.plot_status=3}（地块仍处采摘态、未退茬变空地）——已退茬地块 plot_status=1 应排除，
     * 否则空地仍出现在退茬候选并可被重复退茬（231）。</p>
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
                AND fr.farm_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.harvest_status = 'completed'
           AND p.plot_status = 3
           AND (#{zoneId} IS NULL OR p.zone_id = #{zoneId})
           AND (#{plotCode} IS NULL OR p.plot_code = #{plotCode})
         GROUP BY d.crop_id, c.crop_name, c.crop_code
         ORDER BY c.crop_code ASC
        """)
    List<Map<String, Object>> selectCropTargetCardsForRotation(@Param("farmType") String farmType,
                                                               @Param("zoneId") Long zoneId,
                                                               @Param("plotCode") String plotCode);

    /**
     * 生长工种「片区胶囊」聚合（FIX-PLT-MP-CROPSEL-001 P12/P15·补片区筛选）。
     *
     * <p>按片区统计该工种可操作地块去重数（产出期 {@code plant_status='completed' AND harvest_status<>'completed'}，
     * 即种植完成但未采完），含 0 地块的活跃片区
     * （{@code LEFT JOIN} 从片区表起，胶囊全量显示）。与 {@link #selectCropTargetCardsForGrow} 同口径，
     * 胶囊计数 = 该片区下作物卡 plotCount 之和。手写多表 LEFT JOIN，{@code @InterceptorIgnore} 关租户行注入。</p>
     *
     * @param farmType 农事类型（生长类）
     * @return 每行 {@code {zoneId, zoneName, plotCount}}，按 zone_name 升序
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT z.id AS zoneId, z.zone_name AS zoneName, COUNT(DISTINCT d.plot_id) AS plotCount
          FROM t_plant_plot_zone z
          LEFT JOIN t_plant_plot_info p
            ON p.zone_id = z.id AND p.del_flag = '0' AND p.tenant_id = '1001'
          LEFT JOIN t_plant_plant_details d
            ON d.plot_id = p.id AND d.del_flag = '0' AND d.tenant_id = '1001'
           AND d.plant_status = 'completed' AND d.harvest_status <> 'completed'
         WHERE z.del_flag = '0' AND z.tenant_id = '1001' AND z.zone_status = 1
         GROUP BY z.id, z.zone_name
         ORDER BY z.zone_name ASC
        """)
    List<Map<String, Object>> selectCropZoneCountsForGrow();

    /**
     * 移栽工种「片区胶囊」聚合（FIX-PLT-MP-CROPSEL-001 P12 移栽·补片区筛选）。
     *
     * <p>同 {@link #selectCropZoneCountsForGrow}，但只统计保育类型地块（{@code p.plot_type='nursery'}）——
     * 与 {@link #selectCropTargetCardsForTransplant} 同口径。</p>
     *
     * @return 每行 {@code {zoneId, zoneName, plotCount}}
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT z.id AS zoneId, z.zone_name AS zoneName, COUNT(DISTINCT d.plot_id) AS plotCount
          FROM t_plant_plot_zone z
          LEFT JOIN t_plant_plot_info p
            ON p.zone_id = z.id AND p.del_flag = '0' AND p.tenant_id = '1001'
           AND p.plot_type = 'nursery'
          LEFT JOIN t_plant_plant_details d
            ON d.plot_id = p.id AND d.del_flag = '0' AND d.tenant_id = '1001'
           AND d.plant_status = 'completed' AND d.harvest_status <> 'completed'
          LEFT JOIN (
                SELECT crop_id, plot_id, COALESCE(SUM(transplant_percent), 0) AS moved
                  FROM t_plant_farm_records
                 WHERE del_flag = '0' AND tenant_id = '1001' AND farm_type = 'transplant'
                 GROUP BY crop_id, plot_id
          ) tp ON tp.crop_id = d.crop_id AND tp.plot_id = d.plot_id
         WHERE z.del_flag = '0' AND z.tenant_id = '1001' AND z.zone_status = 1
           AND (d.plot_id IS NULL OR COALESCE(tp.moved, 0) < 100)
         GROUP BY z.id, z.zone_name
         ORDER BY z.zone_name ASC
        """)
    List<Map<String, Object>> selectCropZoneCountsForTransplant();

    /**
     * 退茬工种「片区胶囊」聚合（FIX-PLT-MP-CROPSEL-001 P22 退茬·补片区筛选）。
     *
     * <p>同 {@link #selectCropZoneCountsForGrow}，但状态口径用采摘完成（{@code d.harvest_status='completed'}）——
     * 与 {@link #selectCropTargetCardsForRotation} 同口径。地块额外要求 {@code p.plot_status=3}
     * （未退茬变空地），与列表卡/多选页退茬口径一致（231）。</p>
     *
     * @return 每行 {@code {zoneId, zoneName, plotCount}}
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT z.id AS zoneId, z.zone_name AS zoneName, COUNT(DISTINCT d.plot_id) AS plotCount
          FROM t_plant_plot_zone z
          LEFT JOIN t_plant_plot_info p
            ON p.zone_id = z.id AND p.del_flag = '0' AND p.tenant_id = '1001'
           AND p.plot_status = 3
          LEFT JOIN t_plant_plant_details d
            ON d.plot_id = p.id AND d.del_flag = '0' AND d.tenant_id = '1001'
           AND d.harvest_status = 'completed'
         WHERE z.del_flag = '0' AND z.tenant_id = '1001' AND z.zone_status = 1
         GROUP BY z.id, z.zone_name
         ORDER BY z.zone_name ASC
        """)
    List<Map<String, Object>> selectCropZoneCountsForRotation();
}
