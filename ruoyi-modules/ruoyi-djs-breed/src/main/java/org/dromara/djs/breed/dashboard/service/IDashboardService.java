package org.dromara.djs.breed.dashboard.service;

import org.dromara.djs.breed.dashboard.domain.vo.Activity7dVo;
import org.dromara.djs.breed.dashboard.domain.vo.AgeBucketVo;
import org.dromara.djs.breed.dashboard.domain.vo.AnnualIndicatorVo;
import org.dromara.djs.breed.dashboard.domain.vo.BreedingAnnualVo;
import org.dromara.djs.breed.dashboard.domain.vo.DailyOverviewVo;
import org.dromara.djs.breed.dashboard.domain.vo.FatteningTrendVo;
import org.dromara.djs.breed.dashboard.domain.vo.InventoryVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthActivityVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthlyComparisonVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthlyProductionStatVo;

import java.time.LocalDate;
import java.util.List;
import java.time.YearMonth;

/**
 * 养殖 dashboard 聚合查询 Service（BRD-DASH-001，只读）。
 *
 * <p>4 个核心端点的数据源；定时聚合 job 写入 4 张聚合表，本 service 只 SELECT。
 * 实时库存 ({@link #getCurrentInventory()}) 不走聚合表，直接查 {@code t_farm_pig_info}。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
public interface IDashboardService {

    /**
     * 实时库存（不走聚合表，直接 query t_farm_pig_info）。
     * 排除 lifecycle='END' 的猪只；按 pig_type + lifecycle 分组。
     */
    InventoryVo getCurrentInventory();

    /**
     * 月度对比（当月 vs 上月，7 项 KPI）。
     *
     * @param yearMonth YYYY-MM；null → 取当前月
     */
    MonthlyComparisonVo getMonthlyComparison(YearMonth yearMonth);

    /**
     * 近 7 天活动统计（从 t_farm_sow_record，按 stat_date 升序）。
     */
    Activity7dVo getActivity7d();

    /**
     * 按月聚合种猪场活动统计（原型 21，13 指标 × 当月每日 + 累计列）。
     * 直查各 event 表 GROUP BY 日期，无快照表依赖；累计列后端算。
     *
     * @param month yyyyMM；null/非法 → 当月
     */
    MonthActivityVo getActivityByMonth(String month);

    /**
     * 养殖场日情况概览 16 格（FIX-MGMT-MP-BRD-001）。
     * 某自然日的 16 项养殖活动当日值（中文 metric → 当日值）。
     *
     * @param date 统计日期；null → 今日
     */
    DailyOverviewVo getDailyOverview(LocalDate date);

    /**
     * 年度繁殖与配种 + 产房仔猪质量指标（FIX-MGMT-MP-BRD-001，#7.1-7.5，实时算）。
     *
     * @param year 年份；null → 当前年
     */
    BreedingAnnualVo getBreedingAnnual(Integer year);

    /**
     * 育肥猪日龄分布（6 桶饼图，FIX-MGMT-MP-BRD-001，#7.6）。
     */
    List<AgeBucketVo> getFatteningAgeDistribution();

    /**
     * 育肥指标趋势折线 + 实时库存 3 格（FIX-MGMT-MP-BRD-001）。
     *
     * @param period week / month（null/非法 → week）
     * @param metric market / target / onhand（null/非法 → market）
     */
    FatteningTrendVo getFatteningTrend(String period, String metric);

    /**
     * 当月生产统计表（率/窝均 10 行，当月 vs 上月，#7.8，实时算）。
     *
     * @param yearMonth YYYY-MM；null → 当月
     */
    MonthlyProductionStatVo getMonthlyProductionStats(YearMonth yearMonth);

    /**
     * 年度指标（从 t_farm_annual_indicator）。
     *
     * @param year 年份；null → 当前年
     */
    AnnualIndicatorVo getAnnualIndicator(Integer year);

    /**
     * 手动触发聚合（dev 调试用 / prod 由 SnailJob 调度）。
     *
     * @param targetDate 聚合日期（含），null → 昨天 T-1
     * @return 简单 status 描述（已写入的表清单）
     */
    String triggerAggregate(java.time.LocalDate targetDate);
}
