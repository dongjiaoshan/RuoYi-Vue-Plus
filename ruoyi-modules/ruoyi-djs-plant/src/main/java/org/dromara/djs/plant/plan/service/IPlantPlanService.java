package org.dromara.djs.plant.plan.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.plan.domain.bo.PlantPlanCreateBo;
import org.dromara.djs.plant.plan.domain.bo.PlantPlanUpdateBo;
import org.dromara.djs.plant.plan.domain.query.PlantPlanQuery;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanDetailVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanGanttVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanSummaryVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanVo;
import org.dromara.djs.plant.plan.domain.vo.PlotByZoneVo;

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
     */
    List<PlotByZoneVo> listAvailablePlots();

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
}
