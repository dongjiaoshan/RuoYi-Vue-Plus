package org.dromara.djs.breed.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.breed.dashboard.domain.vo.Activity7dVo;
import org.dromara.djs.breed.dashboard.domain.vo.AnnualIndicatorVo;
import org.dromara.djs.breed.dashboard.domain.vo.InventoryVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthlyComparisonVo;
import org.dromara.djs.breed.dashboard.service.IDashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * 养殖看板 Controller（BRD-DASH-001）。
 *
 * <p>5 个端点：</p>
 * <ul>
 *   <li>{@code GET  /djs/breed/dashboard/inventory}            — 实时库存</li>
 *   <li>{@code GET  /djs/breed/dashboard/monthly-comparison}   — 月度对比（当月 vs 上月）</li>
 *   <li>{@code GET  /djs/breed/dashboard/activity-7d}          — 近 7 天活动统计</li>
 *   <li>{@code GET  /djs/breed/dashboard/annual}               — 年度指标</li>
 *   <li>{@code POST /djs/breed/dashboard/trigger-aggregate}    — 手动触发聚合（dev 调试 / prod SnailJob 调度）</li>
 * </ul>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/breed/dashboard")
public class DashboardController {

    private final IDashboardService dashboardService;

    @SaCheckPermission("djs:breed:dashboard:inventory")
    @GetMapping("/inventory")
    public R<InventoryVo> getInventory() {
        return R.ok(dashboardService.getCurrentInventory());
    }

    @SaCheckPermission("djs:breed:dashboard:monthly")
    @GetMapping("/monthly-comparison")
    public R<MonthlyComparisonVo> getMonthlyComparison(
        @RequestParam(value = "yearMonth", required = false) String yearMonth) {
        YearMonth ym = yearMonth == null || yearMonth.isBlank()
            ? null
            : YearMonth.parse(yearMonth, DateTimeFormatter.ofPattern("yyyy-MM"));
        return R.ok(dashboardService.getMonthlyComparison(ym));
    }

    @SaCheckPermission("djs:breed:dashboard:activity")
    @GetMapping("/activity-7d")
    public R<Activity7dVo> getActivity7d() {
        return R.ok(dashboardService.getActivity7d());
    }

    @SaCheckPermission("djs:breed:dashboard:annual")
    @GetMapping("/annual")
    public R<AnnualIndicatorVo> getAnnual(@RequestParam(value = "year", required = false) Integer year) {
        return R.ok(dashboardService.getAnnualIndicator(year));
    }

    /**
     * 手动触发聚合（dev 调试用；prod 由 SnailJob 后台调度 cron(0 30 0 * * *) 调用本端点）。
     */
    @SaCheckPermission("djs:breed:dashboard:aggregate")
    @PostMapping("/trigger-aggregate")
    public R<String> triggerAggregate(
        @RequestParam(value = "date", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(dashboardService.triggerAggregate(date));
    }
}
