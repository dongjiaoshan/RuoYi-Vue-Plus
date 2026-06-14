package org.dromara.djs.plant.pick.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.pick.domain.bo.PickAdjustBatchBo;
import org.dromara.djs.plant.pick.domain.bo.PickSetScheduleBo;
import org.dromara.djs.plant.pick.domain.bo.PickToggleActivityBo;
import org.dromara.djs.plant.pick.domain.query.PickPlanQuery;
import org.dromara.djs.plant.pick.domain.vo.PickPlanGroupVo;
import org.dromara.djs.plant.pick.service.IPickPlanService;
import org.dromara.djs.plant.plan.domain.vo.PlantDetailsVo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采摘计划 Controller（admin，PLT-PLAN-002）。
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /list}：按作物聚合采摘计划列表（doc/10 §F-PLT-05 入口 1）</li>
 *   <li>{@code GET /{planId}/{cropId}/details}：调整页拉行（按计划 + 作物）</li>
 *   <li>{@code GET /crop/{cropId}/details}：调整页拉行（纯作物聚合，跨多计划；列表已重构为纯作物聚合，UI 侧不再持有单一 planId）</li>
 *   <li>{@code PUT /adjust}：批量调整 plant_details 的 4 时间字段 + is_pick + harvest_by</li>
 *   <li>{@code PUT /setSchedule}：单行设置计划最早 / 最晚采摘日期（按行 id 定位）</li>
 *   <li>{@code PUT /toggleActivity}：单行设为 / 取消采摘活动（按行 id 定位，改 is_pick）</li>
 * </ul>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/pick/plan")
public class PickPlanController extends BaseController {

    private final IPickPlanService pickPlanService;

    @SaCheckPermission("djs:plant:pick:list")
    @GetMapping("/list")
    public R<List<PickPlanGroupVo>> list(PickPlanQuery query) {
        return R.ok(pickPlanService.listByCrop(query));
    }

    @SaCheckPermission("djs:plant:pick:list")
    @GetMapping("/{planId}/{cropId}/details")
    public R<List<PlantDetailsVo>> details(@PathVariable Long planId, @PathVariable Long cropId) {
        return R.ok(pickPlanService.listDetailsByPlanCrop(planId, cropId));
    }

    /**
     * 调整页拉行（纯作物聚合）：列表已重构为按作物聚合（跨多 plant_plan），调整抽屉只持有 cropId、
     * 拿不到单一 planId。本端点按 cropId 跨计划聚合返回该作物名下全部 plant_details 行。
     *
     * <p>定值段 {@code /crop/} 优先级高于 {@code /{planId}/} 占位段，Spring 正确分流、不与旧端点冲突。</p>
     */
    @SaCheckPermission("djs:plant:pick:list")
    @GetMapping("/crop/{cropId}/details")
    public R<List<PlantDetailsVo>> detailsByCrop(@PathVariable Long cropId) {
        return R.ok(pickPlanService.listDetailsByCrop(cropId));
    }

    @SaCheckPermission("djs:plant:pick:adjust")
    @Log(title = "种植-采摘计划-调整", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/adjust")
    public R<Integer> adjust(@Validated @RequestBody PickAdjustBatchBo bo) {
        return R.ok(pickPlanService.adjustDetails(bo));
    }

    /**
     * 「设置计划」单行：按 plant_details.id 更新计划最早 / 最晚采摘日期。
     *
     * <p>列表已重构为纯作物聚合（UI 不持有单一 planId），按行 id 直接定位。</p>
     */
    @SaCheckPermission("djs:plant:pick:adjust")
    @Log(title = "种植-采摘计划-设置计划", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/setSchedule")
    public R<Integer> setSchedule(@Validated @RequestBody PickSetScheduleBo bo) {
        return R.ok(pickPlanService.setSchedule(bo));
    }

    /**
     * 「设为/取消采摘活动」单行：按 plant_details.id 更新 is_pick（1=是 / 2=否）。
     */
    @SaCheckPermission("djs:plant:pick:adjust")
    @Log(title = "种植-采摘计划-设为采摘活动", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/toggleActivity")
    public R<Integer> toggleActivity(@Validated @RequestBody PickToggleActivityBo bo) {
        return R.ok(pickPlanService.toggleActivity(bo));
    }
}
