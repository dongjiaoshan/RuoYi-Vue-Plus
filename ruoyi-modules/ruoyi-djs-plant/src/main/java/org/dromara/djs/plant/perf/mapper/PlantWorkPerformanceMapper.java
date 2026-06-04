package org.dromara.djs.plant.perf.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.perf.domain.PlantWorkPerformance;
import org.dromara.djs.plant.perf.domain.vo.PerfAggRow;
import org.dromara.djs.plant.perf.domain.vo.PlantWorkPerformanceVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 班组绩效 Mapper（PLT-PERF-001）。
 *
 * <p>列表主体走 {@link BaseMapperPlus#selectVoPage}；teamName / cropName 由 service 批量
 * enrich（{@link #selectTeamNames} / {@link #selectCropNames}）避免 N+1。</p>
 *
 * <p><b>聚合 SQL 租户隔离</b>：V1 未启全局 MP 多租户拦截器，{@code @Select} 原生 SQL 不会被自动
 * 注入 tenant 过滤 → {@link #aggregateByMonth} / {@link #selectCropUnitPrices} 必须<b>显式手写
 * {@code tenant_id='1001'}</b>，多表 JOIN 时每张表 + ON 子句都带 tenant 条件（PLT-PERF-001 §0 第 6 项）。</p>
 *
 * @author djs
 * @since PLT-PERF-001
 */
public interface PlantWorkPerformanceMapper extends BaseMapperPlus<PlantWorkPerformance, PlantWorkPerformanceVo> {

    /**
     * 按 班组(harvest_by) × 作物(crop_id) 聚合指定月份的采摘总量。
     *
     * <p>聚合源：{@code t_plant_plant_details.actual_yield}（实际产量，斤）。
     * 月份维度：{@code DATE_FORMAT(end_actualdate, '%Y-%m')}（采摘完成日所在月）。
     * 仅纳入 {@code actual_yield > 0 AND harvest_by IS NOT NULL} 的有效采摘行。</p>
     *
     * <p>显式 {@code tenant_id='1001'}（V1 单租户，无全局拦截器）。</p>
     *
     * @param statMonth 统计月份（"yyyy-MM"）
     * @return 聚合行（teamId / cropId / pickWeight）；无数据返空 list
     */
    @Select("""
        SELECT
            harvest_by AS teamId,
            crop_id AS cropId,
            SUM(actual_yield) AS pickWeight
          FROM t_plant_plant_details
         WHERE tenant_id = '1001'
           AND del_flag = '0'
           AND actual_yield > 0
           AND harvest_by IS NOT NULL
           AND DATE_FORMAT(end_actualdate, '%Y-%m') = #{statMonth}
         GROUP BY harvest_by, crop_id
        """)
    List<PerfAggRow> aggregateByMonth(@Param("statMonth") String statMonth);

    /**
     * 批量查询作物当前采摘单价（结算瞬间取快照用）。
     *
     * <p>显式 {@code tenant_id='1001'}（V1 单租户）。</p>
     *
     * @param cropIds 作物 id 集合（已 dedupe，非空）
     * @return key=cropId(Long) / value=pickUnitPrice(BigDecimal，可能为 null)
     */
    @Select("<script>" +
        "SELECT id AS cropId, pick_unit_price AS pickUnitPrice " +
        "FROM t_plant_crop_info " +
        "WHERE tenant_id = '1001' AND del_flag = '0' AND id IN " +
        "<foreach collection='cropIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach>" +
        "</script>")
    List<Map<String, Object>> selectCropUnitPrices(@Param("cropIds") Collection<Long> cropIds);

    /**
     * 批量查询 teamId → teamName 映射（列表 enrich）。
     *
     * @param teamIds 班组 id 集合（非空）
     * @return key=teamId(Long) / value=teamName(String)
     */
    @Select("<script>" +
        "SELECT id AS teamId, team_name AS teamName " +
        "FROM t_plant_work_team " +
        "WHERE tenant_id = '1001' AND id IN " +
        "<foreach collection='teamIds' item='tid' open='(' separator=',' close=')'>#{tid}</foreach>" +
        "</script>")
    // 不过滤 del_flag：班组被软删后历史绩效行仍需解析出班组名
    List<Map<String, Object>> selectTeamNames(@Param("teamIds") Collection<Long> teamIds);

    /**
     * 批量查询 cropId → cropName 映射（列表 enrich）。
     *
     * @param cropIds 作物 id 集合（非空）
     * @return key=cropId(Long) / value=cropName(String)
     */
    @Select("<script>" +
        "SELECT id AS cropId, crop_name AS cropName " +
        "FROM t_plant_crop_info " +
        "WHERE tenant_id = '1001' AND id IN " +
        "<foreach collection='cropIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach>" +
        "</script>")
    List<Map<String, Object>> selectCropNames(@Param("cropIds") Collection<Long> cropIds);
}
