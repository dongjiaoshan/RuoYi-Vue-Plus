package org.dromara.djs.warehouse.demand.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.domain.bo.AssignPigBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandExplainBo;
import org.dromara.djs.warehouse.demand.domain.vo.DispatchHomeVo;
import org.dromara.djs.warehouse.demand.domain.vo.DispatchListItemVo;
import org.dromara.djs.warehouse.demand.domain.vo.DispatchStatsVo;
import org.dromara.djs.warehouse.demand.service.IAppletDemandService;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.demand.service.IDemandStatusService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 小程序需求调度 Controller（WMS-DEMAND-002 调度员视角）。
 *
 * <p>URL 前缀 {@code /djs/applet/warehouse/demand}（与 {@link AppletDemandPickerController} 同前缀、
 * 路径不撞）。mp 仓库管理员（调度员）首页 / 白条蔬菜列表 / 确认 / 分猪 / 改备注 / 统计。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET  /home} — 调度首页 KPI + 入口卡数字</li>
 *   <li>{@code GET  /white-bar/list} — 白条需求列表（按 storeId 分组前的扁平列表）</li>
 *   <li>{@code GET  /vegetable/list} — 蔬菜需求列表</li>
 *   <li>{@code POST /{id}/confirm} — 确认需求（复用状态机 CONFIRM 事件）</li>
 *   <li>{@code POST /{id}/assign-pig} — 分配猪只（复用 admin assignPigs）</li>
 *   <li>{@code POST /{id}/explain} — 改需求说明 demand_explain</li>
 *   <li>{@code GET  /stats/summary?date=} — 统计页 5 dashboard 聚合数字</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin}（V1 mock auth + ADR-0003，mp 调度员角色可用，不查 perm）。
 * V1.5 升 {@code @SaCheckPermission("djs:applet:warehouse:demand:dispatch")}。</p>
 *
 * <h2>状态机复用</h2>
 * <p>confirm 走 {@link IDemandStatusService#transition}（含白条 CONFIRM 前置校验 + audit_history append）；
 * assign-pig 走 {@link IDemandManageService#assignPigs}——<b>不在本 controller 复写状态机/校验逻辑</b>。</p>
 *
 * @author djs
 * @since WMS-DEMAND-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/warehouse/demand")
public class AppletDemandController {

    private final IAppletDemandService appletDemandService;

    private final IDemandManageService demandManageService;

    private final IDemandStatusService demandStatusService;

    /** 调度首页 KPI + 入口卡数字（当日口径）。 */
    @SaCheckLogin
    @GetMapping("/home")
    public R<DispatchHomeVo> home() {
        return R.ok(appletDemandService.dispatchHome());
    }

    /** 白条需求列表（mp 调度员视角，service enrich storeName + 已指定猪只数）。 */
    @SaCheckLogin
    @GetMapping("/white-bar/list")
    public R<List<DispatchListItemVo>> whiteBarList() {
        return R.ok(appletDemandService.dispatchList("white_bar"));
    }

    /** 蔬菜需求列表（mp 调度员视角，service enrich storeName）。 */
    @SaCheckLogin
    @GetMapping("/vegetable/list")
    public R<List<DispatchListItemVo>> vegetableList() {
        return R.ok(appletDemandService.dispatchList("vegetable"));
    }

    /**
     * 确认需求（CONFIRM 事件：SUBMITTED → CONFIRMED）。
     *
     * <p>复用 admin 端状态机；白条业态在 transition 内部强制校验已指定猪只，未指定则抛业务异常。</p>
     *
     * @param id     需求 ID
     * @param remark 确认备注（可选，写入 audit_history）
     */
    @SaCheckLogin
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id, @RequestParam(required = false) String remark) {
        demandStatusService.transition(id, DemandEvent.CONFIRM, null, remark);
        return R.ok();
    }

    /**
     * 分配猪只（白条业态，body 含 earNos 清单）。
     *
     * <p>复用 admin {@link IDemandManageService#assignPigs}——含白条业态校验 + 状态校验
     * + UNIQUE 兜底；耳号全链路 string（跨层契约 §1）。</p>
     *
     * @param id 需求 ID
     * @param bo 耳号清单
     */
    @SaCheckLogin
    @PostMapping("/{id}/assign-pig")
    public R<Integer> assignPig(@PathVariable Long id, @Valid @RequestBody AssignPigBo bo) {
        return R.ok(demandManageService.assignPigs(id, bo));
    }

    /**
     * 改需求说明（demand_explain 自由备注，最长 500）。
     *
     * @param id 需求 ID
     * @param bo 说明文本
     */
    @SaCheckLogin
    @PostMapping("/{id}/explain")
    public R<Void> explain(@PathVariable Long id, @Valid @RequestBody DemandExplainBo bo) {
        demandManageService.updateExplain(id, bo.getDemandExplain());
        return R.ok();
    }

    /**
     * 统计页 5 dashboard 聚合数字。
     *
     * @param date 统计日期（yyyy-MM-dd）；不传取今天
     */
    @SaCheckLogin
    @GetMapping("/stats/summary")
    public R<DispatchStatsVo> statsSummary(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return R.ok(appletDemandService.dispatchStats(date));
    }
}
