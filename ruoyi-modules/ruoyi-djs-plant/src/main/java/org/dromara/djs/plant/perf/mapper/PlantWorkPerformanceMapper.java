package org.dromara.djs.plant.perf.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.perf.domain.PlantWorkPerformance;
import org.dromara.djs.plant.perf.domain.vo.FarmCountRow;
import org.dromara.djs.plant.perf.domain.vo.PerfActivityAggRow;
import org.dromara.djs.plant.perf.domain.vo.PerfAggRow;
import org.dromara.djs.plant.perf.domain.vo.PlotCropTeamRow;
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
     * 按 采摘班组(team_id) × 作物(crop_id) × 产品(product_id) 聚合指定月份的采收总量
     * （row39 + row43 多组平摊口径；V6 row20 起加产品维度）。
     *
     * <p>聚合源：{@code t_warehouse_handle_record.record_weight}（毛菜处理间采摘重量录入时各组对应作物的
     * 称重重量，公斤）。仅纳入 {@code record_type=1}（采收录入，非处理录入）、{@code record_weight > 0}
     * 的有效称重行。月份维度：{@code DATE_FORMAT(handle_time, '%Y-%m')}（称重录入时刻所在月）。</p>
     *
     * <p><b>row43 多组平摊</b>：一条采收记录可选多个班组（多选中间表
     * {@code t_warehouse_handle_record_team}）。旧口径按单列 {@code team_id}（写首值）GROUP BY，重量全记第一
     * 组。改为按中间表拆行——每条 record 的 {@code record_weight} 除以其班组数
     * {@code COALESCE(中间表班组数, 1)} 后按班组归集（LEFT JOIN 中间表：无中间表行的旧记录兜底单列
     * {@code r.team_id}、班组数按 1 计）。每组平摊量 SUM 后 {@code ROUND(...,3)} 保留 3 位小数。</p>
     *
     * <p>显式 {@code tenant_id='1001'}（V1 单租户，无全局拦截器），中间表 JOIN 每张表带 tenant / del_flag。</p>
     *
     * @param statMonth 统计月份（"yyyy-MM"）
     * @return 聚合行（teamId / cropId / pickWeight）；无数据返空 list
     */
    @Select("""
        SELECT
            s.teamId AS teamId,
            s.cropId AS cropId,
            s.productId AS productId,
            ROUND(SUM(s.shareWeight), 3) AS pickWeight
          FROM (
            SELECT
                COALESCE(rt.team_id, r.team_id) AS teamId,
                r.crop_id AS cropId,
                r.product_id AS productId,
                r.record_weight / COALESCE(tc.cnt, 1) AS shareWeight
              FROM t_warehouse_handle_record r
              LEFT JOIN t_warehouse_handle_record_team rt
                     ON rt.record_id = r.id AND rt.del_flag = '0' AND rt.tenant_id = '1001'
              LEFT JOIN (SELECT record_id, COUNT(*) AS cnt
                           FROM t_warehouse_handle_record_team
                          WHERE del_flag = '0' AND tenant_id = '1001'
                          GROUP BY record_id) tc
                     ON tc.record_id = r.id
             WHERE r.tenant_id = '1001'
               AND r.del_flag = '0'
               AND r.record_type = 1
               AND r.record_weight > 0
               AND DATE_FORMAT(r.handle_time, '%Y-%m') = #{statMonth}
               AND COALESCE(rt.team_id, r.team_id) IS NOT NULL
          ) s
         GROUP BY s.teamId, s.cropId, s.productId
        """)
    List<PerfAggRow> aggregateByMonth(@Param("statMonth") String statMonth);

    /**
     * 当月采摘活动直接指定的班组（row43a：绩效计入活动直接班组）。
     *
     * <p>源 {@code t_plant_plant_activity JOIN t_plant_activity_team}：把每个当月采摘活动
     * （{@code pick_dest} 非空、plot 非空、{@code daily_weight>0}）自身中间表指定的班组，
     * 解析成 (plotId, cropId, teamId) 行（DISTINCT 去重）。service 层用它作为活动量平摊班组的
     * <b>首选集合</b>，为空时才兜底 {@link #selectHarvestTeamsByPlots} 地块采收班组——修复「活动直接
     * 指定班组但地块无 harvest 明细的整条被跳过、活动量丢失」。</p>
     *
     * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 无全局拦截器）。</p>
     *
     * @param statMonth 统计月份（"yyyy-MM"）
     * @return (plotId, cropId, teamId) 行；无数据返空 list
     */
    @Select("""
        SELECT DISTINCT a.plot_id AS plotId,
               a.crop_id AS cropId,
               at.team_id AS teamId
          FROM t_plant_plant_activity a
          JOIN t_plant_activity_team at
                 ON at.activity_id = a.id AND at.del_flag = '0' AND at.tenant_id = '1001'
         WHERE a.tenant_id = '1001'
           AND a.del_flag = '0'
           AND a.pick_dest IS NOT NULL
           AND a.daily_weight > 0
           AND DATE_FORMAT(a.activity_date, '%Y-%m') = #{statMonth}
        """)
    List<PlotCropTeamRow> selectActivityDirectTeamsByMonth(@Param("statMonth") String statMonth);

    /**
     * 当月采摘活动量按 作物 × 地块 聚合（row12：绩效并入采摘活动处理数据）。
     *
     * <p>源 {@code t_plant_plant_activity}：仅计 {@code pick_dest} 非空行（排除旧农事路径行防与
     * 毛菜过磅双算）。含销售去向（{@code pick_dest=sale}，plot_id 为空）行——按 (crop, NULL) 汇总，
     * service 层用其直接班组集合（junction）平摊并入班组绩效，与采摘明细页口径一致。
     * service 层按班组集合平摊后并入 {@link #aggregateByMonth} 结果。</p>
     *
     * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 无全局拦截器）。</p>
     *
     * @param statMonth 统计月份（"yyyy-MM"）
     * @return 聚合行（cropId / plotId / pickWeight）；无数据返空 list
     */
    @Select("""
        SELECT
            a.crop_id AS cropId,
            a.plot_id AS plotId,
            SUM(a.daily_weight) AS pickWeight
          FROM t_plant_plant_activity a
         WHERE a.tenant_id = '1001'
           AND a.del_flag = '0'
           AND a.pick_dest IS NOT NULL
           AND a.daily_weight > 0
           AND DATE_FORMAT(a.activity_date, '%Y-%m') = #{statMonth}
         GROUP BY a.crop_id, a.plot_id
        """)
    List<PerfActivityAggRow> selectActivityAggByMonth(@Param("statMonth") String statMonth);

    /**
     * 按地块集合解析 地块 × 作物 → 采收班组（row12 平摊归属）。
     *
     * <p>{@code t_plant_plant_details JOIN t_plant_details_team(role='harvest')}，DISTINCT 去重
     * （同 plot+crop 多计划/多明细共享班组时只计一次）。</p>
     *
     * @param plotIds 地块 ID 集合（非空）
     * @return (plotId, cropId, teamId) 行
     */
    @Select("""
        <script>
        SELECT DISTINCT d.plot_id AS plotId,
               d.crop_id AS cropId,
               dt.team_id AS teamId
          FROM t_plant_plant_details d
          JOIN t_plant_details_team dt
                 ON dt.detail_id = d.id AND dt.role = 'harvest' AND dt.del_flag = '0'
         WHERE d.del_flag = '0'
           AND d.plot_id IN
           <foreach collection="plotIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
        </script>
        """)
    List<PlotCropTeamRow> selectHarvestTeamsByPlots(@Param("plotIds") Collection<Long> plotIds);

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
            product_id AS productId,
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
         ORDER BY crop_id ASC, product_id ASC
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
     * 批量查询作物产品配置的绩效金额（V6 row20 结算取价用）。
     *
     * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 单租户，原生 SQL 不自动注入）。</p>
     *
     * @param cropIds 作物 id 集合（已 dedupe，非空）
     * @return key=cropId / productId / perfPrice（perfPrice 可能为 null = 该产品没填绩效金额）
     */
    @Select("<script>" +
        "SELECT crop_id AS cropId, product_id AS productId, perf_price AS perfPrice " +
        "FROM t_plant_crop_product " +
        "WHERE tenant_id = '1001' AND del_flag = '0' AND crop_id IN " +
        "<foreach collection='cropIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> " +
        "ORDER BY crop_id, id" +
        "</script>")
    List<Map<String, Object>> selectCropProductPrices(@Param("cropIds") Collection<Long> cropIds);

    /**
     * 批量查询产品名称（绩效详情「产品」列 enrich，避免 N+1）。
     *
     * @param productIds 产品 id 集合（已 dedupe，非空）
     * @return key=productId / productName
     */
    @Select("<script>" +
        "SELECT id AS productId, product_name AS productName " +
        "FROM t_warehouse_product_info " +
        "WHERE tenant_id = '1001' AND del_flag = '0' AND id IN " +
        "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>" +
        "</script>")
    List<Map<String, Object>> selectProductNames(@Param("productIds") Collection<Long> productIds);

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
     * 批量统计各班组当前成员数（列表「班组人数」列 enrich，rework 134/135）。
     *
     * <p>口径：{@code t_plant_work_people} 按 team_id 计活动成员行数（{@code del_flag='0'}）。
     * 当前快照成员数（成员表无历史快照），同班组各月行显示同一人数。一次 IN 取回避免 N+1。</p>
     *
     * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 单租户，原生 SQL 不自动注入）。</p>
     *
     * @param teamIds 班组 id 集合（已 dedupe，非空）
     * @return 命中的 (teamId / cnt) 行；未命中班组不返回（service 端默认 0）
     */
    @Select("<script>" +
        "SELECT team_id AS teamId, COUNT(*) AS cnt " +
        "FROM t_plant_work_people " +
        "WHERE tenant_id = '1001' AND del_flag = '0' AND team_id IN " +
        "<foreach collection='teamIds' item='tid' open='(' separator=',' close=')'>#{tid}</foreach>" +
        " GROUP BY team_id" +
        "</script>")
    List<Map<String, Object>> countTeamMembers(@Param("teamIds") Collection<Long> teamIds);

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
