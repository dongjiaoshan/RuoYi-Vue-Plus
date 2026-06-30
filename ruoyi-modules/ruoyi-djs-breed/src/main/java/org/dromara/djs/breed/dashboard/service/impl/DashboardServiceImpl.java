package org.dromara.djs.breed.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.breed.dashboard.domain.AnnualIndicator;
import org.dromara.djs.breed.dashboard.domain.FarmIndicatorRecord;
import org.dromara.djs.breed.dashboard.domain.MonthlyProduction;
import org.dromara.djs.breed.dashboard.domain.SowRecord;
import org.dromara.djs.breed.production.domain.SowPerformance;
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
import org.dromara.djs.breed.dashboard.mapper.AggregateQueryMapper;
import org.dromara.djs.breed.dashboard.mapper.AnnualIndicatorMapper;
import org.dromara.djs.breed.dashboard.mapper.FarmIndicatorRecordMapper;
import org.dromara.djs.breed.dashboard.mapper.MonthlyProductionMapper;
import org.dromara.djs.breed.dashboard.mapper.SowRecordMapper;
import org.dromara.djs.breed.dashboard.service.IDashboardService;
import org.dromara.djs.breed.production.mapper.SowPerformanceMapper;
import org.dromara.djs.breed.production.service.IProductionCycleConfigService;
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
    private final FarmIndicatorRecordMapper farmIndicatorRecordMapper;
    private final SowPerformanceMapper sowPerformanceMapper;
    private final IProductionCycleConfigService productionCycleConfigService;

    /** 配种→分娩天数配置键（row183）：缺省 114 天。 */
    private static final String CONFIG_KEY_BREED_TO_FARROW = "sow_breed_to_farrow_days";
    /** 配种→分娩天数缺省值（配置 key 缺失时 fallback）。 */
    private static final int DEFAULT_BREED_TO_FARROW_DAYS = 114;

    /** 取配种→分娩天数：读 {@code sow_breed_to_farrow_days}，缺则回退 114。 */
    private int breedToFarrowDays() {
        Integer d = productionCycleConfigService.getValue(CONFIG_KEY_BREED_TO_FARROW);
        return d != null ? d : DEFAULT_BREED_TO_FARROW_DAYS;
    }

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
            vo.setTotalFatteningDeath(0);
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
        vo.setTotalFatteningDeath(zeroIfNull(ai.getTotalFatteningDeath()));
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
        // FIX-MGMT-MP-BRD-ACTIVITY-RANGE-001：日期列「从昨日开始往前」——活动统计只展示已发生的日期，
        // 不再铺满整月（避免今天及未来日期全 0 列），且数组顺序为昨日 → 月初倒序（days[0]=昨日）。
        // 上界 last = min(当月最后一天, 昨日)：
        //   · 查当月：昨日通常 < 月末 → 列从昨日倒排到月初；今天是 1 号查本月时昨日落上月 → last < first → days 空。
        //   · 查历史已过完的月：昨日 ≥ 月末 → last = 月末，从月末倒排到月初（整月）。
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate last = ym.atEndOfMonth().isAfter(yesterday) ? yesterday : ym.atEndOfMonth();
        LocalDate toExclusive = last.plusDays(1); // 右开区间（聚合查询用，缩到昨日+1）
        LocalDateTime dtFrom = first.atStartOfDay();
        LocalDateTime dtTo = toExclusive.atStartOfDay();

        java.time.format.DateTimeFormatter md = java.time.format.DateTimeFormatter.ofPattern("MM-dd");
        List<String> days = new ArrayList<>();
        // 从 last（昨日 / 月末）倒排到 first（月初）。last < first（今天 1 号查本月）时不进循环 → days 空。
        for (LocalDate d = last; !d.isBefore(first); d = d.minusDays(1)) {
            days.add(d.format(md));
        }

        MonthActivityVo vo = new MonthActivityVo();
        vo.setDays(days);

        // 邓博 row10：活动统计统一改读日表 t_farm_indicator_record（不再实时聚合 event 表），并扩到日表全指标集。
        // 拉当月日表行 → 按 stat_date(MM-dd) 索引 → 每指标按 days 顺序逐日取该列值。
        Map<String, FarmIndicatorRecord> byDate = new LinkedHashMap<>();
        // 区间 [first, toExclusive) 与 days 同源（days 倒排，这里只用作索引 Map，顺序不影响）
        for (FarmIndicatorRecord r : aggregateQueryMapper.selectIndicatorRecordsInRange(tenantId, first, toExclusive)) {
            if (r.getStatDate() != null) {
                byDate.put(r.getStatDate().format(md), r);
            }
        }

        List<MonthActivityVo.MonthRow> rows = new ArrayList<>();
        for (ActivityMetric m : ACTIVITY_METRICS) {
            rows.add(buildIndicatorRow(m, days, byDate));
        }
        vo.setRows(rows);
        return vo;
    }

    /** row10 活动统计指标累计聚合方式。 */
    private enum ActivityAgg {
        /** 整数列按日累加。 */
        SUM_INT,
        /** DECIMAL 列按日累加。 */
        SUM_DEC,
        /** 期末快照类：累计取 days 里最后一个有值日的值。 */
        LAST,
        /** 均值类：逐日展示，累计留空串（不汇总）。 */
        NONE
    }

    /** row10 活动统计单指标定义（中文文案 + 取值器 + 累计聚合方式）。 */
    private record ActivityMetric(String label,
                                  java.util.function.Function<FarmIndicatorRecord, Object> getter,
                                  ActivityAgg agg) {
    }

    /**
     * row10 活动统计指标行集（按表列、邓博 row10 中文文案，顺序分组）。
     * 整数列 SUM_INT；DECIMAL 列 SUM_DEC；期末存栏 LAST（累计=当月最后一天值）；均值类 NONE（累计留空）。
     */
    private static final List<ActivityMetric> ACTIVITY_METRICS = List.of(
        // 母猪活动（SUM）
        new ActivityMetric("分娩母猪数", FarmIndicatorRecord::getFarrowSowCount, ActivityAgg.SUM_INT),
        new ActivityMetric("配种母猪数", FarmIndicatorRecord::getBreedingSowCount, ActivityAgg.SUM_INT),
        new ActivityMetric("断奶母猪数", FarmIndicatorRecord::getWeaningSowCount, ActivityAgg.SUM_INT),
        new ActivityMetric("返空流母猪数", FarmIndicatorRecord::getAbnormalSowCount, ActivityAgg.SUM_INT),
        new ActivityMetric("引种母猪数", FarmIndicatorRecord::getIntroduceSowCount, ActivityAgg.SUM_INT),
        new ActivityMetric("查情不配种数", FarmIndicatorRecord::getHeatNoBreedCount, ActivityAgg.SUM_INT),
        // 仔猪（SUM）
        new ActivityMetric("产仔数", FarmIndicatorRecord::getTotalBornCount, ActivityAgg.SUM_INT),
        new ActivityMetric("活仔数", FarmIndicatorRecord::getLiveBornCount, ActivityAgg.SUM_INT),
        new ActivityMetric("仔猪打标数", FarmIndicatorRecord::getPigletTagCount, ActivityAgg.SUM_INT),
        new ActivityMetric("断奶仔猪数", FarmIndicatorRecord::getWeanedPigletCount, ActivityAgg.SUM_INT),
        // 事件（SUM）
        new ActivityMetric("生长记录数", FarmIndicatorRecord::getGrowthRecordCount, ActivityAgg.SUM_INT),
        new ActivityMetric("阉割猪只数", FarmIndicatorRecord::getCastratePigCount, ActivityAgg.SUM_INT),
        new ActivityMetric("用药猪只数", FarmIndicatorRecord::getMedicatedPigCount, ActivityAgg.SUM_INT),
        // 死淘（SUM）
        new ActivityMetric("死亡猪只数", FarmIndicatorRecord::getDeathPigCount, ActivityAgg.SUM_INT),
        new ActivityMetric("淘汰猪只数", FarmIndicatorRecord::getCullingPigCount, ActivityAgg.SUM_INT),
        new ActivityMetric("死亡肥猪数", FarmIndicatorRecord::getDeathFatteningCount, ActivityAgg.SUM_INT),
        new ActivityMetric("死亡种母猪数", FarmIndicatorRecord::getDeathSowCount, ActivityAgg.SUM_INT),
        new ActivityMetric("死亡仔猪数", FarmIndicatorRecord::getDeathPigletCount, ActivityAgg.SUM_INT),
        // 出栏 / 生长（SUM 重/天 · NONE 均值）
        new ActivityMetric("出栏猪只数量", FarmIndicatorRecord::getMarketingPigCount, ActivityAgg.SUM_INT),
        new ActivityMetric("出栏总重", FarmIndicatorRecord::getMarketingWeight, ActivityAgg.SUM_DEC),
        new ActivityMetric("平均出栏重", FarmIndicatorRecord::getAvgMarketingWeight, ActivityAgg.NONE),
        new ActivityMetric("猪只断奶总重", FarmIndicatorRecord::getWeanTotalWeight, ActivityAgg.SUM_DEC),
        new ActivityMetric("生长总天数", FarmIndicatorRecord::getGrowthTotalDays, ActivityAgg.SUM_INT),
        new ActivityMetric("净增重", FarmIndicatorRecord::getNetGainWeight, ActivityAgg.SUM_DEC),
        new ActivityMetric("日增重", FarmIndicatorRecord::getDailyGainWeight, ActivityAgg.NONE),
        new ActivityMetric("平均背膘厚", FarmIndicatorRecord::getAvgBackfatThickness, ActivityAgg.NONE),
        // 期末存栏（LAST：累计列 = 当月最后一天值）
        new ActivityMetric("期末生产母猪头数", FarmIndicatorRecord::getEndProductionSowCount, ActivityAgg.LAST),
        new ActivityMetric("期末种公猪数", FarmIndicatorRecord::getEndBoarCount, ActivityAgg.LAST),
        new ActivityMetric("肥猪头数", FarmIndicatorRecord::getEndFatteningCount, ActivityAgg.LAST),
        new ActivityMetric("仔猪头数", FarmIndicatorRecord::getEndPigletCount, ActivityAgg.LAST),
        new ActivityMetric("后备猪头数", FarmIndicatorRecord::getEndReserveCount, ActivityAgg.LAST),
        new ActivityMetric("230日龄以上后备猪头数", FarmIndicatorRecord::getEndReserve230Count, ActivityAgg.LAST),
        new ActivityMetric("非生产状态母猪头数", FarmIndicatorRecord::getEndNonprodSowCount, ActivityAgg.LAST),
        // 其他（SUM）
        new ActivityMetric("当年配种批次分娩头数", FarmIndicatorRecord::getYearBatchFarrowCount, ActivityAgg.SUM_INT)
    );

    /**
     * 按指标定义 + 当月日表索引构造一行：daily 逐日格式化字符串，total 按聚合方式格式化。
     * days 为「昨日→月初」降序，故 days[0] = 当月时间最大日（昨日/月末）。
     * SUM 按日累加；LAST（期末存栏）取当月时间最大日的有值（= days 顺序里第一个非空格）；NONE（均值）累计留空串。
     */
    private MonthActivityVo.MonthRow buildIndicatorRow(ActivityMetric m, List<String> days,
                                                       Map<String, FarmIndicatorRecord> byDate) {
        List<String> daily = new ArrayList<>(days.size());
        BigDecimal sum = BigDecimal.ZERO;
        boolean anySum = false;
        String lastVal = "";          // 当月最后一天（时间最大）值：days 降序 → 第一个有值日即时间最大
        for (String day : days) {
            FarmIndicatorRecord r = byDate.get(day);
            Object raw = r == null ? null : m.getter().apply(r);
            String cell = formatCell(raw, m.agg());
            daily.add(cell);
            if ((m.agg() == ActivityAgg.SUM_INT || m.agg() == ActivityAgg.SUM_DEC) && raw instanceof Number n) {
                sum = sum.add(new BigDecimal(n.toString()));
                anySum = true;
            }
            if (m.agg() == ActivityAgg.LAST && lastVal.isEmpty() && !cell.isEmpty()) {
                // days 降序，第一个非空 = 当月时间最大日（昨日/月末）的值
                lastVal = cell;
            }
        }

        String total;
        switch (m.agg()) {
            case SUM_INT -> total = anySum ? formatInt(sum) : "";
            case SUM_DEC -> total = anySum ? formatDec(sum) : "";
            case LAST -> total = lastVal;
            default -> total = ""; // NONE 均值类累计留空串
        }

        MonthActivityVo.MonthRow row = new MonthActivityVo.MonthRow();
        row.setMetric(m.label());
        row.setDaily(daily);
        row.setTotal(total);
        return row;
    }

    /** 单元格格式化：整数列原样（"23"），DECIMAL 列 2 位小数（"12.50"），null → 空串。 */
    private String formatCell(Object raw, ActivityAgg agg) {
        if (raw == null) {
            return "";
        }
        if (agg == ActivityAgg.SUM_DEC || agg == ActivityAgg.NONE) {
            // DECIMAL 与均值类统一 2 位小数；整数型（生长总天数走 SUM_INT 不入此分支）
            if (raw instanceof BigDecimal bd) {
                return formatDec(bd);
            }
            if (raw instanceof Number n) {
                return formatDec(new BigDecimal(n.toString()));
            }
            return "";
        }
        // SUM_INT / LAST 整数列原样
        if (raw instanceof Number n) {
            return String.valueOf(n.longValue());
        }
        return Objects.toString(raw, "");
    }

    private String formatInt(BigDecimal v) {
        return String.valueOf(v.setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    private String formatDec(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ============================================================
    //  日情况概览 16 格（FIX-MGMT-MP-BRD-001）
    // ============================================================

    @Override
    public DailyOverviewVo getDailyOverview(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        String tenantId = currentTenant();
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        DailyOverviewVo vo = new DailyOverviewVo(date);
        List<DailyOverviewVo.OverviewCell> cells = vo.getCells();
        // 15 格，顺序 / 文案严格对齐原型（4f113e00 养殖场日情况概览，4+4+4+3 末行=生长记录/阉割/用药）。
        // 与 by-month 15 行同序同文案。死亡 / 淘汰按原型作"猪只数"（非母猪数）。无出栏格。
        cells.add(cell("分娩母猪数", aggregateQueryMapper.countEventInDay("t_farm_pig_farrow", "farrow_date", tenantId, from, to)));
        cells.add(cell("配种母猪数", aggregateQueryMapper.countEventInDay("t_farm_pig_breeding", "breeding_date", tenantId, from, to)));
        cells.add(cell("断奶母猪数", aggregateQueryMapper.countEventInDay("t_farm_pig_weaning", "weaning_date", tenantId, from, to)));
        cells.add(cell("返空流母猪数", aggregateQueryMapper.countAbnormalInRange(tenantId, from, to)));
        cells.add(cell("引种母猪数", aggregateQueryMapper.sumEventInDay("t_farm_pig_introduce", "introduce_date", "pig_count", tenantId, from, to)));
        cells.add(cell("查情不配种数", aggregateQueryMapper.countEventInDay("t_farm_pig_heat", "heat_date", tenantId, from, to)));
        cells.add(cell("死亡猪只数", aggregateQueryMapper.countStatusEventInDay(tenantId, "DIE", from, to)));
        cells.add(cell("淘汰猪只数", aggregateQueryMapper.countStatusEventInDay(tenantId, "ELIMINATE", from, to)));
        cells.add(cell("产仔数", aggregateQueryMapper.sumEventInDay("t_farm_pig_farrow", "farrow_date", "total_born", tenantId, from, to)));
        cells.add(cell("活仔数", aggregateQueryMapper.sumEventInDay("t_farm_pig_farrow", "farrow_date", "live_born", tenantId, from, to)));
        cells.add(cell("仔猪打标数", aggregateQueryMapper.countEventInDay("t_farm_pig_pigletno", "tag_date", tenantId, from, to)));
        cells.add(cell("断奶仔猪数", aggregateQueryMapper.sumEventInDay("t_farm_pig_weaning", "weaning_date", "weaned_count", tenantId, from, to)));
        cells.add(cell("生长记录数", aggregateQueryMapper.countEventInDay("t_farm_pig_growth", "measure_date", tenantId, from, to)));
        cells.add(cell("阉割猪只数", aggregateQueryMapper.countStatusEventInDay(tenantId, "CASTRATE", from, to)));
        cells.add(cell("用药猪只数", aggregateQueryMapper.countMedicatedPigInDay(tenantId, from, to)));
        return vo;
    }

    private DailyOverviewVo.OverviewCell cell(String metric, int value) {
        return new DailyOverviewVo.OverviewCell(metric, value);
    }

    // ============================================================
    //  年度繁殖与配种 + 产房仔猪质量（FIX-MGMT-MP-BRD-001，#7.1-7.5）
    // ============================================================

    @Override
    public BreedingAnnualVo getBreedingAnnual(Integer year) {
        if (year == null) {
            year = LocalDate.now().getYear();
        }
        String tenantId = currentTenant();
        LocalDateTime from = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime to = LocalDate.of(year + 1, 1, 1).atStartOfDay();

        int breedingCount = aggregateQueryMapper.countBreedingInRange(tenantId, from, to);
        int farrowLitter = aggregateQueryMapper.countFarrowLitterInRange(tenantId, from, to);
        int liveBorn = aggregateQueryMapper.sumLiveBornInDateTimeRange(tenantId, from, to);
        int weaningLitter = aggregateQueryMapper.countWeaningLitterInRange(tenantId, from, to);
        int weanedCount = aggregateQueryMapper.sumWeanedInDateTimeRange(tenantId, from, to);

        BreedingAnnualVo vo = new BreedingAnnualVo();
        vo.setYear(year);

        // #7.1 配种率 = 分娩窝数 / 配种次数
        vo.setMateRate(ratio(farrowLitter, breedingCount));
        // #7.2 分娩率 = 有效分娩窝数 / 配种次数（V1：有效分娩 = 分娩记录数）
        vo.setFarrowRate(ratio(farrowLitter, breedingCount));
        // #7.3 断配间隔 = AVG(下次配种 − 上次断奶)
        BigDecimal interval = aggregateQueryMapper.avgWeanMateIntervalDays(tenantId, from, to);
        vo.setWeanMateInterval(interval == null ? BigDecimal.ZERO : interval.setScale(1, RoundingMode.HALF_UP));
        // #7.4 平均非生产天数 NPD = 母猪年总天数 − 妊娠天数 − 哺乳天数；
        //      V1 近似：NPD = (年度天数 − 分娩窝数×妊娠期(114) − 断奶窝数×哺乳期(21)) / 当前活母猪数
        vo.setAvgNonProductiveDays(estimateNpd(tenantId, year, farrowLitter, weaningLitter));

        vo.setTotalLiveBorn(new BigDecimal(liveBorn));
        // 窝均活仔 = 活仔总数 / 分娩窝数
        vo.setAvgLiveBornPerLitter(avgPerLitter(liveBorn, farrowLitter));
        // 窝均断奶 = 断奶总数 / 断奶窝数
        vo.setAvgWeanedPerLitter(avgPerLitter(weanedCount, weaningLitter));
        // #7.5 产房损失率 = (分娩活仔 − 断奶数) / 分娩活仔
        if (liveBorn <= 0) {
            vo.setFarrowingLossRate(BigDecimal.ZERO);
        } else {
            int lost = Math.max(0, liveBorn - weanedCount);
            vo.setFarrowingLossRate(ratio(lost, liveBorn));
        }
        return vo;
    }

    /** NPD 近似：母猪群年累计天数 − 妊娠占用 − 哺乳占用，摊到当前活母猪头数。负值兜 0。 */
    private BigDecimal estimateNpd(String tenantId, int year, int farrowLitter, int weaningLitter) {
        int aliveSows = aggregateQueryMapper.countAliveSows(tenantId);
        if (aliveSows <= 0) {
            return BigDecimal.ZERO;
        }
        int daysInYear = java.time.Year.of(year).length();
        long totalSowDays = (long) daysInYear * aliveSows;
        long pregnantDays = (long) farrowLitter * GESTATION_DAYS;   // 每窝倒推 114 天妊娠
        long lactationDays = (long) weaningLitter * LACTATION_DAYS; // 每窝 21 天哺乳
        long npdTotal = totalSowDays - pregnantDays - lactationDays;
        if (npdTotal < 0) {
            npdTotal = 0;
        }
        return new BigDecimal(npdTotal)
            .divide(new BigDecimal(aliveSows), 1, RoundingMode.HALF_UP);
    }

    // ============================================================
    //  育肥猪日龄分布 / 实时库存（FIX-MGMT-MP-BRD-001，#7.6）
    // ============================================================

    @Override
    public List<AgeBucketVo> getFatteningAgeDistribution() {
        String tenantId = currentTenant();
        // 6 桶边界（#7.6）：保育期<43 / 43-70 / 71-135 / 136-210 / 211-245 / 245+
        int[] b0 = {0, 43, 71, 136, 211, 246};   // 下界（含）
        int[] b1 = {43, 71, 136, 211, 246, -1};  // 上界（不含），-1 = 无界
        String[] labels = {"保育期(<43天)", "43-70天", "71-135天", "136-210天", "211-245天", "245天以上"};
        int[] counts = new int[6];
        for (Map<String, Object> r : aggregateQueryMapper.selectFatteningAges(tenantId)) {
            Object a = r.get("age");
            if (a == null) {
                continue;
            }
            int age = ((Number) a).intValue();
            for (int i = 0; i < 6; i++) {
                boolean geLower = age >= b0[i];
                boolean ltUpper = b1[i] < 0 || age < b1[i];
                if (geLower && ltUpper) {
                    counts[i]++;
                    break;
                }
            }
        }
        List<AgeBucketVo> list = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            list.add(new AgeBucketVo(labels[i], counts[i]));
        }
        return list;
    }

    @Override
    public FatteningTrendVo getFatteningTrend(String period, String metric) {
        String p = "month".equalsIgnoreCase(period) ? "month" : "week";
        String m = metric == null ? "market" : metric.toLowerCase();
        if (!"market".equals(m) && !"target".equals(m) && !"onhand".equals(m)) {
            m = "market";
        }
        String tenantId = currentTenant();
        FatteningTrendVo vo = new FatteningTrendVo();
        vo.setPeriod(p);
        vo.setMetric(m);

        // 趋势窗口：周 → 近 8 周；月 → 近 6 个月（含当前）。
        LocalDate today = LocalDate.now();
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();

        if ("week".equals(p)) {
            LocalDate thisWeekMon = today.minusDays(today.getDayOfWeek().getValue() - 1L);
            LocalDate windowStart = thisWeekMon.minusWeeks(7);
            LocalDateTime from = windowStart.atStartOfDay();
            LocalDateTime to = thisWeekMon.plusWeeks(1).atStartOfDay();
            Map<String, Integer> byWeek = toLabelMap(aggregateQueryMapper.countMarketingByWeek(tenantId, from, to));
            for (int i = 0; i < 8; i++) {
                LocalDate weekMon = windowStart.plusWeeks(i);
                String label = weekMon.toString();
                labels.add(label);
                values.add(trendValue(m, byWeek.getOrDefault(label, 0), tenantId));
            }
        } else {
            YearMonth thisMonth = YearMonth.from(today);
            YearMonth windowStart = thisMonth.minusMonths(5);
            LocalDateTime from = windowStart.atDay(1).atStartOfDay();
            LocalDateTime to = thisMonth.plusMonths(1).atDay(1).atStartOfDay();
            Map<String, Integer> byMonth = toLabelMap(aggregateQueryMapper.countMarketingByMonth(tenantId, from, to));
            for (int i = 0; i < 6; i++) {
                YearMonth ym = windowStart.plusMonths(i);
                String label = ym.toString();
                labels.add(label);
                values.add(trendValue(m, byMonth.getOrDefault(label, 0), tenantId));
            }
        }
        vo.setLabels(labels);
        vo.setValues(values);
        vo.setLiveStock(buildFatteningLiveStock(tenantId));
        return vo;
    }

    /**
     * 按 metric 决定每个时间点的值：
     * market = 当期出栏头数；target = 固定出栏目标（V1 占位常量，可后续配置化）；
     * onhand = 当前育肥存栏（V1 简化为当前快照，每点同值）。
     */
    private BigDecimal trendValue(String metric, int marketCount, String tenantId) {
        return switch (metric) {
            case "target" -> new BigDecimal(FATTENING_TARGET_PER_PERIOD);
            case "onhand" -> new BigDecimal(aggregateQueryMapper.countFatteningOnHand(tenantId));
            default -> new BigDecimal(marketCount);
        };
    }

    private FatteningTrendVo.LiveStock buildFatteningLiveStock(String tenantId) {
        FatteningTrendVo.LiveStock ls = new FatteningTrendVo.LiveStock();
        ls.setFattening(aggregateQueryMapper.countFatteningOnHand(tenantId));
        // 保育存栏 = 育肥猪中 <43 天（#7.6 保育期边界）
        ls.setNursery(aggregateQueryMapper.countFatteningByAge(tenantId, -1, 43));
        // 可出栏 = 育肥猪中 ≥211 天（#7.6 育肥后期段起算）
        ls.setMarketable(aggregateQueryMapper.countFatteningByAge(tenantId, 211, -1));
        return ls;
    }

    private Map<String, Integer> toLabelMap(List<Map<String, Object>> raw) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Map<String, Object> r : raw) {
            Object d = r.get("d");
            Object v = r.get("v");
            if (d == null) {
                continue;
            }
            map.put(Objects.toString(d, ""), v instanceof Number n ? n.intValue() : 0);
        }
        return map;
    }

    // ============================================================
    //  当月生产统计表（率/窝均 10 行，#7.8）
    // ============================================================

    @Override
    public MonthlyProductionStatVo getMonthlyProductionStats(YearMonth yearMonth) {
        if (yearMonth == null) {
            yearMonth = YearMonth.now();
        }
        YearMonth prev = yearMonth.minusMonths(1);
        String tenantId = currentTenant();

        MonthRates curr = computeMonthRates(tenantId, yearMonth);
        MonthRates prevRates = computeMonthRates(tenantId, prev);

        MonthlyProductionStatVo vo = new MonthlyProductionStatVo();
        vo.setCurrentMonth(yearMonth.toString());
        vo.setPreviousMonth(prev.toString());
        List<MonthlyProductionStatVo.StatRow> rows = vo.getRows();
        rows.add(new MonthlyProductionStatVo.StatRow("分娩率", pct(curr.farrowRate), pct(prevRates.farrowRate), "%"));
        rows.add(new MonthlyProductionStatVo.StatRow("配怀率", pct(curr.mateRate), pct(prevRates.mateRate), "%"));
        rows.add(new MonthlyProductionStatVo.StatRow("断配间隔", curr.weanMateInterval, prevRates.weanMateInterval, "天"));
        rows.add(new MonthlyProductionStatVo.StatRow("返空流头数", new BigDecimal(curr.abnormalCount), new BigDecimal(prevRates.abnormalCount), "头"));
        rows.add(new MonthlyProductionStatVo.StatRow("平均非生产天数", curr.npd, prevRates.npd, "天"));
        rows.add(new MonthlyProductionStatVo.StatRow("总产仔数", new BigDecimal(curr.totalBorn), new BigDecimal(prevRates.totalBorn), "头"));
        rows.add(new MonthlyProductionStatVo.StatRow("窝均总产仔", avgPerLitter(curr.totalBorn, curr.farrowLitter), avgPerLitter(prevRates.totalBorn, prevRates.farrowLitter), "头/窝"));
        rows.add(new MonthlyProductionStatVo.StatRow("窝均活仔", avgPerLitter(curr.liveBorn, curr.farrowLitter), avgPerLitter(prevRates.liveBorn, prevRates.farrowLitter), "头/窝"));
        rows.add(new MonthlyProductionStatVo.StatRow("窝均断奶", avgPerLitter(curr.weanedCount, curr.weaningLitter), avgPerLitter(prevRates.weanedCount, prevRates.weaningLitter), "头/窝"));
        rows.add(new MonthlyProductionStatVo.StatRow("产房损失率", pct(curr.farrowingLossRate), pct(prevRates.farrowingLossRate), "%"));
        return vo;
    }

    /** 某月底表实时聚合的中间结果（供 10 行表 + 复用 BreedingAnnual 同套公式）。 */
    private static final class MonthRates {
        int breedingCount;
        int farrowLitter;
        int totalBorn;
        int liveBorn;
        int weaningLitter;
        int weanedCount;
        int abnormalCount;
        BigDecimal mateRate = BigDecimal.ZERO;
        BigDecimal farrowRate = BigDecimal.ZERO;
        BigDecimal weanMateInterval = BigDecimal.ZERO;
        BigDecimal npd = BigDecimal.ZERO;
        BigDecimal farrowingLossRate = BigDecimal.ZERO;
    }

    private MonthRates computeMonthRates(String tenantId, YearMonth ym) {
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();
        MonthRates r = new MonthRates();
        r.breedingCount = aggregateQueryMapper.countBreedingInRange(tenantId, from, to);
        r.farrowLitter = aggregateQueryMapper.countFarrowLitterInRange(tenantId, from, to);
        r.totalBorn = aggregateQueryMapper.sumTotalBornInRange(tenantId, from, to);
        r.liveBorn = aggregateQueryMapper.sumLiveBornInDateTimeRange(tenantId, from, to);
        r.weaningLitter = aggregateQueryMapper.countWeaningLitterInRange(tenantId, from, to);
        r.weanedCount = aggregateQueryMapper.sumWeanedInDateTimeRange(tenantId, from, to);
        r.abnormalCount = aggregateQueryMapper.countAbnormalInRange(tenantId, from, to);
        r.mateRate = ratio(r.farrowLitter, r.breedingCount);
        r.farrowRate = ratio(r.farrowLitter, r.breedingCount);
        BigDecimal interval = aggregateQueryMapper.avgWeanMateIntervalDays(tenantId, from, to);
        r.weanMateInterval = interval == null ? BigDecimal.ZERO : interval.setScale(1, RoundingMode.HALF_UP);
        r.npd = estimateNpd(tenantId, ym.getYear(), r.farrowLitter, r.weaningLitter);
        if (r.liveBorn > 0) {
            int lost = Math.max(0, r.liveBorn - r.weanedCount);
            r.farrowingLossRate = ratio(lost, r.liveBorn);
        }
        return r;
    }

    // ============================================================
    //  rate / 窝均 helpers
    // ============================================================

    /** 妊娠期天数（NPD 近似用）。 */
    private static final int GESTATION_DAYS = 114;
    /** 哺乳期天数（NPD 近似用）。 */
    private static final int LACTATION_DAYS = 21;
    /** 育肥出栏目标占位常量（V1，target 趋势用；可后续配置化）。 */
    private static final int FATTENING_TARGET_PER_PERIOD = 100;

    /** 比率 = numerator / denominator，4 位小数，denom=0 → 0。 */
    private static BigDecimal ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), 4, RoundingMode.HALF_UP);
    }

    /** 窝均 = total / litter，2 位小数，litter=0 → 0。 */
    private static BigDecimal avgPerLitter(int total, int litter) {
        if (litter <= 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(total).divide(new BigDecimal(litter), 2, RoundingMode.HALF_UP);
    }

    /** 比率 0~1 → 百分比数值（×100，2 位小数）。前端直接拼 "%"。 */
    private static BigDecimal pct(BigDecimal ratio01) {
        if (ratio01 == null) {
            return BigDecimal.ZERO;
        }
        return ratio01.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
    }

    // ============================================================
    //  BRD-STAT-001 落盘用 helpers（Map 取值 / 除法 / 精度）
    // ============================================================

    /** Map 取整型值（缺省 0）。 */
    private static int mapInt(Map<String, Object> m, String key) {
        if (m == null) {
            return 0;
        }
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** Map 取 BigDecimal 值（缺省 0）。 */
    private static BigDecimal mapBd(Map<String, Object> m, String key) {
        if (m == null) {
            return BigDecimal.ZERO;
        }
        Object v = m.get(key);
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return BigDecimal.ZERO;
    }

    /** 安全除：denom&lt;=0 返 0；保留 6 位中间精度，外层再 setScale。 */
    private static BigDecimal divide(BigDecimal num, int denom) {
        if (num == null || denom <= 0) {
            return BigDecimal.ZERO;
        }
        return num.divide(new BigDecimal(denom), 6, RoundingMode.HALF_UP);
    }

    /** 3 位小数（重量 / 率 / 天数落库统一精度）。 */
    private static BigDecimal scale3(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(3, RoundingMode.HALF_UP);
    }

    /** 2 位小数（母猪性能怀孕天数 / 断配天数 / NPD，邓博 row11 要求保留两位）。 */
    private static BigDecimal scale2(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** 存在则复用（保留 id + del 字段，updateById 走 UPDATE）；否则 new（id=null，走 insert）。 */
    private static <T> T existingOrNew(T existing, java.util.function.Supplier<T> factory) {
        return existing != null ? existing : factory.get();
    }

    // ============================================================
    //  历史日表只读端点（BRD-STAT-001）
    // ============================================================

    @Override
    public List<FarmIndicatorRecordVo> listIndicatorRecords(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<FarmIndicatorRecord> q = new LambdaQueryWrapper<FarmIndicatorRecord>()
            .ge(FarmIndicatorRecord::getStatDate, start)
            .le(FarmIndicatorRecord::getStatDate, end)
            .orderByAsc(FarmIndicatorRecord::getStatDate);
        return farmIndicatorRecordMapper.selectVoList(q);
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

        // 顺序：日表（farm_indicator + sow_record）→ 母猪性能 → 月 → 年。
        // 月/年高级指标从已落盘的 farm_indicator 日表 Σ 回读，故日表必须先写。
        upsertFarmIndicator(tenantId, targetDate);
        upsertSowRecord(tenantId, targetDate);
        upsertSowPerformance(tenantId, targetDate);
        upsertMonthlyProduction(tenantId, YearMonth.from(targetDate));
        upsertAnnualIndicator(tenantId, (short) targetDate.getYear());

        log.info("[DashboardAggregate] done tenant={} date={}", tenantId, targetDate);
        return String.format("ok | tenant=%s | date=%s | tables=[indicator_record, sow_record, sow_performance, monthly_production, annual_indicator]",
            tenantId, targetDate);
    }

    /**
     * 落盘养殖农场日数据 t_farm_indicator_record（BRD-STAT-001，row10 全 30+ 列）。
     *
     * <p>事件类按 statDate 自然日 [00:00, 次日 00:00) 区间聚合；期末存栏取 T-1 当日 current_status 快照
     * （A#4：不做历史回算，直接读 t_farm_pig_info 当前态）。BigDecimal 全部 setScale(3) 控精度。</p>
     */
    private void upsertFarmIndicator(String tenantId, LocalDate statDate) {
        LocalDate dayFrom = statDate;
        LocalDate dayTo = statDate.plusDays(1);
        LocalDateTime dtFrom = statDate.atStartOfDay();
        LocalDateTime dtTo = statDate.plusDays(1).atStartOfDay();
        int batchYear = statDate.getYear();

        FarmIndicatorRecord r = new FarmIndicatorRecord();
        r.setStatDate(statDate);

        // ---- 当日事件类（头数） ----
        r.setFarrowSowCount(aggregateQueryMapper.countDistinctEventInDay("t_farm_pig_farrow", "farrow_date", "pig_id", tenantId, dayFrom, dayTo));
        r.setBreedingSowCount(aggregateQueryMapper.countDistinctEventInDay("t_farm_pig_breeding", "breeding_date", "pig_id", tenantId, dayFrom, dayTo));
        r.setWeaningSowCount(aggregateQueryMapper.countDistinctEventInDay("t_farm_pig_weaning", "weaning_date", "pig_id", tenantId, dayFrom, dayTo));
        r.setAbnormalSowCount(aggregateQueryMapper.countDistinctAbnormalSowInRange(tenantId, dtFrom, dtTo));
        r.setIntroduceSowCount(aggregateQueryMapper.sumIntroducedSowInRange(tenantId, dayFrom, dayTo));
        r.setHeatNoBreedCount(aggregateQueryMapper.countHeatNoBreedInDay(tenantId, dayFrom, dayTo));
        r.setDeathPigCount(aggregateQueryMapper.countStatusEventInRange(tenantId, "DIE", dtFrom, dtTo));
        r.setCullingPigCount(aggregateQueryMapper.countStatusEventInRange(tenantId, "ELIMINATE", dtFrom, dtTo));
        r.setTotalBornCount(aggregateQueryMapper.sumEventInDayDate("t_farm_pig_farrow", "farrow_date", "total_born", tenantId, dayFrom, dayTo));
        r.setLiveBornCount(aggregateQueryMapper.sumLiveBornInRange(tenantId, dayFrom, dayTo));
        r.setPigletTagCount(aggregateQueryMapper.countDistinctEventInDay("t_farm_pig_pigletno", "tag_date", "id", tenantId, dayFrom, dayTo));
        r.setWeanedPigletCount(aggregateQueryMapper.sumWeanedInRange(tenantId, dayFrom, dayTo));
        r.setGrowthRecordCount(aggregateQueryMapper.countDistinctEventInDay("t_farm_pig_growth", "measure_date", "id", tenantId, dayFrom, dayTo));
        r.setCastratePigCount(aggregateQueryMapper.countStatusEventInRange(tenantId, "CASTRATE", dtFrom, dtTo));
        r.setMedicatedPigCount(aggregateQueryMapper.countMedicatedPigInDay(tenantId, dtFrom, dtTo));

        // ---- 当日出栏聚合（头数 / 总重 / 背膘） ----
        Map<String, Object> mkt = aggregateQueryMapper.aggregateMarketingForDay(tenantId, dtFrom, dtTo);
        int marketingCount = mapInt(mkt, "cnt");
        BigDecimal marketingWeight = mapBd(mkt, "weight");
        BigDecimal backfatSum = mapBd(mkt, "backfatSum");
        int backfatCnt = mapInt(mkt, "backfatCnt");
        r.setMarketingPigCount(marketingCount);
        r.setMarketingWeight(scale3(marketingWeight));
        r.setAvgMarketingWeight(scale3(divide(marketingWeight, marketingCount)));
        r.setAvgBackfatThickness(scale3(divide(backfatSum, backfatCnt)));

        // ---- 当日出栏猪断奶总重 + 生长总天数 → 净增重 / 日增重 ----
        // 净增重 = Σ(出栏重 − 断奶重) 仅对「有断奶快照的出栏育肥猪」同一集合，
        // 故被减数用同集合的出栏重 marketingWeightWeaned（非全部出栏 marketingWeight，
        // 后者含淘汰母猪/种猪出栏会让净增重虚高）。出栏总重列仍取全量 marketingWeight。
        Map<String, Object> weanAgg = aggregateQueryMapper.aggregateMarketingWeanForDay(tenantId, dtFrom, dtTo);
        BigDecimal weanTotalWeight = mapBd(weanAgg, "weanWeightSum");
        int growthDays = mapInt(weanAgg, "growthDaysSum");
        BigDecimal marketingWeightWeaned = mapBd(weanAgg, "marketingWeightWeaned");
        BigDecimal netGain = marketingWeightWeaned.subtract(weanTotalWeight);
        r.setWeanTotalWeight(scale3(weanTotalWeight));
        r.setGrowthTotalDays(growthDays);
        r.setNetGainWeight(scale3(netGain));
        r.setDailyGainWeight(scale3(divide(netGain, growthDays)));

        // ---- 死亡分类（按 pig_type） ----
        r.setDeathFatteningCount(aggregateQueryMapper.countDeathByPigTypeInDay(tenantId, "fattening", dtFrom, dtTo));
        r.setDeathSowCount(aggregateQueryMapper.countDeathByPigTypeInDay(tenantId, "sow", dtFrom, dtTo));
        r.setDeathPigletCount(aggregateQueryMapper.countDeathByPigTypeInDay(tenantId, "piglet", dtFrom, dtTo));

        // ---- 期末存栏快照（T-1 当日 current_status 当前快照，A#4 不回算） ----
        fillEndStock(r, tenantId, statDate);

        // ---- 当年配种批次分娩头数 ----
        // 配种→分娩天数读 sow_breed_to_farrow_days，缺省 114
        r.setYearBatchFarrowCount(aggregateQueryMapper.sumYearBatchFarrowForDay(tenantId, dayFrom, dayTo, batchYear, breedToFarrowDays()));

        // UPSERT
        FarmIndicatorRecord existing = farmIndicatorRecordMapper.selectOne(
            new LambdaQueryWrapper<FarmIndicatorRecord>().eq(FarmIndicatorRecord::getStatDate, statDate));
        if (existing == null) {
            r.setDelFlag("0");
            r.setDelUnique(0L);
            farmIndicatorRecordMapper.insert(r);
        } else {
            r.setId(existing.getId());
            farmIndicatorRecordMapper.updateById(r);
        }
    }

    /**
     * 填期末存栏 7 项快照（按 pig_type + current_status 一次分组查回，service 端汇总）。
     *
     * <p>口径（ADR-0016 解耦：current_status 是种母猪繁殖态轴，非种母猪平时空串）：</p>
     * <ul>
     *   <li>生产母猪头数 = sow 且 status ∈ {PZ,FM,DN,LC,KH,FQ}（非后备 HB、非终止 END、非空串）；</li>
     *   <li>后备母猪 = sow 且 status='HB'；非生产母猪 = sow 且 status ∈ {KH,FQ,LC,DN}；</li>
     *   <li>种公猪 = boar、肥猪 = fattening、仔猪 = piglet（status&lt;&gt;'END' 已在查询过滤）。</li>
     * </ul>
     */
    private void fillEndStock(FarmIndicatorRecord r, String tenantId, LocalDate asOf) {
        int prodSow = 0, reserveSow = 0, nonprodSow = 0, boar = 0, fattening = 0, piglet = 0;
        for (Map<String, Object> row : aggregateQueryMapper.snapshotByTypeStatus(tenantId)) {
            String type = Objects.toString(row.get("pigType"), "");
            String cs = Objects.toString(row.get("cs"), "");
            int cnt = mapInt(row, "cnt");
            switch (type) {
                case "sow" -> {
                    if ("HB".equals(cs)) {
                        reserveSow += cnt;
                    } else if (!cs.isEmpty()) {
                        // PZ/FM/DN/LC/KH/FQ 均算「生产母猪」（非后备非终止非空）
                        prodSow += cnt;
                        if ("KH".equals(cs) || "FQ".equals(cs) || "LC".equals(cs) || "DN".equals(cs)) {
                            nonprodSow += cnt;
                        }
                    }
                }
                case "boar" -> boar += cnt;
                case "fattening" -> fattening += cnt;
                case "piglet" -> piglet += cnt;
                default -> {
                    // 未知 pig_type 不计入任何期末项
                }
            }
        }
        r.setEndProductionSowCount(prodSow);
        r.setEndReserveCount(reserveSow);
        r.setEndNonprodSowCount(nonprodSow);
        r.setEndBoarCount(boar);
        r.setEndFatteningCount(fattening);
        r.setEndPigletCount(piglet);
        r.setEndReserve230Count(aggregateQueryMapper.countReserve230(tenantId, asOf));
    }

    /**
     * 刷新母猪性能 t_farm_sow_performance（BRD-DASH-002 落地，邓博 row11）。
     *
     * <p>扫全部活母猪（pig_type='sow' 且未终止），每头按 farrow / weaning / abnormal / breeding 记录
     * 聚合一行（每头一行，parity 写当前胎次）。累计 + 窝均 + 平均怀孕/断配/NPD。
     * 无源数据的指标置 null（不瞎编），不让整体变 null。</p>
     */
    private void upsertSowPerformance(String tenantId, LocalDate statDate) {
        List<Map<String, Object>> sows = aggregateQueryMapper.selectAliveSows(tenantId);
        for (Map<String, Object> sow : sows) {
            Long pigId = ((Number) sow.get("id")).longValue();
            String earNo = Objects.toString(sow.get("earNo"), "");
            Integer parity = sow.get("parity") == null ? 0 : ((Number) sow.get("parity")).intValue();

            Map<String, Object> fa = aggregateQueryMapper.sowFarrowAgg(tenantId, pigId);
            int totalBorn = mapInt(fa, "totalBorn");
            int totalLiveBorn = mapInt(fa, "totalLiveBorn");
            int litterCount = mapInt(fa, "litterCount");
            BigDecimal sumAvgBornWeight = mapBd(fa, "sumAvgBornWeight");
            int gestSum = mapInt(fa, "gestSum");
            int gestCount = mapInt(fa, "gestCount");

            Map<String, Object> we = aggregateQueryMapper.sowWeanAgg(tenantId, pigId);
            int totalWeaned = mapInt(we, "totalWeaned");
            int weanCount = mapInt(we, "weanCount");
            BigDecimal sumAvgWeanedWeight = mapBd(we, "sumAvgWeanedWeight");

            int abnormalTotal = aggregateQueryMapper.sowAbnormalCount(tenantId, pigId);

            Map<String, Object> wb = aggregateQueryMapper.sowWeanBreedAgg(tenantId, pigId);
            int wbSum = mapInt(wb, "sumDays");
            int wbCnt = mapInt(wb, "cnt");

            SowPerformance sp = new SowPerformance();
            sp.setPigId(pigId);
            sp.setEarNo(earNo);
            sp.setParity(parity);
            sp.setTotalBorn(totalBorn);
            sp.setTotalLiveBorn(totalLiveBorn);
            sp.setTotalWeaned(totalWeaned);
            // 平均出生重 = Σ分娩 avg_weight / 分娩窝数；平均断奶重 = Σ断奶 avg_weaned_weight / 断奶批数
            sp.setAvgBornWeight(litterCount > 0 ? scale3(divide(sumAvgBornWeight, litterCount)) : null);
            sp.setAvgWeanedWeight(weanCount > 0 ? scale3(divide(sumAvgWeanedWeight, weanCount)) : null);
            sp.setLastUpdateDate(statDate);

            // row11 指标算法列
            sp.setAvgGestationDays(gestCount > 0 ? scale2(divide(new BigDecimal(gestSum), gestCount)) : null);
            sp.setWeanBreedDays(wbCnt > 0 ? scale2(divide(new BigDecimal(wbSum), wbCnt)) : null);
            sp.setAbnormalTotal(abnormalTotal);
            // 窝均按胎次（parity）；parity=0 时退化用窝数兜底，避免除 0
            int litterDenom = parity != null && parity > 0 ? parity : litterCount;
            sp.setAvgBornPerLitter(litterDenom > 0 ? scale3(divide(new BigDecimal(totalBorn), litterDenom)) : null);
            sp.setAvgLiveBornPerLitter(litterDenom > 0 ? scale3(divide(new BigDecimal(totalLiveBorn), litterDenom)) : null);
            int weanDenom = parity != null && parity > 0 ? parity : weanCount;
            sp.setAvgWeanedPerLitter(weanDenom > 0 ? scale3(divide(new BigDecimal(totalWeaned), weanDenom)) : null);
            // NPD = 365 − 配种至分娩天数 − 分娩至断奶天数（均取该母猪平均）；缺基准 → null
            BigDecimal avgFarrowWean = aggregateQueryMapper.avgFarrowWeanDays(tenantId, pigId);
            sp.setNpd(computeSowNpd(gestSum, gestCount, avgFarrowWean));

            SowPerformance existing = sowPerformanceMapper.selectOne(
                new LambdaQueryWrapper<SowPerformance>().eq(SowPerformance::getPigId, pigId));
            if (existing == null) {
                sp.setDelFlag("0");
                sp.setDelUnique(0L);
                sowPerformanceMapper.insert(sp);
            } else {
                sp.setId(existing.getId());
                sowPerformanceMapper.updateById(sp);
            }
        }
    }

    /**
     * NPD = 365 − 平均怀孕天数 − 平均(分娩→断奶)天数（邓博 row11：365−配种至分娩−分娩至断奶）。
     * 第三项「分娩至断奶天数」= 该母猪 farrow→weaning 配对的 AVG(DATEDIFF(weaning_date, farrow_date))；
     * 无断奶配对（哺乳未结束）则该项用 0。
     * 怀孕天数缺（无配种-分娩配对）→ 整体 null（无基准不瞎编）。
     */
    private BigDecimal computeSowNpd(int gestSum, int gestCount, BigDecimal avgFarrowWeanDays) {
        if (gestCount <= 0) {
            return null;
        }
        BigDecimal avgGest = divide(new BigDecimal(gestSum), gestCount);
        BigDecimal avgLactation = avgFarrowWeanDays != null ? avgFarrowWeanDays : BigDecimal.ZERO;
        return scale2(new BigDecimal(365).subtract(avgGest).subtract(avgLactation));
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

        // ---- row13 高级指标（从已落盘 farm_indicator 日表 Σ 回读 + 部分实时算） ----
        Map<String, Object> sum = aggregateQueryMapper.sumIndicatorRange(tenantId, from, to);
        int sumFarrowSow = mapInt(sum, "sumFarrowSow");
        int sumWeaningSow = mapInt(sum, "sumWeaningSow");
        int sumTotalBorn = mapInt(sum, "sumTotalBorn");
        int sumLiveBorn = mapInt(sum, "sumLiveBorn");
        int sumWeanedPiglet = mapInt(sum, "sumWeanedPiglet");
        int sumAbnormal = mapInt(sum, "sumAbnormal");
        int sumDeathPiglet = mapInt(sum, "sumDeathPiglet");
        int sumEndProdSow = mapInt(sum, "sumEndProductionSow");
        int sumEndReserve230 = mapInt(sum, "sumEndReserve230");
        int sumEndReserve = mapInt(sum, "sumEndReserve"); // 全量后备（含未满 230 日龄）
        int sumEndNonprodSow = mapInt(sum, "sumEndNonprodSow");

        int daysInMonth = month.lengthOfMonth();
        // 累计匹配配种窝数 = [from-N, to-N) 配种记录数（Σ日配种母猪数，不去重）
        //   N = 配种→分娩天数，读 sow_breed_to_farrow_days，缺省 114
        int mateLitter = aggregateQueryMapper.countMateLitterShifted(tenantId, dtFrom, dtTo, breedToFarrowDays());
        // 当月配种母猪头数（实时，COUNT(DISTINCT pig_id) —— spec「配种母猪头数」按头去重）
        int breedingSowCount = aggregateQueryMapper.countDistinctBreedingSowInRange(tenantId, dtFrom, dtTo);

        // 分娩率% = 当月分娩头数 / 累计匹配配种窝数 × 100
        BigDecimal farrowRate = pct(ratio(sumFarrowSow, mateLitter));
        // 配种率% = 当月配种母猪头数 / ((Σ日生产母猪 + Σ日230后备)/当月天数) × 100
        //   配种率分母用 230 后备（与 NPD 分母「全后备」不同口径，两公式后备口径不共用变量）
        BigDecimal breedDenom = divide(new BigDecimal(sumEndProdSow + sumEndReserve230), daysInMonth);
        BigDecimal breedRate = breedDenom.signum() == 0
            ? BigDecimal.ZERO
            : scale3(new BigDecimal(breedingSowCount).divide(breedDenom, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100")));
        // 断配间隔（实时配对）
        Map<String, Object> wbMonth = aggregateQueryMapper.sumWeanMateIntervalRange(tenantId, dtFrom, dtTo);
        int wbDays = mapInt(wbMonth, "totalDays");
        int wbCnt = mapInt(wbMonth, "totalCount");
        BigDecimal weanBreedInterval = wbCnt > 0 ? scale3(divide(new BigDecimal(wbDays), wbCnt)) : BigDecimal.ZERO;
        // 月均NPD天数 = (Σ日230后备 + Σ日非生产母猪) / ((Σ日生产母猪+Σ日全后备)/当月天数)
        //   NPD 分母后备口径 = 全部后备（end_reserve_count），不是 230 后备（spec row13 T6）
        BigDecimal npdDenom = divide(new BigDecimal(sumEndProdSow + sumEndReserve), daysInMonth);
        BigDecimal npdDays = npdDenom.signum() == 0
            ? BigDecimal.ZERO
            : scale3(new BigDecimal(sumEndReserve230 + sumEndNonprodSow).divide(npdDenom, 6, RoundingMode.HALF_UP));
        // 窝均
        BigDecimal avgBornPerLitter = scale3(divide(new BigDecimal(sumTotalBorn), sumFarrowSow));
        BigDecimal avgLiveBornPerLitter = scale3(divide(new BigDecimal(sumLiveBorn), sumFarrowSow));
        BigDecimal avgWeanedPerLitter = scale3(divide(new BigDecimal(sumWeanedPiglet), sumWeaningSow));
        // 分娩舍损失率% = 当月死亡仔猪 / 当月总活仔
        BigDecimal farrowLossRate = pct(ratio(sumDeathPiglet, sumLiveBorn));

        MonthlyProduction m = existingOrNew(selectMonth(tenantId, month), MonthlyProduction::new);
        m.setStatMonth(month.toString());
        m.setIntroduceCount(introduceCount);
        m.setBornCount(bornCount);
        m.setWeanedCount(weanedCount);
        m.setDeathCount(deathCount);
        m.setCullingCount(cullingCount);
        m.setMarketingCount(marketingCount);
        m.setMarketingWeight(marketingWeight);
        m.setMateLitterCount(mateLitter);
        m.setFarrowRate(farrowRate);
        m.setBreedRate(breedRate);
        m.setWeanBreedInterval(weanBreedInterval);
        m.setAbnormalCount(sumAbnormal);
        m.setNpdDays(npdDays);
        m.setTotalBornCount(sumTotalBorn);
        m.setAvgBornPerLitter(avgBornPerLitter);
        m.setAvgLiveBornPerLitter(avgLiveBornPerLitter);
        m.setAvgWeanedPerLitter(avgWeanedPerLitter);
        m.setFarrowLossRate(farrowLossRate);
        if (m.getId() == null) {
            m.setDelFlag("0");
            m.setDelUnique(0L);
            monthlyProductionMapper.insert(m);
        } else {
            monthlyProductionMapper.updateById(m);
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

        // 死亡率 = DEATH / (DEATH + alive sows)（4 位小数）
        int aliveSows = aggregateQueryMapper.countAliveSows(tenantId);
        int denom = deathCount + aliveSows;
        BigDecimal mortalityRate = denom == 0
            ? BigDecimal.ZERO
            : new BigDecimal(deathCount).divide(new BigDecimal(denom), 4, RoundingMode.HALF_UP);

        // ---- row14 高级指标（从当年已落盘 farm_indicator 日表 Σ 回读 + 部分实时算） ----
        Map<String, Object> sum = aggregateQueryMapper.sumIndicatorRange(tenantId, from, to);
        int sumFarrowSow = mapInt(sum, "sumFarrowSow");
        int sumTotalBorn = mapInt(sum, "sumTotalBorn");
        int sumLiveBorn = mapInt(sum, "sumLiveBorn");
        int sumWeaningSow = mapInt(sum, "sumWeaningSow");
        int sumWeanedPiglet = mapInt(sum, "sumWeanedPiglet");
        int sumDeathPiglet = mapInt(sum, "sumDeathPiglet");
        int sumDeathFattening = mapInt(sum, "sumDeathFattening"); // T7 肥猪死亡数
        int sumMarketingCount = mapInt(sum, "sumMarketingCount");
        BigDecimal sumMarketingWeight = mapBd(sum, "sumMarketingWeight");
        int sumEndProdSow = mapInt(sum, "sumEndProductionSow");
        int sumEndReserve230 = mapInt(sum, "sumEndReserve230");
        int sumEndNonprodSow = mapInt(sum, "sumEndNonprodSow");
        int sumYearBatchFarrow = mapInt(sum, "sumYearBatchFarrow");

        // 已历天数 = 当年日表已落盘行数（年初到 T-1）；为 0 时分母兜底为 1 避免除 0
        int daysElapsed = aggregateQueryMapper.countIndicatorDays(tenantId, from, to);
        // 年均能繁母猪存栏 = (Σ日生产母猪 + Σ日230后备) / 已历天数
        BigDecimal avgBreedingSowStock = scale3(divide(new BigDecimal(sumEndProdSow + sumEndReserve230), daysElapsed));

        // 年配种头数（实时）
        int breedingCount = aggregateQueryMapper.countBreedingInRange(tenantId, dtFrom, dtTo);
        // 年配种率 = 年配种头数 / 年均能繁母猪存栏
        BigDecimal breedRate = avgBreedingSowStock.signum() == 0
            ? BigDecimal.ZERO
            : scale3(new BigDecimal(breedingCount).divide(avgBreedingSowStock, 6, RoundingMode.HALF_UP));

        // 窝均活仔 = 总活仔 / 年分娩次数；窝均断奶 = 总断奶仔猪 / 总断奶母猪
        BigDecimal avgLiveBornPerLitter = scale3(divide(new BigDecimal(sumLiveBorn), sumFarrowSow));
        BigDecimal avgWeanedPerLitter = scale3(divide(new BigDecimal(sumWeanedPiglet), sumWeaningSow));

        // 断配间隔（实时配对）
        Map<String, Object> wbYear = aggregateQueryMapper.sumWeanMateIntervalRange(tenantId, dtFrom, dtTo);
        int wbDays = mapInt(wbYear, "totalDays");
        int wbCnt = mapInt(wbYear, "totalCount");
        BigDecimal weanBreedInterval = wbCnt > 0 ? scale3(divide(new BigDecimal(wbDays), wbCnt)) : BigDecimal.ZERO;

        // 全年总NPD天数 = Σ日230后备 + Σ日非生产母猪；年均NPD = 总NPD / 年均能繁存栏
        int totalNpdDays = sumEndReserve230 + sumEndNonprodSow;
        BigDecimal avgNpdDays = avgBreedingSowStock.signum() == 0
            ? BigDecimal.ZERO
            : scale3(new BigDecimal(totalNpdDays).divide(avgBreedingSowStock, 6, RoundingMode.HALF_UP));

        // 年分娩率% = 年分娩头数 / 年配种头数 × 100
        BigDecimal yearFarrowRate = pct(ratio(sumYearBatchFarrow, breedingCount));
        // 平均出栏重 = Σ日出栏总重 / Σ日出栏头数
        BigDecimal avgMarketingWeight = scale3(divide(sumMarketingWeight, sumMarketingCount));
        // 分娩舍损失率% = 当年死亡仔猪 / 总活仔
        BigDecimal farrowLossRate = pct(ratio(sumDeathPiglet, sumLiveBorn));
        // PSY = (年分娩头数 / 年均能繁母存栏) × 窝均断奶数（邓博 row14 新口径）
        BigDecimal psy = avgBreedingSowStock.signum() == 0
            ? BigDecimal.ZERO
            : scale3(new BigDecimal(sumYearBatchFarrow)
                .divide(avgBreedingSowStock, 6, RoundingMode.HALF_UP)
                .multiply(avgWeanedPerLitter));

        AnnualIndicator a = existingOrNew(selectYear(tenantId, year), AnnualIndicator::new);
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
        a.setAvgBreedingSowStock(avgBreedingSowStock);
        a.setBreedingCount(breedingCount);
        a.setBreedRate(breedRate);
        a.setTotalBornCount(sumTotalBorn);
        a.setFarrowCount(sumFarrowSow);
        a.setTotalLiveBorn(sumLiveBorn);
        a.setAvgLiveBornPerLitter(avgLiveBornPerLitter);
        a.setTotalWeanedPiglet(sumWeanedPiglet);
        a.setTotalWeanedSow(sumWeaningSow);
        a.setAvgWeanedPerLitter(avgWeanedPerLitter);
        a.setWeanBreedTotalDays(wbDays);
        a.setWeanBreedTotalCount(wbCnt);
        a.setWeanBreedInterval(weanBreedInterval);
        a.setTotalNpdDays(totalNpdDays);
        a.setAvgNpdDays(avgNpdDays);
        a.setYearBatchFarrowCount(sumYearBatchFarrow);
        a.setYearFarrowRate(yearFarrowRate);
        a.setAvgMarketingWeight(avgMarketingWeight);
        a.setFarrowLossRate(farrowLossRate);
        a.setTotalFatteningDeath(sumDeathFattening);
        if (a.getId() == null) {
            a.setDelFlag("0");
            a.setDelUnique(0L);
            annualIndicatorMapper.insert(a);
        } else {
            annualIndicatorMapper.updateById(a);
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
