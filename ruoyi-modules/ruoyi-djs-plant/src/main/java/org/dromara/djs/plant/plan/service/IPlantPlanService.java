package org.dromara.djs.plant.plan.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.plan.domain.bo.PlantFinishBo;
import org.dromara.djs.plant.plan.domain.bo.PlantPlanCreateBo;
import org.dromara.djs.plant.plan.domain.bo.PlantPlanUpdateBo;
import org.dromara.djs.plant.plan.domain.bo.PlantStartBo;
import org.dromara.djs.plant.plan.domain.query.PlantPlanQuery;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanDetailVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanGanttVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanStatsVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanSummaryVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanVo;
import org.dromara.djs.plant.plan.domain.vo.PlotByZoneVo;
import org.dromara.djs.plant.plan.domain.vo.PlotYearPlanRowVo;

import java.util.Collection;
import java.util.List;

/**
 * 种植计划 Service（PLT-PLAN-001）。
 *
 * <p>核心职责：3 步向导提交 / 详情 / 编辑 / 软删 / 甘特图数据 / 向导 step3 用地块清单。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
public interface IPlantPlanService {

    TableDataInfo<PlantPlanVo> queryPageList(PlantPlanQuery query, PageQuery pageQuery);

    List<PlantPlanVo> queryList(PlantPlanQuery query);

    PlantPlanDetailVo queryDetailById(Long id);

    /**
     * 向导提交：INSERT 主表 + batch INSERT details + recalc 聚合字段，单事务。
     *
     * @return 新建主表 id
     */
    Long createByBo(PlantPlanCreateBo bo);

    /**
     * 编辑：plant_status='ongoing' 时禁改 cropId；details 已开始执行行不允许删/改 plot/month/period。
     *
     * @return 受影响主表行数（1 或 0）
     */
    int updateByBo(PlantPlanUpdateBo bo);

    /**
     * 软删主表：要求关联 details 全部 begin_actualdate IS NULL（无开始执行行）。
     *
     * @return 受影响主表行数
     */
    int deleteWithValidByIds(Collection<Long> ids);

    /**
     * 甘特图数据：主表摘要 + 每地块 1 行（4 时间字段）。
     */
    PlantPlanGanttVo getGantt(Long planId);

    /**
     * 向导 step3 用：按片区分组返回所有地块。
     *
     * @param planYear 计划年份（非空时回填每块地的「当年轮作次数」rotationCount；为空则不回填）
     */
    List<PlotByZoneVo> listAvailablePlots(Integer planYear);

    /**
     * 向导 step3「查看」弹框：某地块在某年份的计划实际数据列表（测试反馈 row27）。
     *
     * @param plotId   地块 id
     * @param planYear 计划年份
     * @return 该地块当年种植明细行列表（无数据返空列表）
     */
    List<PlotYearPlanRowVo> getPlotYearPlanRows(Long plotId, Integer planYear);

    /**
     * mp 播种「开始种植」开工（FIX-PLT-MP-SEED-001 #5）。
     *
     * <p>单事务：仅 {@code begin_actualdate IS NULL}（尚未开工）的明细可开工，批量回写
     * {@code begin_actualdate=入参日期} + {@code plant_by=入参班组} + {@code plant_status='ongoing'}；
     * 并同步关联地块 {@code t_plant_plot_info.plot_status=2}（种植中）。所有 detailIds 必须属当前租户。</p>
     *
     * @param bo 开工入参（detailIds + 开始日期 + 班组）
     * @return 实际开工的明细行数（已开工的明细被跳过，不计入）
     */
    int startPlant(PlantStartBo bo);

    /**
     * mp 播种「种植完成」收工。
     *
     * <p>单事务：仅 {@code plant_status='ongoing'} 且 {@code begin_actualdate IS NOT NULL} 且
     * {@code end_actualdate IS NULL}（已开工尚未完成）的明细可完成，批量回写
     * {@code plant_status='completed'} + {@code end_actualdate=入参日期}。不动地块
     * {@code t_plant_plot_info.plot_status}（保持 2 种植，待采摘开始才转 3）。所有 detailIds 必须属当前租户。</p>
     *
     * @param bo 收工入参（detailIds + 结束日期）
     * @return 实际完成的明细行数（非进行中 / 已完成的明细被跳过，不计入）
     */
    int finishPlant(PlantFinishBo bo);

    /**
     * 育苗移栽落地：为每个目标地块新增一条「满种」明细（PLT-TRANSPLANT-REDO-001）。
     *
     * <p>由 {@code FarmRecordsServiceImpl} 在移栽累计 100%（或手动结束移栽）的同事务内调用。每个目标地块建一条
     * {@code plant_status='completed'}（产出期）+ {@code harvest_status='pending'}（采摘改在大田）+
     * {@code transplant_adjusted=1} 的明细：日期（begin_actualdate / 月 / 旬 / earliest / last_harvestdate）
     * 沿用源育苗明细——种植/采收时间不变、采摘期含育苗时长；面积 / 预计产量按目标地块重取。落地前对新明细跑采摘
     * 区间重叠校验（目标须在计划时间窗口内无冲突计划）。目标已有本计划明细则跳过（幂等，防并发双落地）。落地后
     * 目标地块 {@code plot_status=2}（种植）并 recalc 计划聚合 / 状态。</p>
     *
     * @param planId        种植计划 id（源育苗明细所属计划）
     * @param sourcePlotId  源育苗地块 id
     * @param cropId        作物 id
     * @param targetPlotIds 目标地块 id 列表（来自移栽记录 DISTINCT transplant_plot）
     * @return 实际新增的目标满种明细行数（幂等跳过的不计入）
     */
    int materializeTransplantTargets(Long planId, Long sourcePlotId, Long cropId, java.util.List<Long> targetPlotIds);

    /**
     * 跨模块薄壳：聚合"进行中（pending/ongoing）"种植计划摘要给需求确认 SummaryBar 用
     * （DJS-FIX-ADMIN-W22-003 蔬菜业态）。
     *
     * <p>仅返进行中计划，已 completed/delayed 不计；4 字段中 currentStockKg 由 warehouse 端
     * 用 location_stock + product_info(belong_type='vegetable') 聚合，不在本 VO。</p>
     *
     * @return 聚合 VO（无数据时各字段返 0 / null，永远不返 null 对象）
     */
    PlantPlanSummaryVo aggregateForDemandSummary();

    /**
     * 列表顶部 5 KPI 卡（FIX-PLT-AD-PLAN-001 · row37，按计划年份过滤）：
     * 当前空地块数 / 当年已种植地块数 / 当年计划种植地块数 / 当年计划地块使用频次 /
     * 当年计划种植作物品种数。
     *
     * <p>① 空地块数取 plot_info 当前状态（不受年份影响）；②③⑤ 取 {@code plan_year=planYear}
     * 计划聚合明细；④ = ③ / 地块总数（2 位小数）。V1 单租户 '1001'。</p>
     *
     * @param planYear 计划年份（null 时 service 兜底当年）
     * @return KPI 聚合 VO（无数据返零值，永不返 null）
     */
    PlantPlanStatsVo getPlanStats(Integer planYear);
}
