package org.dromara.djs.plant.pick.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.pick.domain.bo.PickSubmitBo;
import org.dromara.djs.plant.pick.domain.vo.PickCropTaskVo;
import org.dromara.djs.plant.pick.domain.vo.PickSummaryVo;
import org.dromara.djs.plant.pick.domain.vo.PickTaskVo;
import org.dromara.djs.plant.pick.service.IAppletPickService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * mp 端采摘任务 Controller（PLT-PLAN-002）。
 *
 * <p>浏览（PLT-PLAN-002）+ 采收录入闭环（PLT-PICK-001）。</p>
 *
 * <h2>4 端点</h2>
 * <ul>
 *   <li>{@code GET  /djs/applet/plant/pick/myTasks?status=pending,picking}：待采摘 / 采摘中任务列表</li>
 *   <li>{@code GET  /djs/applet/plant/pick/detail/{id}}：任务详情</li>
 *   <li>{@code POST /djs/applet/plant/pick/submit}：采收录入（累加 actual_yield + 流转 harvest_status）</li>
 *   <li>{@code GET  /djs/applet/plant/pick/todaySummary}：采收概览 KPI（按作物聚合）</li>
 * </ul>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Validated
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/plant/pick")
public class AppletPickController extends BaseController {

    private final IAppletPickService appletPickService;

    @SaCheckPermission("djs:applet:plant:pick:list")
    @GetMapping("/myTasks")
    public R<List<PickTaskVo>> myTasks(@RequestParam(required = false) String status) {
        return R.ok(appletPickService.listMyTasks(status));
    }

    @SaCheckPermission("djs:applet:plant:pick:detail")
    @GetMapping("/detail/{id}")
    public R<PickTaskVo> detail(@PathVariable Long id) {
        return R.ok(appletPickService.getTaskDetail(id));
    }

    /**
     * 采收录入（PLT-PICK-001）：累加 actual_yield + 流转 harvest_status + INSERT 农事记录。
     */
    @SaCheckPermission("djs:applet:plant:pick:submit")
    @PostMapping("/submit")
    public R<Void> submit(@Valid @RequestBody PickSubmitBo bo) {
        appletPickService.submitPick(bo);
        return R.ok();
    }

    /**
     * 采收概览 KPI（PLT-PICK-001，按作物聚合）：完成率 / 今日采摘品种数 / 今日采摘重量。
     */
    @SaCheckPermission("djs:applet:plant:pick:summary")
    @GetMapping("/todaySummary")
    public R<PickSummaryVo> todaySummary() {
        return R.ok(appletPickService.todaySummary());
    }

    /**
     * 采收列表「作物维度」任务卡（FIX-PLT-MP-PICK-001 #3）：按片区分组 → 作物聚合。
     *
     * @param zoneId 片区 id（可空 = 全部片区）
     */
    @GetMapping("/cropTasks")
    public R<List<PickCropTaskVo>> cropTasks(@RequestParam(required = false) Long zoneId) {
        return R.ok(appletPickService.listCropTasks(zoneId));
    }

    /**
     * 采收作物详情下「N 张地块卡」（FIX-PLT-MP-PICK-001 #3）：该作物（+ 计划）下逐地块卡。
     *
     * @param planId 计划 id（可空）
     * @param cropId 作物 id（必填）
     */
    @GetMapping("/cropPlots")
    public R<List<PickTaskVo>> cropPlots(@RequestParam(required = false) Long planId, @RequestParam Long cropId) {
        return R.ok(appletPickService.listCropPlots(planId, cropId));
    }
}
