package org.dromara.djs.breed.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.breed.dashboard.domain.AnnualIndicator;
import org.dromara.djs.breed.dashboard.domain.MonthlyProduction;
import org.dromara.djs.breed.dashboard.domain.SowRecord;
import org.dromara.djs.breed.dashboard.domain.vo.Activity7dVo;
import org.dromara.djs.breed.dashboard.domain.vo.AnnualIndicatorVo;
import org.dromara.djs.breed.dashboard.domain.vo.InventoryVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthActivityVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthlyComparisonVo;
import org.dromara.djs.breed.dashboard.mapper.AggregateQueryMapper;
import org.dromara.djs.breed.dashboard.mapper.AnnualIndicatorMapper;
import org.dromara.djs.breed.dashboard.mapper.MonthlyProductionMapper;
import org.dromara.djs.breed.dashboard.mapper.SowRecordMapper;
import org.dromara.djs.breed.dashboard.service.IDashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 养殖 dashboard 聚合查询 + 聚合写入实现（BRD-DASH-001）。
 *
 * <p>本类承担两类职责：</p>
 * <ol>
 *   <li><b>读端</b>：4 个 dashboard 查询端点（实时库存 / 月度对比 / 7 天活动 / 年度指标）</li>
 *   <li><b>写端</b>：{@link #triggerAggregate(LocalDate)} 重算指定日期的 sow_record + 当月 monthly_production + 当年 annual_indicator。
 *       prod 由 SnailJob 后台调度 cron(每天 00:30) 调用本端点；本地 snail-job.enabled=false，开发期靠手动 POST。</li>
 * </ol>
 *
 * <p><b>颜色规则强调（国内畜牧惯例，反国际惯例）</b>：
 * 当月数值更"好"（如出栏头数上升 / 死亡数下降）→ trend="better" → fe 红色；
 * 当月更"差"→ trend="worse" → fe 绿色；持平→ trend="flat" → fe 黑色。
 * "好坏"判断由本类的 {@link #judgeTrend(String, BigDecimal, BigDecimal)} 集中决定。</p>
 *
 * <p><b>终止时间走 status_record</b>（ADR-0007 + D6 closing #8）：死亡 / 淘汰 / 出栏头数一律
 * 查 {@code t_farm_status_record event_type IN (DIE, ELIMINATE, SLAUGHTER)}，
 * 不查 {@code t_farm_pig_info.end_date}（该字段不存在）。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements IDashboardService {

    private static final String DEFAULT_TENANT = "1001";

    /** "更高 = 更好"的 KPI（KPI 名 → 是否好向上）。 */
    private static final Map<String, Boolean> KPI_UP_IS_BETTER = Map.of(
        "introduceCount", true,
        "bornCount", true,
        "weanedCount", true,
        "deathCount", false,
        "cullingCount", false,
        "marketingCount", true,
        "marketingWeight", true
    );

    private final SowRecordMapper sowRecordMapper;
    private final MonthlyProductionMapper monthlyProductionMapper;
    private final AnnualIndicatorMapper annualIndicatorMapper;
    private final AggregateQueryMapper aggregateQueryMapper;

    // ============================================================
    //  Read endpoints
    // ============================================================

    @Override
    public InventoryVo getCurrentInventory() {
        String tenantId = currentTenant();
        InventoryVo vo = new InventoryVo();

        Map<String, Integer> byType = new LinkedHashMap<>();
        for (Map<String, Object> row : aggregateQueryMapper.countInventoryByType(tenantId)) {
            String type = Objects.toString(row.get("pigType"), "");
            int cnt = ((Number) row.get("cnt")).intValue();
            byType.put(type, cnt);
        }
        vo.setInventoryByType(byType);

        Map<String, Integer> sowByLc = new LinkedHashMap<>();
        for (Map<String, Object> row : aggregateQueryMapper.countByLifecycle(tenantId, "sow")) {
            String lc = Objects.toString(row.get("lifecycle"), "");
            int cnt = ((Number) row.get("cnt")).intValue();
            sowByLc.put(lc, cnt);
        }
        vo.setSowByLifecycle(sowByLc);

        return vo;
    }

    @Override
    public MonthlyComparisonVo getMonthlyComparison(YearMonth yearMonth) {
        if (yearMonth == null) {
            yearMonth = YearMonth.now();
        }
        YearMonth previous = yearMonth.minusMonths(1);
        String tenantId = currentTenant();

        MonthlyProduction curr = selectMonth(tenantId, yearMonth);
        MonthlyProduction prev = selectMonth(tenantId, previous);

        MonthlyComparisonVo vo = new MonthlyComparisonVo();
        vo.setCurrentMonth(yearMonth.toString());
        vo.setPreviousMonth(previous.toString());

        vo.setIntroduceCount(compare("introduceCount", bd(curr, MonthlyProduction::getIntroduceCount), bd(prev, MonthlyProduction::getIntroduceCount)));
        vo.setBornCount(compare("bornCount", bd(curr, MonthlyProduction::getBornCount), bd(prev, MonthlyProduction::getBornCount)));
        vo.setWeanedCount(compare("weanedCount", bd(curr, MonthlyProduction::getWeanedCount), bd(prev, MonthlyProduction::getWeanedCount)));
        vo.setDeathCount(compare("deathCount", bd(curr, MonthlyProduction::getDeathCount), bd(prev, MonthlyProduction::getDeathCount)));
        vo.setCullingCount(compare("cullingCount", bd(curr, MonthlyProduction::getCullingCount), bd(prev, MonthlyProduction::getCullingCount)));
        vo.setMarketingCount(compare("marketingCount", bd(curr, MonthlyProduction::getMarketingCount), bd(prev, MonthlyProduction::getMarketingCount)));
        vo.setMarketingWeight(compare("marketingWeight",
            curr == null ? BigDecimal.ZERO : Optional.ofNullable(curr.getMarketingWeight()).orElse(BigDecimal.ZERO),
            prev == null ? BigDecimal.ZERO : Optional.ofNullable(prev.getMarketingWeight()).orElse(BigDecimal.ZERO)));

        return vo;
    }

    @Override
    public Activity7dVo getActivity7d() {
        String tenantId = currentTenant();
        LocalDate from = LocalDate.now().minusDays(6); // 含今天共 7 天
        List<SowRecord> records = sowRecordMapper.selectRangeAsc(tenantId, from);
        Activity7dVo vo = new Activity7dVo();
        List<Activity7dVo.DailyRow> rows = new ArrayList<>(records.size());
        for (SowRecord r : records) {
            Activity7dVo.DailyRow row = new Activity7dVo.DailyRow();
            row.setStatDate(r.getStatDate());
            row.setSowTotal(r.getSowTotal());
            row.setSowPregnant(r.getSowPregnant());
            row.setSowFarrow(r.getSowFarrow());
            row.setSowWeaning(r.getSowWeaning());
            row.setSowIdle(r.getSowIdle());
            row.setSowCullingCount(r.getSowCullingCount());
            row.setSowDeathCount(r.getSowDeathCount());
            row.setPigletTotal(r.getPigletTotal());
            rows.add(row);
        }
        vo.setRows(rows);
        return vo;
    }

    @Override
    public AnnualIndicatorVo getAnnualIndicator(Integer year) {
        if (year == null) {
            year = LocalDate.now().getYear();
        }
        String tenantId = currentTenant();
        AnnualIndicator ai = selectYear(tenantId, year.shortValue());
        AnnualIndicatorVo vo = new AnnualIndicatorVo();
        vo.setStatYear(year.shortValue());
        if (ai == null) {
            vo.setIntroduceCount(0);
            vo.setBornCount(0);
            vo.setWeanedCount(0);
            vo.setDeathCount(0);
            vo.setCullingCount(0);
            vo.setMarketingCount(0);
            vo.setMarketingWeight(BigDecimal.ZERO);
            vo.setPsy(BigDecimal.ZERO);
            vo.setMortalityRate(BigDecimal.ZERO);
            return vo;
        }
        vo.setIntroduceCount(zeroIfNull(ai.getIntroduceCount()));
        vo.setBornCount(zeroIfNull(ai.getBornCount()));
        vo.setWeanedCount(zeroIfNull(ai.getWeanedCount()));
        vo.setDeathCount(zeroIfNull(ai.getDeathCount()));
        vo.setCullingCount(zeroIfNull(ai.getCullingCount()));
        vo.setMarketingCount(zeroIfNull(ai.getMarketingCount()));
        vo.setMarketingWeight(Optional.ofNullable(ai.getMarketingWeight()).orElse(BigDecimal.ZERO));
        vo.setPsy(Optional.ofNullable(ai.getPsy()).orElse(BigDecimal.ZERO));
        vo.setMortalityRate(Optional.ofNullable(ai.getMortalityRate()).orElse(BigDecimal.ZERO));
        return vo;
    }

    @Override
    public MonthActivityVo getActivityByMonth(String month) {
        YearMonth ym;
        if (month == null || month.isBlank()) {
            ym = YearMonth.now();
        } else {
            try {
                ym = YearMonth.parse(month, java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
            } catch (Exception e) {
                ym = YearMonth.now(); // 入参非法退化为当月，不打断查询
            }
        }
        String tenantId = currentTenant();
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        LocalDate toExclusive = last.plusDays(1); // 右开区间
        LocalDateTime dtFrom = first.atStartOfDay();
        LocalDateTime dtTo = toExclusive.atStartOfDay();

        java.time.format.DateTimeFormatter md = java.time.format.DateTimeFormatter.ofPattern("MM-dd");
        List<String> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            days.add(d.format(md));
        }

        MonthActivityVo vo = new MonthActivityVo();
        vo.setDays(days);
        List<MonthActivityVo.MonthRow> rows = new ArrayList<>();
        // 按原型 21 行序。数据源缺失的指标返 0（不抛异常）。
        // 真实表名/列名见 SYS-INIT-001 DDL；返空流母猪数无独立数据源 → 退化 0。
        rows.add(buildRow("分娩母猪数", days, byDayCount("t_farm_pig_farrow", "farrow_date", tenantId, first, toExclusive)));
        rows.add(buildRow("配种母猪数", days, byDayCount("t_farm_pig_breeding", "breeding_date", tenantId, first, toExclusive)));
        rows.add(buildRow("断奶母猪数", days, byDayCount("t_farm_pig_weaning", "weaning_date", tenantId, first, toExclusive)));
        rows.add(buildRow("返空流母猪数", days, emptyDay()));
        rows.add(buildRow("死亡母猪数", days, byDayStatus(tenantId, "DIE", dtFrom, dtTo)));
        rows.add(buildRow("淘汰母猪数", days, byDayStatus(tenantId, "ELIMINATE", dtFrom, dtTo)));
        rows.add(buildRow("后备入场数", days, byDaySum("t_farm_pig_introduce", "introduce_date", "pig_count", tenantId, first, toExclusive)));
        rows.add(buildRow("查情母猪数", days, byDayCount("t_farm_pig_heat", "heat_date", tenantId, first, toExclusive)));
        rows.add(buildRow("产仔数", days, byDaySum("t_farm_pig_farrow", "farrow_date", "total_born", tenantId, first, toExclusive)));
        rows.add(buildRow("活仔数", days, byDaySum("t_farm_pig_farrow", "farrow_date", "live_born", tenantId, first, toExclusive)));
        rows.add(buildRow("仔猪打标数", days, byDayCount("t_farm_pig_pigletno", "tag_date", tenantId, first, toExclusive)));
        vo.setRows(rows);
        return vo;
    }

    private Map<String, Integer> emptyDay() {
        return new LinkedHashMap<>();
    }

    private Map<String, Integer> byDayCount(String table, String dateColumn, String tenantId, LocalDate from, LocalDate to) {
        return toDayMap(aggregateQueryMapper.countEventByDay(table, dateColumn, tenantId, from, to));
    }

    private Map<String, Integer> byDaySum(String table, String dateColumn, String valueColumn, String tenantId, LocalDate from, LocalDate to) {
        return toDayMap(aggregateQueryMapper.sumEventByDay(table, dateColumn, valueColumn, tenantId, from, to));
    }

    private Map<String, Integer> byDayStatus(String tenantId, String eventType, LocalDateTime from, LocalDateTime to) {
        return toDayMap(aggregateQueryMapper.countStatusEventByDay(tenantId, eventType, from, to));
    }

    private Map<String, Integer> toDayMap(List<Map<String, Object>> raw) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Map<String, Object> r : raw) {
            Object d = r.get("d");
            Object v = r.get("v");
            if (d == null) {
                continue;
            }
            m.put(Objects.toString(d, ""), v instanceof Number n ? n.intValue() : 0);
        }
        return m;
    }

    private MonthActivityVo.MonthRow buildRow(String metric, List<String> days, Map<String, Integer> byDay) {
        List<Integer> daily = new ArrayList<>(days.size());
        int total = 0;
        for (String day : days) {
            int v = byDay.getOrDefault(day, 0);
            daily.add(v);
            total += v;
        }
        MonthActivityVo.MonthRow row = new MonthActivityVo.MonthRow();
        row.setMetric(metric);
        row.setDaily(daily);
        row.setTotal(total);
        return row;
    }

    // ============================================================
    //  Write end — aggregate job
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String triggerAggregate(LocalDate targetDate) {
        if (targetDate == null) {
            targetDate = LocalDate.now().minusDays(1); // T-1
        }
        String tenantId = currentTenant();
        log.info("[DashboardAggregate] start tenant={} date={}", tenantId, targetDate);

        upsertSowRecord(tenantId, targetDate);
        upsertMonthlyProduction(tenantId, YearMonth.from(targetDate));
        upsertAnnualIndicator(tenantId, (short) targetDate.getYear());

        log.info("[DashboardAggregate] done tenant={} date={}", tenantId, targetDate);
        return String.format("ok | tenant=%s | date=%s | tables=[sow_record, monthly_production, annual_indicator]",
            tenantId, targetDate);
    }

    /**
     * 重算指定 stat_date 一行（UPSERT 语义：存在则 UPDATE，不存在则 INSERT）。
     */
    private void upsertSowRecord(String tenantId, LocalDate statDate) {
        LocalDateTime dayStart = statDate.atStartOfDay();
        LocalDateTime dayEnd = statDate.plusDays(1).atStartOfDay();

        // 当日 23:59 时点 sow / piglet 分布
        int sowTotal = 0, sowPregnant = 0, sowFarrow = 0, sowWeaning = 0, sowIdle = 0;
        for (Map<String, Object> row : aggregateQueryMapper.countByLifecycle(tenantId, "sow")) {
            String lc = Objects.toString(row.get("lifecycle"), "");
            int cnt = ((Number) row.get("cnt")).intValue();
            sowTotal += cnt;
            switch (lc) {
                case "PZ":
                case "PH":
                    sowPregnant += cnt;
                    break;
                case "FM":
                    sowFarrow += cnt;
                    break;
                case "DN":
                    sowWeaning += cnt;
                    break;
                default:
                    sowIdle += cnt; // KH/LC/FQ/HB
            }
        }
        int pigletTotal = 0;
        for (Map<String, Object> row : aggregateQueryMapper.countByLifecycle(tenantId, "piglet")) {
            pigletTotal += ((Number) row.get("cnt")).intValue();
        }

        int dieCount = aggregateQueryMapper.countStatusEventInRange(tenantId, "DIE", dayStart, dayEnd);
        int eliminateCount = aggregateQueryMapper.countStatusEventInRange(tenantId, "ELIMINATE", dayStart, dayEnd);

        // 查现有行
        LambdaQueryWrapper<SowRecord> q = new LambdaQueryWrapper<SowRecord>()
            .eq(SowRecord::getStatDate, statDate);
        SowRecord existing = sowRecordMapper.selectOne(q);
        if (existing == null) {
            SowRecord r = new SowRecord();
            r.setStatDate(statDate);
            r.setSowTotal(sowTotal);
            r.setSowPregnant(sowPregnant);
            r.setSowFarrow(sowFarrow);
            r.setSowWeaning(sowWeaning);
            r.setSowIdle(sowIdle);
            r.setSowCullingCount(eliminateCount);
            r.setSowDeathCount(dieCount);
            r.setPigletTotal(pigletTotal);
            r.setDelFlag("0");
            r.setDelUnique(0L);
            sowRecordMapper.insert(r);
        } else {
            UpdateWrapper<SowRecord> up = Wrappers.<SowRecord>update()
                .eq("id", existing.getId())
                .set("sow_total", sowTotal)
                .set("sow_pregnant", sowPregnant)
                .set("sow_farrow", sowFarrow)
                .set("sow_weaning", sowWeaning)
                .set("sow_idle", sowIdle)
                .set("sow_culling_count", eliminateCount)
                .set("sow_death_count", dieCount)
                .set("piglet_total", pigletTotal)
                .set("update_time", LocalDateTime.now());
            sowRecordMapper.update(null, up);
        }
    }

    private void upsertMonthlyProduction(String tenantId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        LocalDateTime dtFrom = from.atStartOfDay();
        LocalDateTime dtTo = to.atStartOfDay();

        int introduceCount = aggregateQueryMapper.sumIntroducedInRange(tenantId, from, to);
        int bornCount = aggregateQueryMapper.sumLiveBornInRange(tenantId, from, to);
        int weanedCount = aggregateQueryMapper.sumWeanedInRange(tenantId, from, to);
        int deathCount = aggregateQueryMapper.countStatusEventInRange(tenantId, "DIE", dtFrom, dtTo);
        int cullingCount = aggregateQueryMapper.countStatusEventInRange(tenantId, "ELIMINATE", dtFrom, dtTo);
        Map<String, Object> marketing = aggregateQueryMapper.aggregateMarketingInRange(tenantId, from, to);
        int marketingCount = marketing == null ? 0 : ((Number) marketing.getOrDefault("cnt", 0)).intValue();
        BigDecimal marketingWeight = marketing == null
            ? BigDecimal.ZERO
            : Optional.ofNullable((BigDecimal) marketing.get("weight")).orElse(BigDecimal.ZERO);

        MonthlyProduction existing = selectMonth(tenantId, month);
        if (existing == null) {
            MonthlyProduction m = new MonthlyProduction();
            m.setStatMonth(month.toString());
            m.setIntroduceCount(introduceCount);
            m.setBornCount(bornCount);
            m.setWeanedCount(weanedCount);
            m.setDeathCount(deathCount);
            m.setCullingCount(cullingCount);
            m.setMarketingCount(marketingCount);
            m.setMarketingWeight(marketingWeight);
            m.setDelFlag("0");
            m.setDelUnique(0L);
            monthlyProductionMapper.insert(m);
        } else {
            UpdateWrapper<MonthlyProduction> up = Wrappers.<MonthlyProduction>update()
                .eq("id", existing.getId())
                .set("introduce_count", introduceCount)
                .set("born_count", bornCount)
                .set("weaned_count", weanedCount)
                .set("death_count", deathCount)
                .set("culling_count", cullingCount)
                .set("marketing_count", marketingCount)
                .set("marketing_weight", marketingWeight)
                .set("update_time", LocalDateTime.now());
            monthlyProductionMapper.update(null, up);
        }
    }

    private void upsertAnnualIndicator(String tenantId, short year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year + 1, 1, 1);
        LocalDateTime dtFrom = from.atStartOfDay();
        LocalDateTime dtTo = to.atStartOfDay();

        int introduceCount = aggregateQueryMapper.sumIntroducedInRange(tenantId, from, to);
        int bornCount = aggregateQueryMapper.sumLiveBornInRange(tenantId, from, to);
        int weanedCount = aggregateQueryMapper.sumWeanedInRange(tenantId, from, to);
        int deathCount = aggregateQueryMapper.countStatusEventInRange(tenantId, "DIE", dtFrom, dtTo);
        int cullingCount = aggregateQueryMapper.countStatusEventInRange(tenantId, "ELIMINATE", dtFrom, dtTo);
        Map<String, Object> marketing = aggregateQueryMapper.aggregateMarketingInRange(tenantId, from, to);
        int marketingCount = marketing == null ? 0 : ((Number) marketing.getOrDefault("cnt", 0)).intValue();
        BigDecimal marketingWeight = marketing == null
            ? BigDecimal.ZERO
            : Optional.ofNullable((BigDecimal) marketing.get("weight")).orElse(BigDecimal.ZERO);

        // PSY = annual weaned / current alive sows（4 位小数）
        int aliveSows = aggregateQueryMapper.countAliveSows(tenantId);
        BigDecimal psy = aliveSows == 0
            ? BigDecimal.ZERO
            : new BigDecimal(weanedCount).divide(new BigDecimal(aliveSows), 4, RoundingMode.HALF_UP);

        // 死亡率 = DEATH / (DEATH + alive sows)（4 位小数）
        int denom = deathCount + aliveSows;
        BigDecimal mortalityRate = denom == 0
            ? BigDecimal.ZERO
            : new BigDecimal(deathCount).divide(new BigDecimal(denom), 4, RoundingMode.HALF_UP);

        AnnualIndicator existing = selectYear(tenantId, year);
        if (existing == null) {
            AnnualIndicator a = new AnnualIndicator();
            a.setStatYear(year);
            a.setIntroduceCount(introduceCount);
            a.setBornCount(bornCount);
            a.setWeanedCount(weanedCount);
            a.setDeathCount(deathCount);
            a.setCullingCount(cullingCount);
            a.setMarketingCount(marketingCount);
            a.setMarketingWeight(marketingWeight);
            a.setPsy(psy);
            a.setMortalityRate(mortalityRate);
            a.setDelFlag("0");
            a.setDelUnique(0L);
            annualIndicatorMapper.insert(a);
        } else {
            UpdateWrapper<AnnualIndicator> up = Wrappers.<AnnualIndicator>update()
                .eq("id", existing.getId())
                .set("introduce_count", introduceCount)
                .set("born_count", bornCount)
                .set("weaned_count", weanedCount)
                .set("death_count", deathCount)
                .set("culling_count", cullingCount)
                .set("marketing_count", marketingCount)
                .set("marketing_weight", marketingWeight)
                .set("psy", psy)
                .set("mortality_rate", mortalityRate)
                .set("update_time", LocalDateTime.now());
            annualIndicatorMapper.update(null, up);
        }
    }

    // ============================================================
    //  helpers
    // ============================================================

    private String currentTenant() {
        try {
            String t = TenantHelper.getTenantId();
            return t == null || t.isEmpty() ? DEFAULT_TENANT : t;
        } catch (Exception e) {
            return DEFAULT_TENANT;
        }
    }

    private MonthlyProduction selectMonth(String tenantId, YearMonth ym) {
        return monthlyProductionMapper.selectOne(
            new LambdaQueryWrapper<MonthlyProduction>()
                .eq(MonthlyProduction::getStatMonth, ym.toString()));
    }

    private AnnualIndicator selectYear(String tenantId, short year) {
        return annualIndicatorMapper.selectOne(
            new LambdaQueryWrapper<AnnualIndicator>()
                .eq(AnnualIndicator::getStatYear, year));
    }

    private MonthlyComparisonVo.KpiCompare compare(String kpi, BigDecimal curr, BigDecimal prev) {
        MonthlyComparisonVo.KpiCompare c = new MonthlyComparisonVo.KpiCompare();
        c.setCurrent(curr);
        c.setPrevious(prev);
        c.setDiff(curr.subtract(prev));
        c.setTrend(judgeTrend(kpi, curr, prev));
        return c;
    }

    /**
     * 判定趋势：基于 {@link #KPI_UP_IS_BETTER} 表决定"上升"是否算"更好"。
     */
    private String judgeTrend(String kpi, BigDecimal curr, BigDecimal prev) {
        int cmp = curr.compareTo(prev);
        if (cmp == 0) {
            return "flat";
        }
        boolean upIsBetter = KPI_UP_IS_BETTER.getOrDefault(kpi, true);
        boolean increased = cmp > 0;
        return (increased == upIsBetter) ? "better" : "worse";
    }

    private static BigDecimal bd(MonthlyProduction m, java.util.function.Function<MonthlyProduction, Integer> getter) {
        if (m == null) {
            return BigDecimal.ZERO;
        }
        Integer v = getter.apply(m);
        return v == null ? BigDecimal.ZERO : new BigDecimal(v);
    }

    private static int zeroIfNull(Integer v) {
        return v == null ? 0 : v;
    }
}
