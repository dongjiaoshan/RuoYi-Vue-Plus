package org.dromara.djs.plant.indicator.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Report;
import org.dromara.djs.plant.indicator.mapper.PlantProductionIndicatorMapper;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ProductionIndicatorService {

    public static final String BUSINESS_ZONE = "Asia/Shanghai";

    private final ProductionIndicatorSourceMapper source;
    private final PlantProductionIndicatorMapper indicators;

    /**
     * 重算闭区间的日报及涉及的整月、整年。进行中的月年只纳入已结束的业务日，
     * 绩效按所属月份归集，计划分母始终使用完整自然期。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recalculate(LocalDate from, LocalDate to) {
        validateRange(from, to);
        LocalDate today = LocalDate.now(ZoneId.of(BUSINESS_ZONE));
        if (!to.isBefore(today)) {
            throw new ServiceException("回刷结束日须早于当前业务日");
        }
        String tenantId = currentTenant();
        // 先按年份递增取得行锁，再读取源数据，保证并发重跑按相同顺序刷新。
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            indicators.lockYear(IdWorker.getId(), year);
        }
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            LocalDate yearStart = LocalDate.of(year, 1, 1);
            LocalDate nextYear = yearStart.plusYears(1);
            LocalDate until = nextYear.isBefore(today) ? nextYear : today;
            LocalDate first = from.isAfter(yearStart) ? from : yearStart;
            LocalDate last = to.isBefore(nextYear) ? to : nextYear.minusDays(1);
            refreshYear(tenantId, yearStart, until, first, last);
        }
    }

    private void refreshYear(String tenantId, LocalDate yearStart, LocalDate until, LocalDate first, LocalDate last) {
        var calculator = new ProductionIndicatorCalculator(yearStart, until,
            source.selectDetails(tenantId, yearStart.getYear(), until),
            source.selectFarmEvents(tenantId, yearStart, until),
            source.selectInbound(tenantId, yearStart, until),
            source.selectPerformance(tenantId, YearMonth.from(yearStart).toString(),
                YearMonth.from(until.minusDays(1)).toString()));
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            indicators.upsertDaily(IdWorker.getId(), calculator.daily(date));
        }
        YearMonth lastMonth = YearMonth.from(last);
        for (YearMonth month = YearMonth.from(first); !month.isAfter(lastMonth); month = month.plusMonths(1)) {
            indicators.upsertMonthly(IdWorker.getId(), calculator.monthly(month));
        }
        var idle = source.selectIdleTotals(tenantId, yearStart, until);
        indicators.upsertAnnual(IdWorker.getId(), calculator.annual(idle));
    }

    /** 返回所选业务日与该区间涉及的自然月、自然年指标。 */
    @Transactional(readOnly = true)
    public Report query(LocalDate from, LocalDate to) {
        validateRange(from, to);
        String tenantId = currentTenant();
        LocalDate today = LocalDate.now(ZoneId.of(BUSINESS_ZONE));
        LocalDate reportDay = to.isBefore(today) ? to : today;
        // 本年空闲天数可查询到所选报表日，其余指标读取已回刷快照。
        var annual = indicators.selectAnnual(tenantId, from.getYear(), to.getYear()).stream()
            .map(row -> row.statYear() == today.getYear()
                ? row.withIdleTotals(source.selectIdleTotals(tenantId, today.withDayOfYear(1), reportDay.plusDays(1)))
                : row)
            .toList();
        return new Report(indicators.selectDaily(tenantId, from, to),
            indicators.selectMonthly(tenantId, from.withDayOfMonth(1), to.withDayOfMonth(1)), annual);
    }

    private static String currentTenant() {
        String tenantId = TenantHelper.getTenantId();
        return tenantId == null || tenantId.isBlank() ? DjsJobRunner.DEFAULT_TENANT : tenantId;
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ServiceException("请选择有效的统计起止日期");
        }
    }
}
