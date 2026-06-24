package org.dromara.djs.plant.plan.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.plan.domain.PlantPlan;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanSummaryVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanVo;

import java.util.List;
import java.util.Map;

/**
 * 种植计划主表 Mapper（PLT-PLAN-001）。
 *
 * @author djs
 * @since PLT-PLAN-001
 */
public interface PlantPlanMapper extends BaseMapperPlus<PlantPlan, PlantPlanVo> {

    /**
     * 重算主表 4 个聚合字段（total_area / total_plot / earliest_harvestdate / last_harvestdate）。
     *
     * <p>实现：直接 UPDATE plan 用子查询聚合 details。details.del_flag='0' 过滤软删行。</p>
     *
     * <p>调用时机：</p>
     * <ul>
     *   <li>create：INSERT details 后</li>
     *   <li>update：details 增删改后</li>
     * </ul>
     *
     * @param planId 主表 id
     * @return 受影响行数（1 或 0）
     */
    @Update("UPDATE t_plant_plant_plan p "
        + "LEFT JOIN ("
        + "  SELECT plant_id, "
        + "         SUM(plot_area) AS area, "
        + "         COUNT(1) AS cnt, "
        + "         MIN(earliest_harvestdate) AS min_h, "
        + "         MAX(last_harvestdate) AS max_h "
        + "  FROM t_plant_plant_details "
        + "  WHERE plant_id = #{planId} AND del_flag = '0' "
        + "  GROUP BY plant_id"
        + ") d ON p.id = d.plant_id "
        + "SET p.total_area = COALESCE(d.area, 0), "
        + "    p.total_plot = COALESCE(d.cnt, 0), "
        + "    p.earliest_harvestdate = d.min_h, "
        + "    p.last_harvestdate = d.max_h, "
        + "    p.update_time = NOW() "
        + "WHERE p.id = #{planId} AND p.del_flag = '0'")
    int recalcAggregates(@Param("planId") Long planId);

    /**
     * 派生重算主表 plant_status（FIX-PLT-PLAN-STATUS-derive）。
     *
     * <p>口径（Kevin 拍定，按"完成种植"计数判定）：</p>
     * <ul>
     *   <li>明细全部完成（done = total）→ 'completed'（已完成）</li>
     *   <li>任一明细完成（done &gt; 0）→ 'ongoing'（进行中/已开始）</li>
     *   <li>0 完成（含无明细）→ 'pending'（待开始）</li>
     * </ul>
     *
     * <p>done = 子表 plant_status='completed' 计数；total = 子表有效明细行数。
     * 仅数"完成"，开工未完成（ongoing 明细但 0 completed）仍落 'pending'。
     * delayed（延期）此派生不覆盖，保持现状不自动设。</p>
     *
     * <p>实现：单 SQL UPDATE plan LEFT JOIN 子查询聚合 details，对齐 recalcAggregates 风格。
     * details / plan 均过滤 del_flag='0'。</p>
     *
     * <p>调用时机：finishPlant / startPlant 末尾，按受影响 planId 去重逐个调。</p>
     *
     * @param planId 主表 id
     * @return 受影响行数（1 或 0）
     */
    @Update("UPDATE t_plant_plant_plan p "
        + "LEFT JOIN ("
        + "  SELECT plant_id, "
        + "         COUNT(1) AS total, "
        + "         SUM(plant_status = 'completed') AS done "
        + "  FROM t_plant_plant_details "
        + "  WHERE plant_id = #{planId} AND del_flag = '0' "
        + "  GROUP BY plant_id"
        + ") d ON p.id = d.plant_id "
        + "SET p.plant_status = CASE "
        + "    WHEN d.total IS NULL OR d.total = 0 THEN 'pending' "
        + "    WHEN d.done = d.total THEN 'completed' "
        + "    WHEN d.done > 0 THEN 'ongoing' "
        + "    ELSE 'pending' END, "
        + "    p.update_time = NOW() "
        + "WHERE p.id = #{planId} AND p.del_flag = '0'")
    int recalcPlanStatus(@Param("planId") Long planId);

    /**
     * 跨模块只读：聚合"进行中"种植计划给需求确认 SummaryBar 用
     * （DJS-FIX-ADMIN-W22-003）。
     *
     * <p>"进行中"语义：{@code plant_status IN ('pending','ongoing')}。聚合方式：</p>
     * <ul>
     *   <li>{@code plotCount} = COUNT(DISTINCT d.plot_id)（同地块在多月/多期只算一次）</li>
     *   <li>{@code expectedYieldKg} = SUM(COALESCE(d.expected_yield,0)) - SUM(COALESCE(d.actual_yield,0))，
     *       负值兜 0</li>
     *   <li>{@code earliestPickDate} = MIN(d.earliest_harvestdate)（NULL safe）</li>
     * </ul>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户）。
     * 没有数据时返一个零值 VO 而非 null（service 层兜底）。</p>
     */
    @Select("""
        SELECT
            COUNT(DISTINCT d.plot_id) AS plotCount,
            GREATEST(
                COALESCE(SUM(d.expected_yield), 0) - COALESCE(SUM(d.actual_yield), 0),
                0
            ) AS expectedYieldKg,
            MIN(d.earliest_harvestdate) AS earliestPickDate
          FROM t_plant_plant_plan p
          JOIN t_plant_plant_details d
            ON d.plant_id = p.id
           AND d.del_flag = '0'
           AND d.tenant_id = p.tenant_id
         WHERE p.del_flag = '0'
           AND p.tenant_id = '1001'
           AND p.plant_status IN ('pending','ongoing')
        """)
    PlantPlanSummaryVo selectDemandSummary();

