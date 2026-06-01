package org.dromara.djs.warehouse.dashboard.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;
import org.dromara.djs.warehouse.dashboard.service.IWarehouseDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序仓库看板 Controller（DJS-FIX-ADMIN-W22-006 占位版）。
 *
 * <p>mp 用户角色不含 admin 的 {@code djs:warehouse:demand:list} 权限，无法直连
 * admin 端 {@code /djs/warehouse/dashboard/summary}（会被 sa-token perm 拦截 403）。
 * 故 mp 走独立 applet 端点，复用同一 {@link IWarehouseDashboardService}。</p>
 *
 * <p>看板为只读聚合、无敏感写操作，仅 {@code @SaCheckLogin}（登录即可看），
 * 不挂细粒度 perm —— 避免为占位版 dashboard 单独写 menu seed DDL。完整版
 * V1.x WMS-DASH-001 再视需要补 {@code djs:applet:warehouse:dashboard:*} 权限菜单。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-006
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/warehouse/dashboard")
public class WarehouseDashboardAppletController {

    private final IWarehouseDashboardService dashboardService;

    /**
     * mp 仓库看板汇总：3 KPI 卡（mp 不渲染库位概览列表，但 VO 仍含，前端按需取）。
     *
     * @return 看板汇总 VO
     */
    @SaCheckLogin
    @GetMapping("/summary")
    public R<WarehouseDashboardSummaryVo> summary() {
        return R.ok(dashboardService.getSummary());
    }

}
