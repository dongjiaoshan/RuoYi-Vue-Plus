package org.dromara.djs.plant.pick.service;

import org.dromara.djs.plant.pick.domain.vo.PickTaskVo;

import java.util.List;

/**
 * mp 端采摘任务 Service（PLT-PLAN-002）。
 *
 * <p>mp 工人浏览待采摘 / 进行中的地块任务，**不录入**（录入在 D12 PLT-PICK-001）。</p>
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
}
