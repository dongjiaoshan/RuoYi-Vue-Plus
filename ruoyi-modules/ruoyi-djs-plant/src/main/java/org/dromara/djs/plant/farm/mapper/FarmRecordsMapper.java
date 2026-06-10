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
}
