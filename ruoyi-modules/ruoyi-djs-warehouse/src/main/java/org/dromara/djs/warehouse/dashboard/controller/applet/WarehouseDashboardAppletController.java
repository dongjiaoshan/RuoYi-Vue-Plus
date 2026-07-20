package org.dromara.djs.warehouse.dashboard.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehousePorkEfficiencyVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseVegEfficiencyVo;
import org.dromara.djs.warehouse.dashboard.service.IWarehouseDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序仓库看板 Controller。
 *
 * <p>mp 用户角色不含 admin 的仓库菜单权限，无法直连 admin 端
 * {@code /djs/warehouse/dashboard/summary}（会被 sa-token perm 拦截 403）。
 * 故 mp 走独立 applet 端点，复用同一 {@link IWarehouseDashboardService}。</p>
 *
 * <p>看板为只读聚合、无敏感写操作，仅 {@code @SaCheckLogin}（登录即可看），
 * 不挂细粒度 perm。</p>
 *
 * @author djs
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

    /**
     * mp 仓库管理看板 tab1：猪只分割效能管理（年度屠宰/分割 8 KPI + 月度屠宰效能趋势 + 日猪肉处理矩阵）。
     *
     * @param year  统计年份（可空，缺省当年）
     * @param month 统计月份 yyyy-MM（可空，缺省当月；日矩阵区间锚点）
     * @return 猪只分割效能 VO
     */
    @SaCheckLogin
    @GetMapping("/pork")
    public R<WarehousePorkEfficiencyVo> pork(
        @RequestParam(value = "year", required = false) Integer year,
        @RequestParam(value = "month", required = false) String month) {
        return R.ok(dashboardService.getPorkEfficiency(year, month));
    }

    /**
     * mp 仓库管理看板 tab2：果蔬处理效能管理（年度蔬菜 6 KPI + 收获量 TOP10 + 损耗率 TOP10 + 日蔬菜处理矩阵）。
     *
     * @param year  统计年份（可空，缺省当年）
     * @param month 统计月份 yyyy-MM（可空，缺省当月；TOP10 + 日矩阵区间）
     * @return 果蔬处理效能 VO
     */
    @SaCheckLogin
    @GetMapping("/veg")
    public R<WarehouseVegEfficiencyVo> veg(
        @RequestParam(value = "year", required = false) Integer year,
        @RequestParam(value = "month", required = false) String month) {
        return R.ok(dashboardService.getVegEfficiency(year, month));
    }

}
