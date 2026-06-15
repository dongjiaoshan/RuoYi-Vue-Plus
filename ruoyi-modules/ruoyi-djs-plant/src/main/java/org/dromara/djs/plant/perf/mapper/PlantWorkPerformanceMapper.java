package org.dromara.djs.plant.perf.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.perf.domain.PlantWorkPerformance;
import org.dromara.djs.plant.perf.domain.vo.FarmCountRow;
import org.dromara.djs.plant.perf.domain.vo.PerfAggRow;
import org.dromara.djs.plant.perf.domain.vo.PerfListRow;
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
     * 主列表分页：按 班组 × 月 聚合已结算绩效行（rework 134/135）。
     *
     * <p>聚合源：{@code t_plant_work_performance}（已结算落库的逐作物行）。
     * GROUP BY {@code stat_month, team_id}，每组算绩效总额 / 采摘总量 / 作物种类数。
     * 命中索引 {@code idx_month_team (stat_month, team_id)}。</p>
     *
     * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 无全局拦截器，原生 SQL 不自动注入
     * 租户 / 软删过滤，漏写会串租户或带出 del_flag='2' 的被覆盖历史行）。</p>
     *
     * @param page      分页参数（IPage，由 MP 分页拦截器填充 total / 切片）
     * @param statMonth 统计月份精确匹配（可空）
     * @param teamId    班组精确匹配（可空）
     * @return 班组 × 月聚合行（teamName / farmCount 由 service enrich，此处不含）
     */
    @Select("""
        <script>
        SELECT
            stat_month AS statMonth,
            team_id AS teamId,
            SUM(performance_amount) AS teamMonthAmount,
            SUM(pick_weight) AS totalPickWeight,
            COUNT(DISTINCT crop_id) AS cropCount
          FROM t_plant_work_performance
         WHERE tenant_id = '1001'
           AND del_flag = '0'
        <if test="statMonth != null and statMonth != ''"> AND stat_month = #{statMonth} </if>
        <if test="teamId != null"> AND team_id = #{teamId} </if>
         GROUP BY stat_month, team_id
         ORDER BY stat_month DESC, team_id ASC
        </script>
        """)
    IPage<PerfListRow> selectTeamMonthPage(IPage<PerfListRow> page,
                                           @Param("statMonth") String statMonth,
                                           @Param("teamId") Long teamId);

    /**
     * 批量统计农事次数：对 {@code t_plant_farm_records} 按 (班组, 月) 计数（rework 134 农事次数列）。
     *
     * <p>口径：全部 {@code farm_type}（不过滤采摘类，与详情农事记录 tab 现有口径一致）。
     * 月份维度 {@code DATE_FORMAT(farm_date,'%Y-%m')}。仅统计入参 (teamId, statMonth) 组合，
     * 一次取回避免 N+1。</p>
     *
     * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 单租户，原生 SQL 不自动注入）。</p>
     *
     * @param pairs (farmBy, statMonth) 组合集合（已 dedupe，非空；每元素为长度 2 的对象数组 [Long teamId, String month]）
     * @return 命中的 (farmBy / statMonth / cnt) 行；未命中组合不返回（service 端默认 0）
     */
    @Select("""
        <script>
        SELECT
            farm_by AS farmBy,
            DATE_FORMAT(farm_date, '%Y-%m') AS statMonth,
            COUNT(*) AS cnt
          FROM t_plant_farm_records
         WHERE tenant_id = '1001'
           AND del_flag = '0'
           AND farm_by IN
        <foreach collection='teamIds' item='tid' open='(' separator=',' close=')'>#{tid}</foreach>
           AND DATE_FORMAT(farm_date, '%Y-%m') IN
        <foreach collection='months' item='m' open='(' separator=',' close=')'>#{m}</foreach>
         GROUP BY farm_by, DATE_FORMAT(farm_date, '%Y-%m')
        </script>
        """)
    List<FarmCountRow> countFarmByTeamMonths(@Param("teamIds") Collection<Long> teamIds,
                                             @Param("months") Collection<String> months);

    /**
     * 详情按作物分行：查某 班组 × 月 下全部作物绩效行（rework 135 产量绩效 tab）。
     *
     * <p>返回逐作物的采摘量 / 单价快照 / 该作物绩效额；cropName 由 service enrich
     * （复用 {@link #selectCropNames}）。</p>
     *
     * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 单租户，原生 SQL 不自动注入）。</p>
     *
     * @param teamId    班组 ID（非空）
     * @param statMonth 统计月份 yyyy-MM（非空）
     * @return 逐作物绩效行（不含 cropName，service enrich 后填）
     */
    @Select("""
        SELECT
            id,
            stat_month AS statMonth,
            team_id AS teamId,
            crop_id AS cropId,
            pick_weight AS pickWeight,
            unit_price_snapshot AS unitPriceSnapshot,
            performance_amount AS performanceAmount,
            performance_rule AS performanceRule,
            remark,
            create_time AS createTime
          FROM t_plant_work_performance
         WHERE tenant_id = '1001'
           AND del_flag = '0'
           AND team_id = #{teamId}
           AND stat_month = #{statMonth}
         ORDER BY crop_id ASC
        """)
    List<PlantWorkPerformanceVo> selectCropRowsByTeamMonth(@Param("teamId") Long teamId,
                                                           @Param("statMonth") String statMonth);

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
