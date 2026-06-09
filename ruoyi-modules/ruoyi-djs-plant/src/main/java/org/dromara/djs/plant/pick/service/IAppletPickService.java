package org.dromara.djs.plant.pick.service;

import org.dromara.djs.plant.pick.domain.bo.PickSubmitBo;
import org.dromara.djs.plant.pick.domain.vo.PickCropTaskVo;
import org.dromara.djs.plant.pick.domain.vo.PickSummaryVo;
import org.dromara.djs.plant.pick.domain.vo.PickTaskVo;

import java.util.List;

/**
 * mp 端采摘任务 Service（PLT-PLAN-002 只读 + PLT-PICK-001 录入闭环）。
 *
 * <p>mp 工人浏览待采摘 / 进行中的地块任务并录采收。游客采摘活动（{@code is_pick=1}）全程排除，
 * 工人只录普通采收为销售。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
public interface IAppletPickService {

    /**
     * 查询当前用户班组的待采摘 / 采摘中任务。
     *
     * <p>V1 简化：mp 用户无班组绑定 → 返回所有 {@code harvest_status IN ('pending','picking')}
     * 且 {@code begin_harvestdate / earliest_harvestdate} 不超过 7 天的明细。
     * V2 接入"用户-班组"映射后改为 {@code WHERE harvest_by = userTeamId}。</p>
     *
     * @param status 可选过滤，逗号分隔多状态，例 {@code "pending,picking"}；
     *               为空时默认 pending+picking
     * @return mp 卡片列表（含 enrich plot/crop/team 名称）
     */
    List<PickTaskVo> listMyTasks(String status);

    /**
     * 单个任务详情。
     *
     * @param id plant_details.id
     */
    PickTaskVo getTaskDetail(Long id);

    /**
     * 工人采收录入（PLT-PICK-001）。
     *
     * <p>单事务：SELECT FOR UPDATE plant_details → 累加 {@code actual_yield} / 首次回填
     * {@code begin_harvestdate} / 流转 {@code harvest_status}（pending→picking）；
     * {@code finish=true} 时置 {@code harvest_status='completed'} + {@code end_actualdate=NOW}
     * + {@code end_harvestdate} + 计算 {@code average_yield}；INSERT 一行 {@code t_plant_farm_records}
     * （{@code farm_type='harvest_activity'}）。<b>不动 {@code is_pick}</b>（游客采摘 flag 由 admin 维护）。
     * 超期 / 提前采仅前端 warning，后端不拦。</p>
     *
     * @param bo 采收录入入参
     */
    void submitPick(PickSubmitBo bo);

    /**
     * mp 采收概览 KPI（PLT-PICK-001，按作物聚合，排除游客采摘）。
     *
     * @return 完成率 / 今日采摘品种数 / 今日采摘重量
     */
    PickSummaryVo todaySummary();

    /**
     * 采收列表「作物维度」任务卡（FIX-PLT-MP-PICK-001 #3）。
     *
     * <p>按片区分组（{@code zoneId} 可空 = 全部片区）→ 作物维度聚合，排除游客采摘活动（is_pick=1）。
     * 每卡含完成率 / 开始-最晚日期 / 预计-已采产量 / 地块数。</p>
     *
     * @param zoneId 片区 id（可空，空则不按片区过滤）
     * @return 作物维度卡列表（无数据返空列表）
     */
    List<PickCropTaskVo> listCropTasks(Long zoneId);

    /**
     * 采收作物详情下「N 张地块卡」（FIX-PLT-MP-PICK-001 #3）。
     *
     * <p>该作物（+ 计划）下逐地块卡，复用 {@link PickTaskVo}（含实际开始/结束时间）。排除游客采摘。</p>
     *
     * @param planId 计划 id（可空，空则该作物全部计划的地块）
     * @param cropId 作物 id（必填）
     * @return 地块卡列表（无数据返空列表）
     */
    List<PickTaskVo> listCropPlots(Long planId, Long cropId);
}
