package org.dromara.djs.breed.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.breed.dashboard.domain.vo.Activity7dVo;
import org.dromara.djs.breed.dashboard.domain.vo.AgeBucketVo;
import org.dromara.djs.breed.dashboard.domain.vo.AnnualIndicatorVo;
import org.dromara.djs.breed.dashboard.domain.vo.BreedingAnnualVo;
import org.dromara.djs.breed.dashboard.domain.vo.DailyOverviewVo;
import org.dromara.djs.breed.dashboard.domain.vo.FarmIndicatorRecordVo;
import org.dromara.djs.breed.dashboard.domain.vo.FatteningTrendVo;
import org.dromara.djs.breed.dashboard.domain.vo.InventoryVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthActivityVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthlyComparisonVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthlyProductionStatVo;
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
import java.util.List;

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

    /**
     * 种猪场活动统计（按月聚合，原型 21）。
     *
     * @param month yyyyMM，缺省为当月
     */
    @SaCheckPermission("djs:breed:dashboard:activity")
    @GetMapping("/activity/by-month")
    public R<MonthActivityVo> getActivityByMonth(@RequestParam(value = "month", required = false) String month) {
        return R.ok(dashboardService.getActivityByMonth(month));
    }

    @SaCheckPermission("djs:breed:dashboard:annual")
    @GetMapping("/annual")
    public R<AnnualIndicatorVo> getAnnual(@RequestParam(value = "year", required = false) Integer year) {
        return R.ok(dashboardService.getAnnualIndicator(year));
    }

    /**
     * 养殖场日情况概览 16 格（FIX-MGMT-MP-BRD-001，原型种猪 tab）。
     *
     * @param date yyyy-MM-dd，缺省今日
     */
    @SaCheckPermission("djs:breed:dashboard:activity")
    @GetMapping("/daily-overview")
    public R<DailyOverviewVo> getDailyOverview(
        @RequestParam(value = "date", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(dashboardService.getDailyOverview(date));
    }

    /**
     * 年度繁殖与配种 + 产房仔猪质量指标（FIX-MGMT-MP-BRD-001，#7.1-7.5 实时算）。
     *
     * @param year 缺省当前年
     */
    @SaCheckPermission("djs:breed:dashboard:annual")
    @GetMapping("/breeding-annual")
    public R<BreedingAnnualVo> getBreedingAnnual(@RequestParam(value = "year", required = false) Integer year) {
        return R.ok(dashboardService.getBreedingAnnual(year));
    }

    /**
     * 育肥猪日龄分布（6 桶饼图，FIX-MGMT-MP-BRD-001，#7.6）。
     */
    @SaCheckPermission("djs:breed:dashboard:inventory")
    @GetMapping("/fattening-age-distribution")
    public R<List<AgeBucketVo>> getFatteningAgeDistribution() {
        return R.ok(dashboardService.getFatteningAgeDistribution());
    }

    /**
     * 育肥猪日龄分布（按后台「育肥阶段」配置动态分桶，row95）。
     * 读 t_farm_fatten_age_stage 各阶段 [startAge, endAge] 归桶，label 用 remark。
     */
    @SaCheckPermission("djs:breed:dashboard:inventory")
    @GetMapping("/fattening-stage-distribution")
    public R<List<AgeBucketVo>> getFatteningStageDistribution() {
        return R.ok(dashboardService.getFatteningStageDistribution());
    }

    /**
     * 育肥指标趋势折线 + 实时库存 3 格（FIX-MGMT-MP-BRD-001）。
     *
     * @param period week / month，缺省 week；month 非空时忽略
     * @param metric market / target / onhand，缺省 market
     * @param month  yyyyMM / yyyy-MM，非空时返回该月逐日数据（忽略 period）
     */
    @SaCheckPermission("djs:breed:dashboard:inventory")
    @GetMapping("/fattening-trend")
    public R<FatteningTrendVo> getFatteningTrend(
        @RequestParam(value = "period", required = false) String period,
        @RequestParam(value = "metric", required = false) String metric,
        @RequestParam(value = "month", required = false) String month) {
        return R.ok(dashboardService.getFatteningTrend(period, metric, month));
    }

    /**
     * 当月生产统计表（率/窝均 10 行，当月 vs 上月，FIX-MGMT-MP-BRD-001，#7.8 实时算）。
     *
     * @param yearMonth yyyy-MM，缺省当月
     */
    @SaCheckPermission("djs:breed:dashboard:monthly")
    @GetMapping("/monthly-production-stats")
    public R<MonthlyProductionStatVo> getMonthlyProductionStats(
        @RequestParam(value = "yearMonth", required = false) String yearMonth) {
        YearMonth ym = yearMonth == null || yearMonth.isBlank()
            ? null
            : YearMonth.parse(yearMonth, DateTimeFormatter.ofPattern("yyyy-MM"));
        return R.ok(dashboardService.getMonthlyProductionStats(ym));
    }

    /**
     * 养殖农场日数据记录历史查询（BRD-STAT-001，row10 落盘日表，按 stat_date 范围）。
     *
     * <p>mp「种猪场活动统计」看历史 / admin 查历史日表用。from/to 缺省查近 30 天。
     * 今日实时仍走 {@code /daily-overview}，本端点专供历史落盘数据。</p>
     *
     * @param from yyyy-MM-dd，缺省 to 前推 29 天
     * @param to   yyyy-MM-dd，缺省今日
     */
    @SaCheckPermission("djs:breed:dashboard:activity")
    @GetMapping("/indicator-records")
    public R<List<FarmIndicatorRecordVo>> listIndicatorRecords(
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(dashboardService.listIndicatorRecords(from, to));
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
