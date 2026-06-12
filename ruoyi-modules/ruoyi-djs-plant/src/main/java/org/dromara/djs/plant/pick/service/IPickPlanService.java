package org.dromara.djs.plant.pick.service;

import org.dromara.djs.plant.pick.domain.bo.PickAdjustBatchBo;
import org.dromara.djs.plant.pick.domain.query.PickPlanQuery;
import org.dromara.djs.plant.pick.domain.vo.PickPlanGroupVo;
import org.dromara.djs.plant.plan.domain.vo.PlantDetailsVo;

import java.util.List;

/**
 * 采摘计划 Service（PLT-PLAN-002）。
 *
 * <p>核心：复用 D9 落地的 {@code t_plant_plant_details}，按计划×作物聚合 + 按地块调整。
 * 本接口**不**新建主表，仅围绕已有 plant_details 提供采摘视角的查询和调整。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
public interface IPickPlanService {

    /**
     * 按作物聚合采摘计划列表（对齐原型按作物维度）。
     *
     * <p>doc/10 §F-PLT-05 admin 入口 1：按作物聚合显示。</p>
     */
    List<PickPlanGroupVo> listByCrop(PickPlanQuery query);

    /**
     * 详情：指定计划下指定作物的所有 plant_details 明细（含 enrich plot/team 名称）。
     *
     * <p>admin 调整页 step1：拉行作为表格行。</p>
     */
    List<PlantDetailsVo> listDetailsByPlanCrop(Long planId, Long cropId);

    /**
     * 按计划批量调整 plant_details 的 4 时间字段 + is_pick + harvest_by。
     *
     * <p>校验：</p>
     * <ul>
     *   <li>同事务批量 UPDATE</li>
     *   <li>每行 row.id 必须属于 (plantId, cropId)（防越权改其他计划的明细）</li>
     *   <li>beginHarvestdate ≤ endHarvestdate（若两个都填）</li>
     *   <li>isPick 仅允许 1 或 2</li>
     * </ul>
     *
     * @return 实际 UPDATE 行数
     */
    int adjustDetails(PickAdjustBatchBo bo);
}
