package org.dromara.djs.plant.pick.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
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
     * @param isPick is_pick 过滤值（可空，空时默认 2=普通采收；「采摘活动管理」页传 1 = 游客采摘活动）
     */
    @GetMapping("/cropTasks")
    public R<List<PickCropTaskVo>> cropTasks(@RequestParam(required = false) Long zoneId,
                                             @RequestParam(required = false) Integer isPick) {
        return R.ok(appletPickService.listCropTasks(zoneId, isPick));
    }

    /**
     * 采收作物详情下「N 张地块卡」（FIX-PLT-MP-PICK-001 #3）：该作物（+ 计划）下逐地块卡。
     *
     * <p>cropId / planId 以 String 接收后自行解析（前端 mp 传 snowflake string）：空 / 非数字直接抛
     * {@link ServiceException} 给明确友好提示，避免 String→Long 绑定失败触发框架级 MethodArgumentTypeMismatch
     * 导致 mp 「发生未知异常」（采摘活动管理详情 / 采收详情两页共用本端点）。</p>
     *
     * @param planId 计划 id（可空字符串）
     * @param cropId 作物 id（必填字符串）
     * @param isPick is_pick 过滤值（可空，空时默认 2=普通采收；「采摘活动管理」页传 1 = 游客采摘活动）
     */
    @GetMapping("/cropPlots")
    public R<List<PickTaskVo>> cropPlots(@RequestParam(required = false) String planId,
                                         @RequestParam(required = false) String cropId,
                                         @RequestParam(required = false) Integer isPick) {
        return R.ok(appletPickService.listCropPlots(parsePlanId(planId), parseCropId(cropId), isPick));
    }

    /**
     * 作物级「是否已录入完成/结算」（row223 边界缺口修复）：mp 采摘活动详情头卡「采摘重量录入」按钮
     * 据此从服务端恢复持久置灰态。
     *
     * <p>与 {@link #cropPlots} 不同，本判定<b>不带列表日期过滤</b>（无当月采摘窗口相交、无「completed 地块
     * 只在 end_harvestdate==today 展示」），故跨完成窗口后重进——那些 completed 行已被列表日期驱逐、
     * plots 变空——仍据表内 {@code pick_settle_round>0} 行返回 true，按钮保持置灰不复活。</p>
     *
     * @param cropId 作物 id（必填字符串，空 / 非数字 → 明确友好 ServiceException）
     * @param isPick is_pick 过滤值（可空，空时默认 2=普通采收；「采摘活动管理」页传 1 = 游客采摘活动，
     *               与 {@link #cropPlots} 同 isPick 契约）
     */
    @GetMapping("/cropSettled")
    public R<Boolean> cropSettled(@RequestParam(required = false) String cropId,
                                  @RequestParam(required = false) Integer isPick) {
        return R.ok(appletPickService.isCropSettled(parseCropId(cropId), isPick));
    }

    /** 必填作物 id 解析：空 / 非数字 → 明确友好 ServiceException（不让框架抛类型不匹配）。 */
    private Long parseCropId(String cropId) {
        if (cropId == null || cropId.isBlank()) {
            throw new ServiceException("作物 id 不能为空");
        }
        try {
            return Long.parseLong(cropId.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("作物 id 非法：" + cropId);
        }
    }

    /** 可空计划 id 解析：空 → null（不过滤计划）；非数字 → 明确友好 ServiceException。 */
    private Long parsePlanId(String planId) {
        if (planId == null || planId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(planId.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("计划 id 非法：" + planId);
        }
    }
}
