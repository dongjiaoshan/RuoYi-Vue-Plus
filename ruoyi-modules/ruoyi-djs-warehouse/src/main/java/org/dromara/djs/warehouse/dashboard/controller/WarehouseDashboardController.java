package org.dromara.djs.warehouse.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;
import org.dromara.djs.warehouse.dashboard.service.IWarehouseDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仓库看板 Controller（DJS-FIX-ADMIN-W22-006 占位版）。
 *
 * <p>admin + mp 双端复用同一 {@code /summary} 端点。3 KPI 卡 + 库位概览，
 * 趋势折线 / 饼图推 V1.x WMS-DASH-001 完整版。</p>
 *
 * <p>权限复用 demand 读权限（{@code djs:warehouse:demand:list}），不另起 perm。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-006
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/dashboard")
public class WarehouseDashboardController extends BaseController {

    private final IWarehouseDashboardService dashboardService;

    /**
     * 仓库看板汇总：3 KPI 卡 + 库位概览（Top 20）。
     *
     * @return 看板汇总 VO（无数据时各计数返 0、库位列表空）
     */
    @SaCheckPermission("djs:warehouse:demand:list")
    @GetMapping("/summary")
    public R<WarehouseDashboardSummaryVo> summary() {
        return R.ok(dashboardService.getSummary());
    }

}
