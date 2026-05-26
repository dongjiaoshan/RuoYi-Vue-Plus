package org.dromara.djs.breed.dashboard.service;

import org.dromara.djs.breed.dashboard.domain.vo.Activity7dVo;
import org.dromara.djs.breed.dashboard.domain.vo.AnnualIndicatorVo;
import org.dromara.djs.breed.dashboard.domain.vo.InventoryVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthlyComparisonVo;

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
