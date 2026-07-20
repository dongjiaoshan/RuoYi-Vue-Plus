package org.dromara.djs.warehouse.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardChartsVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehousePorkEfficiencyVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseVegEfficiencyVo;
import org.dromara.djs.warehouse.dashboard.service.IWarehouseDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仓库看板 Controller。
 *
 * <p>两个端点：</p>
 * <ul>
 *   <li>{@code GET /summary}：3 KPI 卡 + 库位概览（admin + mp 双端复用）。</li>
 *   <li>{@code GET /charts}：6 张 ECharts 图 series + 双 KPI 横条 11 指标（admin 完整看板）。</li>
 * </ul>
 *
 * <p>权限统一用菜单 perm {@code djs:warehouse:dashboard:view}（9400 仓库看板菜单 seed 该 perm，
 * 已授给所有持仓库菜单的角色）。mp 端走独立 applet controller（{@code @SaCheckLogin}）。</p>
 *
 * @author djs
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
    @SaCheckPermission("djs:warehouse:dashboard:view")
    @GetMapping("/summary")
    public R<WarehouseDashboardSummaryVo> summary() {
        return R.ok(dashboardService.getSummary());
    }

    /**
     * 仓库看板可视化：6 张图 series + 双 KPI 横条 11 指标。
     *
     * @return 看板可视化 VO（无数据时各 series 空、折线全 0、横条指标全 0）
     */
    @SaCheckPermission("djs:warehouse:dashboard:view")
    @GetMapping("/charts")
    public R<WarehouseDashboardChartsVo> charts() {
        return R.ok(dashboardService.getCharts());
    }

    /**
     * 猪只分割效能管理（消费 WMS-STAT-001 落盘表，admin 端与 mp 复用同 service）。
     *
     * @param year  统计年份（可空，缺省当年）
     * @param month 统计月份 yyyy-MM（可空，缺省当月）
     * @return 猪只分割效能 VO
     */
    @SaCheckPermission("djs:warehouse:dashboard:view")
    @GetMapping("/pork")
    public R<WarehousePorkEfficiencyVo> pork(
        @RequestParam(value = "year", required = false) Integer year,
        @RequestParam(value = "month", required = false) String month) {
        return R.ok(dashboardService.getPorkEfficiency(year, month));
    }

    /**
     * 果蔬处理效能管理（消费 WMS-STAT-001 落盘表，admin 端与 mp 复用同 service）。
     *
     * @param year  统计年份（可空，缺省当年）
     * @param month 统计月份 yyyy-MM（可空，缺省当月）
     * @return 果蔬处理效能 VO
     */
    @SaCheckPermission("djs:warehouse:dashboard:view")
    @GetMapping("/veg")
    public R<WarehouseVegEfficiencyVo> veg(
        @RequestParam(value = "year", required = false) Integer year,
        @RequestParam(value = "month", required = false) String month) {
        return R.ok(dashboardService.getVegEfficiency(year, month));
    }

}
