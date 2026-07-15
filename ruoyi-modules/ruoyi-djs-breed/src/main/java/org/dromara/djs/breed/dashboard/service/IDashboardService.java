package org.dromara.djs.breed.dashboard.service;

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
     * 育肥猪日龄分布（按后台「育肥阶段」配置 t_farm_fatten_age_stage 动态分桶，row95）。
     *
     * <p>读 {@link org.dromara.djs.breed.production.service.IFattenAgeStageService#queryList()} 各阶段
     * {@code [startAge, endAge]}（含两端）归桶；label 用 remark 非空、否则 "startAge-endAge天"；按 startAge 升序。</p>
     */
    List<AgeBucketVo> getFatteningStageDistribution();

    /**
     * 育肥指标趋势折线 + 实时库存 3 格（FIX-MGMT-MP-BRD-001）。
     *
     * @param period week / month（null/非法 → week）；month 非空时忽略此参
     * @param metric market / target / onhand（null/非法 → market）
     * @param month  yyyyMM / yyyy-MM，非空时返回该月逐日数据（1~该月天数），忽略 period；空/null 时保持 week/month 原逻辑
     */
    FatteningTrendVo getFatteningTrend(String period, String metric, String month);

    /**
     * 当月生产统计表（率/窝均 10 行，当月 vs 上月，#7.8，实时算）。
     *
     * @param yearMonth YYYY-MM；null → 当月
     */
    MonthlyProductionStatVo getMonthlyProductionStats(YearMonth yearMonth);

    /**
     * 年度指标（从 t_farm_year_production）。
     *
     * @param year 年份；null → 当前年
     */
    AnnualIndicatorVo getAnnualIndicator(Integer year);

    /**
     * 按 stat_date 范围查养殖农场日数据记录 t_farm_indicator_record（BRD-STAT-001，只读，历史日表）。
     *
     * <p>mp/admin 看历史日表用；不影响实时端点。from/to 任一为 null 时默认查近 30 天。
     * 结果按 stat_date 升序。</p>
     *
     * @param from 起始日期（含），null → to 前推 29 天 / 今日前推 29 天
     * @param to   结束日期（含），null → 今日
     * @return 区间内的日指标记录列表（无数据返空列表，不抛）
     */
    List<FarmIndicatorRecordVo> listIndicatorRecords(LocalDate from, LocalDate to);

    /**
     * 手动触发聚合（dev 调试用 / prod 由 SnailJob 调度）。
     *
     * @param targetDate 聚合日期（含），null → 昨天 T-1
     * @return 简单 status 描述（已写入的表清单）
     */
    String triggerAggregate(java.time.LocalDate targetDate);
}