    /**
     * 列表聚合 enrich（FIX-PLT-AD-PLAN-001）：按 planId 批量聚合明细。
     *
     * <p>每行 key=planId(Long) / expectedYield(BigDecimal SUM) / actualYield(BigDecimal SUM)
     * / finishedPlot(Long COUNT plant_status='completed')
     * / earliestBegindate(DATE MIN) / lastBegindate(DATE MAX)
     * / plantMonth(Integer，最早开始那条明细的种植月份)
     * / plantPeriod(String，最早开始那条明细的上中下旬 05/15/25)。</p>
     *
     * <p>plantMonth/plantPeriod 取"最早开始日期"对应明细：对 {@code CONCAT(LPAD(plant_month,2,'0'), plant_period)}
     * 取 MIN（月份补零后拼旬别，字典序 == 时间序），再拆出月份与旬别，供列表"计划种植日期"列展示如「6月中旬」。</p>
     *
     * <p>finishedPlot 语义：实施 finishPlant（pending→ongoing→completed 状态机的"种植完成"动作）后，
     * plant_status='completed' 即表示该地块种植已完成，故 {@code COUNT(plant_status='completed')}
     * 对齐 {@code PlantPlanVo} 的"已完成种植地数量"（finishedPlot），无需改 SQL。</p>
     *
     * <p>计划开始日期 = {@code DATE(CONCAT(plan_year,'-',LPAD(plant_month,2,'0'),'-',plant_period))}
     * （plant_period 取值 05/15/25 恰为合法日，无需 periodToDay 映射）。
     * del_flag='0' 过滤软删；tenant_id='1001'（V1 单租户）。</p>
     *
     * @param planIds 当前分页页的计划 id 列表（空列表外层不调）
     */
    @Select("""
        <script>
        SELECT
            d.plant_id AS planId,
            COALESCE(SUM(d.expected_yield), 0) AS expectedYield,
            COALESCE(SUM(d.actual_yield), 0) AS actualYield,
            SUM(CASE WHEN d.plant_status = 'completed' THEN 1 ELSE 0 END) AS finishedPlot,
            MIN(DATE(CONCAT(p.plan_year, '-', LPAD(d.plant_month, 2, '0'), '-', d.plant_period))) AS earliestBegindate,
            MAX(DATE(CONCAT(p.plan_year, '-', LPAD(d.plant_month, 2, '0'), '-', d.plant_period))) AS lastBegindate,
            CAST(SUBSTRING(MIN(CONCAT(LPAD(d.plant_month, 2, '0'), d.plant_period)), 1, 2) AS UNSIGNED) AS plantMonth,
            SUBSTRING(MIN(CONCAT(LPAD(d.plant_month, 2, '0'), d.plant_period)), 3, 2) AS plantPeriod
          FROM t_plant_plant_details d
          JOIN t_plant_plant_plan p
            ON p.id = d.plant_id
           AND p.del_flag = '0'
           AND p.tenant_id = d.tenant_id
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.plant_id IN
           <foreach collection="planIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
         GROUP BY d.plant_id
        </script>
        """)
    List<Map<String, Object>> selectListAggregates(@Param("planIds") List<Long> planIds);

    /**
     * KPI 卡：地块维度统计（FIX-PLT-AD-PLAN-001）。
     *
     * <p>每行单条：idlePlot(plot_status=1) / plantedPlot(plot_status IN (2,3))。
     * tenant_id='1001'（V1 单租户），del_flag='0'。</p>
     */
    @Select("""
        SELECT
            COALESCE(SUM(CASE WHEN plot_status = 1 THEN 1 ELSE 0 END), 0) AS idlePlot,
            COALESCE(SUM(CASE WHEN plot_status IN (2, 3) THEN 1 ELSE 0 END), 0) AS plantedPlot
          FROM t_plant_plot_info
         WHERE del_flag = '0'
           AND tenant_id = '1001'
        """)
    Map<String, Object> selectPlotStatusStats();

    /**
     * KPI 卡：计划维度统计（FIX-PLT-AD-PLAN-001）。
     *
     * <p>仅进行中计划（plant_status IN ('pending','ongoing')）。每行单条：
     * plannedPlot(COUNT DISTINCT details.plot_id) / detailRows(明细总行数，算频次用)
     * / cropVarietyCount(COUNT DISTINCT plan.crop_id)。
     * tenant_id='1001'（V1 单租户），del_flag='0'。</p>
     */
    @Select("""
        SELECT
            COUNT(DISTINCT d.plot_id) AS plannedPlot,
            COUNT(d.id) AS detailRows,
            COUNT(DISTINCT p.crop_id) AS cropVarietyCount
          FROM t_plant_plant_plan p
          JOIN t_plant_plant_details d
            ON d.plant_id = p.id
           AND d.del_flag = '0'
           AND d.tenant_id = p.tenant_id
         WHERE p.del_flag = '0'
           AND p.tenant_id = '1001'
           AND p.plant_status IN ('pending', 'ongoing')
        """)
    Map<String, Object> selectPlanDetailStats();
}
