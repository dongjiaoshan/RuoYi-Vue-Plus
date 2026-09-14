package org.dromara.djs.breed.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.SpringUtils;
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
import org.dromara.djs.breed.dashboard.domain.vo.CohortLedgerVo;
import org.dromara.djs.breed.dashboard.domain.vo.DailyOverviewVo;
import org.dromara.djs.breed.dashboard.domain.vo.FarmIndicatorRecordVo;
import org.dromara.djs.breed.dashboard.domain.vo.FatteningTrendVo;
import org.dromara.djs.breed.dashboard.domain.vo.InventoryVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthActivityVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthlyComparisonVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthlyProductionStatVo;
import org.dromara.djs.breed.dashboard.domain.vo.OverdueUndecidedVo;
import org.dromara.djs.breed.dashboard.mapper.AggregateQueryMapper;
import org.dromara.djs.breed.dashboard.mapper.AnnualIndicatorMapper;
import org.dromara.djs.breed.dashboard.mapper.FarmIndicatorRecordMapper;
import org.dromara.djs.breed.dashboard.mapper.MonthlyProductionMapper;
import org.dromara.djs.breed.dashboard.mapper.SowRecordMapper;
import org.dromara.djs.breed.dashboard.service.IDashboardService;
import org.dromara.djs.breed.production.domain.vo.FattenAgeStageVo;
import org.dromara.djs.breed.production.mapper.SowPerformanceMapper;
import org.dromara.djs.breed.production.service.IFattenAgeStageService;
import org.dromara.djs.breed.production.service.IProductionCycleConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final IFattenAgeStageService fattenAgeStageService;

    /** 配种→分娩天数配置键（row183）：缺省 114 天。 */
    private static final String CONFIG_KEY_BREED_TO_FARROW = "sow_breed_to_farrow_days";
    /** 配种→分娩天数缺省值（配置 key 缺失时 fallback）。 */
    private static final int DEFAULT_BREED_TO_FARROW_DAYS = 114;

    /** 分娩判定节点配置键（BRD-STAT-COHORT-001）。 */
    private static final String CONFIG_KEY_FARROW_JUDGE_DEADLINE = "sow_farrow_judge_deadline_days";
    private static final int DEFAULT_FARROW_JUDGE_DEADLINE_DAYS = 119;
    /** 年化乘数分子（PSY / 平均非生产天数按「头/母猪·年」「天/年」口径展示）。 */
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");

    /** 取配种→分娩天数：读 {@code sow_breed_to_farrow_days}，缺则回退 114。 */
    private int breedToFarrowDays() {
        Integer d = productionCycleConfigService.getValue(CONFIG_KEY_BREED_TO_FARROW);
        return d != null ? d : DEFAULT_BREED_TO_FARROW_DAYS;
    }

    /**
     * 取分娩判定节点天数：读 {@code sow_farrow_judge_deadline_days}，缺或非正回退 119。
     *
     * <p>与 {@link #breedToFarrowDays()} 是两回事：那个是 mp 分娩板块的可选门（配种满 N 天才进
     * 录入列表），这个是统计判定节点（配种满 N 天仍未分娩即定性为未分娩、计入损失）。
     * 甲方口径：正常妊娠 114-116 天，判定节点 119 天。</p>
     *
     * <p>非正值一律回退：{@code ProductionCycleConfig#effectiveValue} 把显式 0 当有效自定义值，
     * 而 admin「母猪生产配置」表单保存时对未填字段会写回 0；判定节点取 0 会让所有批次瞬间到期，
     * 分娩率直接塌成 0。0 天对判定节点无业务含义 = 未定制。</p>
     */
    private int farrowJudgeDeadlineDays() {
        Integer d = productionCycleConfigService.getValue(CONFIG_KEY_FARROW_JUDGE_DEADLINE);
        return d != null && d > 0 ? d : DEFAULT_FARROW_JUDGE_DEADLINE_DAYS;
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
            vo.setIntroduceBoarCount(0);
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
        vo.setIntroduceBoarCount(zeroIfNull(ai.getIntroduceBoarCount()));
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
        /** 均值比率类：累计 = Σ分子 / Σ分母（逐日显示存表均值，累计重算真实加权均值，row143）。 */
        AVG_RATIO,
        /** 均值均分类：累计 = 有值日（{@code >0}）均值的简单平均（无分母可用时，如平均背膘厚，row143）。 */
        AVG_MEAN
    }

    /**
     * row10 活动统计单指标定义（中文文案 + 取值器 + 累计聚合方式）。
     * {@code numGetter}/{@code denGetter} 仅 {@link ActivityAgg#AVG_RATIO} 用（累计 = Σnum/Σden）。
     */
    private record ActivityMetric(String label,
                                  java.util.function.Function<FarmIndicatorRecord, Object> getter,
                                  ActivityAgg agg,
                                  java.util.function.Function<FarmIndicatorRecord, Object> numGetter,
                                  java.util.function.Function<FarmIndicatorRecord, Object> denGetter) {
        /** 非 AVG_RATIO 指标便捷构造（无分子/分母）。 */
        ActivityMetric(String label,
                       java.util.function.Function<FarmIndicatorRecord, Object> getter,
                       ActivityAgg agg) {
            this(label, getter, agg, null, null);
        }
    }

    /**
     * 种猪活动详情指标行集（row158：仅展示以下 15 个活动计数指标 + 累计，其他字段不展示）。
     * 顺序按测试口径固定；均值/期末存栏/出栏生长/饲养总天数/日NPD 等不在此明细，故不列。
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
        new ActivityMetric("淘汰猪只数", FarmIndicatorRecord::getCullingPigCount, ActivityAgg.SUM_INT)
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
        BigDecimal sumNum = BigDecimal.ZERO;  // AVG_RATIO 分子累加
        BigDecimal sumDen = BigDecimal.ZERO;  // AVG_RATIO 分母累加
        BigDecimal sumMean = BigDecimal.ZERO; // AVG_MEAN 有值日累加
        int meanCount = 0;                    // AVG_MEAN 有值日计数（>0 才计）
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
            if (m.agg() == ActivityAgg.AVG_RATIO && r != null) {
                Object nv = m.numGetter().apply(r);
                Object dv = m.denGetter().apply(r);
                if (nv instanceof Number nn) {
                    sumNum = sumNum.add(new BigDecimal(nn.toString()));
                }
                if (dv instanceof Number dn) {
                    sumDen = sumDen.add(new BigDecimal(dn.toString()));
                }
            }
            if (m.agg() == ActivityAgg.AVG_MEAN && raw instanceof Number mn) {
                BigDecimal bv = new BigDecimal(mn.toString());
                if (bv.signum() > 0) { // 背膘等测量类：0/空 = 当日未测量，不计入均值
                    sumMean = sumMean.add(bv);
                    meanCount++;
                }
            }
        }

        String total = switch (m.agg()) {
            case SUM_INT -> anySum ? formatInt(sum) : "";
            case SUM_DEC -> anySum ? formatDec(sum) : "";
            case LAST -> lastVal;
            case AVG_RATIO -> sumDen.signum() == 0 ? "" : formatDec(sumNum.divide(sumDen, 2, RoundingMode.HALF_UP));
            case AVG_MEAN -> meanCount == 0 ? "" : formatDec(sumMean.divide(BigDecimal.valueOf(meanCount), 2, RoundingMode.HALF_UP));
        };

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
        if (agg == ActivityAgg.SUM_DEC || agg == ActivityAgg.AVG_RATIO || agg == ActivityAgg.AVG_MEAN) {
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
        // r124（Kevin 2026-07-02 定）：养殖场日概览「这里都读表、显示昨日的指标」——
        // 改读 t_farm_indicator_record（DashboardAggregateJob 每晚落盘 T-1 全量日指标），不再实时聚合。
        // 默认目标 = 昨日（T-1，= 该表 statDate 语义）；传了 date 则读该日。
        LocalDate target = (date != null) ? date : LocalDate.now().minusDays(1);

        // 读该日整行（tenant 由 MP 拦截器 + del_flag 逻辑删自动加，与 triggerAggregate UPSERT 读法一致）。
        FarmIndicatorRecord rec = farmIndicatorRecordMapper.selectOne(
            new LambdaQueryWrapper<FarmIndicatorRecord>().eq(FarmIndicatorRecord::getStatDate, target));
        // 该日尚无落盘（今日 job 未跑 / 该日无数据）→ 回退最近一条已落盘，避免空板块。
        if (rec == null) {
            rec = farmIndicatorRecordMapper.selectOne(
                new LambdaQueryWrapper<FarmIndicatorRecord>()
                    .orderByDesc(FarmIndicatorRecord::getStatDate)
                    .last("LIMIT 1"));
        }
        // VO.date 回填实际展示的统计日（回退时=最近落盘日；整表空时仍=target，前端可显日期）。
        LocalDate shownDate = (rec != null && rec.getStatDate() != null) ? rec.getStatDate() : target;

        DailyOverviewVo vo = new DailyOverviewVo(shownDate);
        List<DailyOverviewVo.OverviewCell> cells = vo.getCells();
        // 15 格，顺序 / 文案严格对齐原型（4f113e00 养殖场日情况概览，4+4+4+3 末行=生长记录/阉割/用药）。
        // 与 by-month 15 行同序同文案；逐字段读 t_farm_indicator_record 落盘值；整表空 → 全 0（不硬造）。
        cells.add(cell("分娩母猪数", n(rec, FarmIndicatorRecord::getFarrowSowCount)));
        cells.add(cell("配种母猪数", n(rec, FarmIndicatorRecord::getBreedingSowCount)));
        cells.add(cell("断奶母猪数", n(rec, FarmIndicatorRecord::getWeaningSowCount)));
        cells.add(cell("返空流母猪数", n(rec, FarmIndicatorRecord::getAbnormalSowCount)));
        cells.add(cell("引种母猪数", n(rec, FarmIndicatorRecord::getIntroduceSowCount)));
        cells.add(cell("查情不配种数", n(rec, FarmIndicatorRecord::getHeatNoBreedCount)));
        cells.add(cell("死亡猪只数", n(rec, FarmIndicatorRecord::getDeathPigCount)));
        cells.add(cell("淘汰猪只数", n(rec, FarmIndicatorRecord::getCullingPigCount)));
        cells.add(cell("产仔数", n(rec, FarmIndicatorRecord::getTotalBornCount)));
        cells.add(cell("活仔数", n(rec, FarmIndicatorRecord::getLiveBornCount)));
        cells.add(cell("仔猪打标数", n(rec, FarmIndicatorRecord::getPigletTagCount)));
        cells.add(cell("断奶仔猪数", n(rec, FarmIndicatorRecord::getWeanedPigletCount)));
        cells.add(cell("生长记录数", n(rec, FarmIndicatorRecord::getGrowthRecordCount)));
        cells.add(cell("阉割猪只数", n(rec, FarmIndicatorRecord::getCastratePigCount)));
        cells.add(cell("用药猪只数", n(rec, FarmIndicatorRecord::getMedicatedPigCount)));
        return vo;
    }

    private DailyOverviewVo.OverviewCell cell(String metric, int value) {
        return new DailyOverviewVo.OverviewCell(metric, value);
    }

    /** 从落盘记录取某指标列，rec 为空或列为 null → 0（空表/未落盘不硬造，显 0）。 */
    private int n(FarmIndicatorRecord rec, java.util.function.Function<FarmIndicatorRecord, Integer> getter) {
        if (rec == null) {
            return 0;
        }
        Integer v = getter.apply(rec);
        return v == null ? 0 : v;
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
        AnnualIndicator ai = selectYear(tenantId, year.shortValue());

        BreedingAnnualVo vo = new BreedingAnnualVo();
        vo.setYear(year);
        if (ai == null) {
            vo.setPsy(BigDecimal.ZERO);
            vo.setMateRate(BigDecimal.ZERO);
            vo.setFarrowRate(BigDecimal.ZERO);
            vo.setWeanMateInterval(BigDecimal.ZERO);
            vo.setAvgNonProductiveDays(BigDecimal.ZERO);
            vo.setTotalBornCount(BigDecimal.ZERO);
            vo.setTotalLiveBorn(BigDecimal.ZERO);
            vo.setAvgLiveBornPerLitter(BigDecimal.ZERO);
            vo.setAvgWeanedPerLitter(BigDecimal.ZERO);
            vo.setFarrowingLossRate(BigDecimal.ZERO);
            vo.setPsyStatDays(0);
            return vo;
        }
        // ②年度繁殖与配种：以年表 t_farm_year_production 落盘值为权威源（甲方口径，取代旧 live 实时算）。
        //   年表率类字段已 ×100（如 55.56），mp 直接拼 "%"。配种率 V1 口径同分娩率（年表仅存 year_farrow_rate）。
        BigDecimal farrowRate = bdZero(ai.getYearFarrowRate());
        // R72：年度繁殖卡首格由「配种率」改「PSY」，取年表 psy（头/母猪·年）。mateRate 仍保留兼容不再前端展示。
        vo.setPsy(bdZero(ai.getPsy()));
        vo.setPsyStatFrom(ai.getPsyStatFrom() == null ? null : ai.getPsyStatFrom().toString());
        vo.setPsyStatDays(zeroIfNull(ai.getPsyStatDays()));
        vo.setMateRate(farrowRate);
        vo.setFarrowRate(farrowRate);
        vo.setWeanMateInterval(bdZero(ai.getWeanBreedInterval()));
        vo.setAvgNonProductiveDays(bdZero(ai.getAvgNpdDays()));
        // ③年度产房与仔猪质量
        vo.setTotalBornCount(new BigDecimal(zeroIfNull(ai.getTotalBornCount())));
        vo.setTotalLiveBorn(new BigDecimal(zeroIfNull(ai.getTotalLiveBorn())));
        vo.setAvgLiveBornPerLitter(bdZero(ai.getAvgLiveBornPerLitter()));
        vo.setAvgWeanedPerLitter(bdZero(ai.getAvgWeanedPerLitter()));
        vo.setFarrowingLossRate(bdZero(ai.getFarrowLossRate()));
        return vo;
    }

    /** 年表 BigDecimal 字段取值：null → ZERO。 */
    private static BigDecimal bdZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
    public List<AgeBucketVo> getFatteningStageDistribution() {
        String tenantId = currentTenant();
        // 读后台「育肥阶段」配置（queryList 已按 startAge 升序）；每条 [startAge, endAge] 含两端归桶。
        List<FattenAgeStageVo> stages = fattenAgeStageService.queryList();
        List<AgeBucketVo> list = new ArrayList<>(stages.size());
        if (stages.isEmpty()) {
            return list;
        }
        int[] counts = new int[stages.size()];
        for (Map<String, Object> r : aggregateQueryMapper.selectFatteningAges(tenantId)) {
            Object a = r.get("age");
            if (a == null) {
                continue;
            }
            int age = ((Number) a).intValue();
            // 归入第一个匹配阶段（含两端）；阶段区间理论不重叠，命中即停。
            for (int i = 0; i < stages.size(); i++) {
                FattenAgeStageVo s = stages.get(i);
                Integer start = s.getStartAge();
                Integer end = s.getEndAge();
                boolean geLower = start == null || age >= start;
                boolean leUpper = end == null || age <= end;
                if (geLower && leUpper) {
                    counts[i]++;
                    break;
                }
            }
        }
        for (int i = 0; i < stages.size(); i++) {
            FattenAgeStageVo s = stages.get(i);
            String remark = s.getRemark();
            String label = (remark != null && !remark.isBlank())
                ? remark
                : (s.getStartAge() + "-" + s.getEndAge() + "天");
            list.add(new AgeBucketVo(label, counts[i]));
        }
        return list;
    }

    @Override
    public FatteningTrendVo getFatteningTrend(String period, String metric, String month) {
        // R67/68/69：三 tab 的 metric 键对齐 mp 契约（marketing 出栏头数 / marketingTarget 出栏均重 /
        // liveStock 肥猪进栏）。旧键 market/target/onhand 与 mp 实际发的键不匹配 → 三 tab 全落 default 出栏头数
        // （折线完全一致，甲方投诉根因）。统一小写后按新键分发，未知键回落出栏头数。
        String m = metric == null ? METRIC_MARKETING : metric.toLowerCase();
        if (!METRIC_MARKETING.equals(m) && !METRIC_MARKETING_WEIGHT.equals(m) && !METRIC_WEANED.equals(m)) {
            m = METRIC_MARKETING;
        }
        String tenantId = currentTenant();

        // month 非空 → 忽略 period，返回该月逐日（1~该月天数）数据。
        YearMonth targetMonth = parseMonth(month);
        if (targetMonth != null) {
            return buildFatteningTrendByDay(tenantId, m, targetMonth);
        }

        String p = "month".equalsIgnoreCase(period) ? "month" : "week";
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
     * 解析 month 入参（"yyyyMM" 或 "yyyy-MM"），可空。
     *
     * @return 解析出的 YearMonth；入参空/null/非法 → null（调用方据此退回 week/month 原逻辑）
     */
    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        String s = month.trim();
        try {
            if (s.contains("-")) {
                return YearMonth.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            }
            return YearMonth.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 单月逐日育肥趋势：labels = 该月每一天的「日」数字，values 按 metric 逐日取日表列值。
     * R67/68/69：三 tab 分别取 t_farm_indicator_record 的 marketing_pig_count（出栏头数）/
     * avg_marketing_weight（出栏均重）/ weaned_piglet_count（肥猪进栏），按 stat_date 日号索引，无当日行补 0。
     * R71：当月只画到今天（不显示未来日期的 0 平线）；历史月画满当月最大日。
     */
    private FatteningTrendVo buildFatteningTrendByDay(String tenantId, String metric, YearMonth ym) {
        FatteningTrendVo vo = new FatteningTrendVo();
        vo.setPeriod("month");
        vo.setMetric(metric);

        int days = ym.lengthOfMonth();
        // R71：当月截到今天（今天 7/20 → x 轴止于 20，不把未来空数据画成 0 平线）；历史月画满月。
        if (ym.equals(YearMonth.now())) {
            days = Math.min(days, LocalDate.now().getDayOfMonth());
        }
        // R67/68/69：逐日读日表整行明细（[first, toExclusive) 右开区间），按 stat_date 日号索引。
        Map<String, FarmIndicatorRecord> byDay = new LinkedHashMap<>();
        for (FarmIndicatorRecord r : aggregateQueryMapper.selectIndicatorRecordsInRange(
            tenantId, ym.atDay(1), ym.plusMonths(1).atDay(1))) {
            if (r.getStatDate() != null) {
                byDay.put(String.valueOf(r.getStatDate().getDayOfMonth()), r);
            }
        }

        List<String> labels = new ArrayList<>(days);
        List<BigDecimal> values = new ArrayList<>(days);
        for (int day = 1; day <= days; day++) {
            String label = String.valueOf(day);
            labels.add(label);
            values.add(dailyTrendValue(metric, byDay.get(label)));
        }
        vo.setLabels(labels);
        vo.setValues(values);
        vo.setLiveStock(buildFatteningLiveStock(tenantId));
        return vo;
    }

    /**
     * R67/68/69：逐日趋势按 metric 取日表列值（无当日记录补 0）。
     * marketing 出栏头数 / marketingTarget 出栏均重 / liveStock 肥猪进栏。
     */
    private static BigDecimal dailyTrendValue(String metric, FarmIndicatorRecord r) {
        if (r == null) {
            return BigDecimal.ZERO;
        }
        return switch (metric) {
            case METRIC_MARKETING_WEIGHT ->
                r.getAvgMarketingWeight() == null ? BigDecimal.ZERO : r.getAvgMarketingWeight();
            case METRIC_WEANED ->
                r.getWeanedPigletCount() == null ? BigDecimal.ZERO : new BigDecimal(r.getWeanedPigletCount());
            default ->
                r.getMarketingPigCount() == null ? BigDecimal.ZERO : new BigDecimal(r.getMarketingPigCount());
        };
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

        // 各指标以月表 t_farm_monthly_production 预算字段为权威源（甲方口径）：live 重算对「率」类有跨月
        // 窗口伪影（分娩率/配怀率 >100%）、NPD 用年均公式算成数百天，均以月表 job 落盘值为准。
        MonthlyProduction curr = selectMonth(tenantId, yearMonth);
        MonthlyProduction prev0 = selectMonth(tenantId, prev);

        MonthlyProductionStatVo vo = new MonthlyProductionStatVo();
        vo.setCurrentMonth(yearMonth.toString());
        vo.setPreviousMonth(prev.toString());
        List<MonthlyProductionStatVo.StatRow> rows = vo.getRows();
        rows.add(new MonthlyProductionStatVo.StatRow("分娩率", mv(curr, MonthlyProduction::getFarrowRate), mv(prev0, MonthlyProduction::getFarrowRate), "%"));
        // R73：删除「配怀率」行（窗口伪影常 >100%，甲方要求整行去掉）。
        rows.add(new MonthlyProductionStatVo.StatRow("断配间隔", mv(curr, MonthlyProduction::getWeanBreedInterval), mv(prev0, MonthlyProduction::getWeanBreedInterval), "天"));
        rows.add(new MonthlyProductionStatVo.StatRow("返空流头数", mvInt(curr, MonthlyProduction::getAbnormalCount), mvInt(prev0, MonthlyProduction::getAbnormalCount), "头"));
        rows.add(new MonthlyProductionStatVo.StatRow("平均非生产天数", mv(curr, MonthlyProduction::getNpdDays), mv(prev0, MonthlyProduction::getNpdDays), "天"));
        rows.add(new MonthlyProductionStatVo.StatRow("总产仔数", mvInt(curr, MonthlyProduction::getTotalBornCount), mvInt(prev0, MonthlyProduction::getTotalBornCount), "头"));
        rows.add(new MonthlyProductionStatVo.StatRow("窝均总产仔", mv(curr, MonthlyProduction::getAvgBornPerLitter), mv(prev0, MonthlyProduction::getAvgBornPerLitter), "头/窝"));
        rows.add(new MonthlyProductionStatVo.StatRow("窝均活仔", mv(curr, MonthlyProduction::getAvgLiveBornPerLitter), mv(prev0, MonthlyProduction::getAvgLiveBornPerLitter), "头/窝"));
        rows.add(new MonthlyProductionStatVo.StatRow("窝均断奶", mv(curr, MonthlyProduction::getAvgWeanedPerLitter), mv(prev0, MonthlyProduction::getAvgWeanedPerLitter), "头/窝"));
        rows.add(new MonthlyProductionStatVo.StatRow("产房损失率", mv(curr, MonthlyProduction::getFarrowLossRate), mv(prev0, MonthlyProduction::getFarrowLossRate), "%"));
        return vo;
    }

    /** 月表 BigDecimal 字段取值：m 或值为 null → ZERO。 */
    private static BigDecimal mv(MonthlyProduction m, java.util.function.Function<MonthlyProduction, BigDecimal> g) {
        BigDecimal v = m == null ? null : g.apply(m);
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 月表整型字段 → BigDecimal：m 或值为 null → ZERO。 */
    private static BigDecimal mvInt(MonthlyProduction m, java.util.function.Function<MonthlyProduction, Integer> g) {
        Integer v = m == null ? null : g.apply(m);
        return v == null ? BigDecimal.ZERO : new BigDecimal(v);
    }

    // ============================================================
    //  rate / 窝均 helpers
    // ============================================================

    /** 育肥出栏目标占位常量（V1，target 趋势用；可后续配置化）。 */
    private static final int FATTENING_TARGET_PER_PERIOD = 100;

    /** R67/68/69 育肥趋势三 tab metric 键（小写，对齐 mp 契约）：出栏头数 / 出栏均重 / 肥猪进栏。 */
    private static final String METRIC_MARKETING = "marketing";
    private static final String METRIC_MARKETING_WEIGHT = "marketingtarget";
    private static final String METRIC_WEANED = "livestock";

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

    /**
     * Map 取日期值（缺省 null）。
     *
     * <p>DATE 列在 Map 结果里按驱动/类型处理器不同可能是 {@link LocalDate}、{@link java.sql.Date}
     * 或 {@link java.sql.Timestamp}，三种都收。</p>
     */
    private static LocalDate mapDate(Map<String, Object> m, String key) {
        if (m == null) {
            return null;
        }
        Object v = m.get(key);
        if (v instanceof LocalDate d) {
            return d;
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (v instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime().toLocalDate();
        }
        return null;
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

    /**
     * 产房损失率% = Σ该批窝哺乳期死淘数 / Σ该批窝活仔数 × 100（只统计已断奶的窝）。
     *
     * <p>死淘数超过活仔数（数据异常）时按 100% 封顶，活仔数为 0 时按 0，都不产出越界值。</p>
     */
    private static BigDecimal farrowHouseLossRate(Map<String, Object> loss) {
        int liveBorn = mapInt(loss, "liveBorn");
        int death = mapInt(loss, "lactationDeath");
        if (liveBorn <= 0 || death <= 0) {
            return BigDecimal.ZERO;
        }
        return pct(ratio(Math.min(death, liveBorn), liveBorn));
    }

    /**
     * 年化：把「统计区间内的量」折成「每年」。{@code statDays} 非正时原样返回（不放大噪声）。
     *
     * <p>PSY 定义是「每头母猪每年提供的断奶仔猪数」、平均非生产天数是「天/年」，
     * 而区间内算出来的是「每 statDays 天」的值，挂「年度」标题展示必须乘 365/statDays。</p>
     */
    private static BigDecimal annualize(BigDecimal perWindowValue, int statDays) {
        if (perWindowValue == null || perWindowValue.signum() == 0 || statDays <= 0) {
            return perWindowValue == null ? BigDecimal.ZERO : perWindowValue;
        }
        return scale3(perWindowValue.multiply(DAYS_PER_YEAR)
            .divide(new BigDecimal(statDays), 6, RoundingMode.HALF_UP));
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

    @Override
    public List<CohortLedgerVo> getCohortLedger(Integer year) {
        String tenantId = currentTenant();
        int y = year == null ? LocalDate.now().getYear() : year;
        LocalDate from = LocalDate.of(y, 1, 1);
        LocalDate to = LocalDate.of(y + 1, 1, 1);
        // 判定基准日 = T-1（与月/年指标同口径，不把今天算进来）
        LocalDate asOf = LocalDate.now().minusDays(1);
        List<CohortLedgerVo> rows = new ArrayList<>();
        for (Map<String, Object> r : aggregateQueryMapper.selectCohortLedgerByBreedMonth(
                tenantId, from, to, farrowJudgeDeadlineDays(), asOf)) {
            CohortLedgerVo vo = new CohortLedgerVo();
            vo.setBreedMonth(Objects.toString(r.get("breedMonth"), ""));
            vo.setBred(mapInt(r, "bred"));
            vo.setMatured(mapInt(r, "matured"));
            vo.setFarrow(mapInt(r, "farrow"));
            vo.setFarrowLate(mapInt(r, "farrowLate"));
            vo.setReturnCount(mapInt(r, "returnCount"));
            vo.setEmptyCount(mapInt(r, "emptyCount"));
            vo.setAbortCount(mapInt(r, "abortCount"));
            vo.setGoneCount(mapInt(r, "goneCount"));
            vo.setUndecided(mapInt(r, "undecided"));
            vo.setPending(mapInt(r, "pending"));
            vo.setFirstDeadline(mapDate(r, "firstDeadline"));
            vo.setLastDeadline(mapDate(r, "lastDeadline"));
            vo.setFarrowRate(pct(ratio(vo.getFarrow(), vo.getMatured())));
            rows.add(vo);
        }
        return rows;
    }

    @Override
    public List<OverdueUndecidedVo> listOverdueUndecided() {
        String tenantId = currentTenant();
        LocalDate asOf = LocalDate.now().minusDays(1);
        List<OverdueUndecidedVo> rows = new ArrayList<>();
        for (Map<String, Object> r : aggregateQueryMapper.selectOverdueUndecided(
                tenantId, farrowJudgeDeadlineDays(), asOf)) {
            OverdueUndecidedVo vo = new OverdueUndecidedVo();
            Object bid = r.get("breedingId");
            vo.setBreedingId(bid instanceof Number n ? n.longValue() : null);
            vo.setEarNo(Objects.toString(r.get("earNo"), null));
            vo.setBreedingDate(mapDate(r, "breedingDate"));
            vo.setDeadline(mapDate(r, "deadline"));
            vo.setOverdueDays(mapInt(r, "overdueDays"));
            vo.setParity(mapInt(r, "parity"));
            vo.setBarnName(Objects.toString(r.get("barnName"), null));
            vo.setPenName(Objects.toString(r.get("penName"), null));
            vo.setCurrentStatus(Objects.toString(r.get("currentStatus"), null));
            rows.add(vo);
        }
        return rows;
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

        aggregateDay(tenantId, targetDate);
        aggregateRollups(tenantId, targetDate, List.of(YearMonth.from(targetDate)), List.of((short) targetDate.getYear()));

        log.info("[DashboardAggregate] done tenant={} date={}", tenantId, targetDate);
        return String.format("ok | tenant=%s | date=%s | tables=[indicator_record, sow_record, sow_performance, monthly_production, annual_indicator]",
            tenantId, targetDate);
    }

    /**
     * 单业务日重算：快照重放 → 日表 → 母猪日记录。
     *
     * <p>滚动窗口逐日调用本方法（经 AOP 代理 → 每天一个独立事务），某天炸了不回滚已算好的其它天。
     * public 是为了让代理能调到；不进 {@link IDashboardService} 接口，外部入口只有
     * {@link #triggerAggregate} / {@link #triggerAggregateRange} 两个。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void aggregateDay(String tenantId, LocalDate date) {
        // BRD-STAT-003：先按业务时间重放当日猪群快照（期末存栏 + 母猪日分布的唯一数据源），再落日表——
        // upsertFarmIndicator / upsertSowRecord 都绑当日快照，故快照必须先写。
        rebuildPigSnapshot(tenantId, date);
        upsertFarmIndicator(tenantId, date);
        upsertSowRecord(tenantId, date);
    }

    /**
     * 跨日汇总：母猪性能 + 月表 + 年表。
     *
     * <p>这三样与「具体是哪一天」无关（母猪性能是当前累计、月/年是 Σ 日表），所以整段窗口只跑一次，
     * 不跟着 45 天循环重复 45 遍。月/年从已落盘的日表 Σ 回读，故必须在 {@link #aggregateDay} 之后。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void aggregateRollups(String tenantId, LocalDate asOf,
                                 Collection<YearMonth> months, Collection<Short> years) {
        upsertSowPerformance(tenantId, asOf);
        for (YearMonth m : months) {
            upsertMonthlyProduction(tenantId, m);
        }
        for (Short y : years) {
            upsertAnnualIndicator(tenantId, y);
        }
    }

    /** 滚动重算跨度上限（天）：防手滑传个 2020-01-01 把库跑穿。历史一次性回补分段跑。 */
    private static final int MAX_REBUILD_SPAN_DAYS = 400;

    /** 窗口外补录的回看范围（天）：只报「最近这些天才录进来的」，老账不反复刷屏。 */
    private static final int LATE_ENTRY_LOOKBACK_DAYS = 7;

    @Override
    public String triggerAggregateRange(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now().minusDays(1) : to;
        if (from == null || from.isAfter(end)) {
            throw new IllegalArgumentException("重算区间非法：from=" + from + " to=" + end);
        }
        long span = ChronoUnit.DAYS.between(from, end) + 1;
        if (span > MAX_REBUILD_SPAN_DAYS) {
            throw new IllegalArgumentException("重算区间过长（" + span + " 天 > " + MAX_REBUILD_SPAN_DAYS + "），请分段跑");
        }
        String tenantId = currentTenant();
        // 走代理调自己：逐日各自开事务，某天炸了不回滚已经算好的其它天。
        DashboardServiceImpl self = SpringUtils.getAopProxy(this);
        int ok = 0;
        List<String> failed = new ArrayList<>();
        Set<YearMonth> months = new LinkedHashSet<>();
        Set<Short> years = new LinkedHashSet<>();
        for (LocalDate d = from; !d.isAfter(end); d = d.plusDays(1)) {
            months.add(YearMonth.from(d));
            years.add((short) d.getYear());
            try {
                self.aggregateDay(tenantId, d);
                ok++;
            } catch (Exception e) {
                failed.add(d.toString());
                log.error("[DashboardAggregate] 单日重算失败 tenant={} date={}", tenantId, d, e);
            }
        }
        // 月/年/母猪性能整段只跑一次（与具体哪天无关），别跟着 45 天循环白跑 45 遍
        self.aggregateRollups(tenantId, end, months, years);
        String lateHint = warnLateEntriesBefore(tenantId, from);
        log.info("[DashboardAggregate] range done tenant={} {}~{} ok={} failed={}", tenantId, from, end, ok, failed);
        return String.format("ok | tenant=%s | range=%s~%s | ok=%d | failed=%s%s",
            tenantId, from, end, ok, failed.isEmpty() ? "[]" : failed, lateHint);
    }

    /**
     * 窗口外补录告警：业务日早于重算窗口、却是最近几天才录进来的记录。
     *
     * <p>这些记录对应的日/月行改不动（滚动窗口够不着），必须人工按日期补跑
     * {@code POST /djs/breed/dashboard/trigger-aggregate?date=业务日}。不报出来就会变成
     * 8 月那种静默漏计（分娩少 8 窝 81 头 / 返空流少 5 条）。</p>
     */
    private String warnLateEntriesBefore(String tenantId, LocalDate windowStart) {
        LocalDateTime since = LocalDate.now().minusDays(LATE_ENTRY_LOOKBACK_DAYS).atStartOfDay();
        List<Map<String, Object>> rows = aggregateQueryMapper.findLateEntriesBeforeWindow(tenantId, windowStart, since);
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        String dates = rows.stream()
            .map(r -> Objects.toString(r.get("bizDate")) + "(" + mapInt(r, "cnt") + ")")
            .collect(Collectors.joining(", "));
        log.warn("[DashboardAggregate] ⚠️ 发现窗口外补录：业务日早于 {} 但最近 {} 天才录入 → 这些日期需人工补跑 trigger-aggregate?date=… 明细={}",
            windowStart, LATE_ENTRY_LOOKBACK_DAYS, dates);
        return " | ⚠️窗口外补录(需人工补跑): " + dates;
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
        r.setIntroduceBoarCount(aggregateQueryMapper.sumIntroducedBoarInRange(tenantId, dayFrom, dayTo));
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

        // ---- 当日出栏猪断奶总重 + 饲养/生长总天数 → 净增重 / 日增重（row112 NPD 口径重构） ----
        // 净增重 = Σ(出栏重 − 断奶重) 仅对「有断奶快照的出栏育肥猪」同一集合，
        // 故被减数用同集合的出栏重 marketingWeightWeaned（非全部出栏 marketingWeight，
        // 后者含淘汰母猪/种猪出栏会让净增重虚高）。出栏总重列仍取全量 marketingWeight。
        //   饲养总天数 feedTotalDays = Σ(出栏日−断奶日+1)（从断奶起算）；
        //   生长总天数 growthTotalDays（row186 口径）= Σ(出栏日−出生日+1)（从出生起算，整个生命周期，独立展示指标）。
        //   日增重 = 净增重 / 饲养总天数（row189 最终确认）：净增重按「断奶后」增重、饲养天数按「断奶」起算，分子分母同起点；分母 0 → 日增重 0。
        Map<String, Object> weanAgg = aggregateQueryMapper.aggregateMarketingWeanForDay(tenantId, dtFrom, dtTo);
        BigDecimal weanTotalWeight = mapBd(weanAgg, "weanWeightSum");
        int feedDays = mapInt(weanAgg, "feedDaysSum");
        int growthDays = mapInt(weanAgg, "growthDaysSum");
        BigDecimal marketingWeightWeaned = mapBd(weanAgg, "marketingWeightWeaned");
        BigDecimal netGain = marketingWeightWeaned.subtract(weanTotalWeight);
        r.setWeanTotalWeight(scale3(weanTotalWeight));
        r.setFeedTotalDays(feedDays);
        r.setGrowthTotalDays(growthDays);
        r.setNetGainWeight(scale3(netGain));
        r.setDailyGainWeight(scale3(divide(netGain, feedDays)));

        // ---- 死亡分类（按 pig_type） ----
        r.setDeathFatteningCount(aggregateQueryMapper.countDeathByPigTypeInDay(tenantId, "fattening", dtFrom, dtTo));
        r.setDeathSowCount(aggregateQueryMapper.countDeathByPigTypeInDay(tenantId, "sow", dtFrom, dtTo));
        r.setDeathPigletCount(aggregateQueryMapper.countDeathByPigTypeInDay(tenantId, "piglet", dtFrom, dtTo));

        // ---- 期末存栏快照（T-1 当日 current_status 当前快照，A#4 不回算） ----
        fillEndStock(r, tenantId, statDate);

        // ---- 日NPD天数（row112）= 当日非生产状态母猪头数（= end_nonprod_sow_count 同值） ----
        // 邓博 row114/115 分子改「纯非生产母猪头数」，去掉 230 后备；月/年从本列 Σ 回读。
        r.setNpdDays(zeroIfNull(r.getEndNonprodSowCount()));

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
        // BRD-STAT-003：期末存栏只认当日快照，而快照是 upsertFarmIndicator 之前刚按业务时间重放出来的。
        // 这里**不再回落实时主表** —— 回落会把「今天的猪群」写进历史行，滚动重算时直接污染历史
        // （旧实现的 bug：任何没有快照的历史日期一重算，期末存栏就变成当天的值）。
        List<Map<String, Object>> statusRows = aggregateQueryMapper.snapshotByTypeStatusOnDate(tenantId, asOf);
        for (Map<String, Object> row : statusRows) {
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
        // 230 日龄后备同口径：读当日快照（birth_date 随快照重放，日龄基准 = asOf）。
        r.setEndReserve230Count(aggregateQueryMapper.countReserve230OnSnapshot(tenantId, asOf));
    }

    /**
     * 按业务时间重放某业务日收盘的在群猪群快照（BRD-STAT-003）。
     *
     * <p>先删后建，每次重放结果只由「业务时间」决定，与什么时候跑、跑几次无关（幂等）。
     * 这替代了原先「采集那一刻 SELECT 实时主表 + 已有快照就跳过」的做法 —— 那个做法下，
     * 单据补录晚于采集时点，那一天的存栏就永久错（9/8 出栏 4 头、存栏没减就是这么来的）。</p>
     *
     * <p>{@link TenantHelper#ignore} 包裹读写：SQL 已显式写 tenant_id，避免多租户拦截器
     * 在 INSERT 列再注入 tenant_id 造成重复列。</p>
     */
    private void rebuildPigSnapshot(String tenantId, LocalDate snapDate) {
        TenantHelper.ignore(() -> {
            int removed = aggregateQueryMapper.deletePigSnapshotOnDate(tenantId, snapDate);
            int n = aggregateQueryMapper.rebuildPigSnapshotForDate(tenantId, snapDate);
            log.info("[DashboardAggregate] pig snapshot rebuilt tenant={} date={} removed={} rows={}",
                tenantId, snapDate, removed, n);
        });
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
            try {
                upsertSingleSowPerformance(tenantId, statDate, sow, pigId);
            } catch (Exception e) {
                // 单猪失败只跳过该猪（记日志），不让异常中断整轮母猪性能跑批
                log.warn("[DashboardAggregate] upsertSowPerformance 单猪失败 pigId={}", pigId, e);
            }
        }
    }

    private void upsertSingleSowPerformance(String tenantId, LocalDate statDate, Map<String, Object> sow, Long pigId) {
        String earNo = Objects.toString(sow.get("earNo"), "");
        Integer parity = sow.get("parity") == null ? 0 : ((Number) sow.get("parity")).intValue();

        Map<String, Object> fa = aggregateQueryMapper.sowFarrowAgg(tenantId, pigId);
        int totalBorn = mapInt(fa, "totalBorn");
        int totalLiveBorn = mapInt(fa, "totalLiveBorn");
        int litterCount = mapInt(fa, "litterCount");
        BigDecimal sumAvgBornWeight = mapBd(fa, "sumAvgBornWeight");
        // 平均怀孕天数（row94）：状态记录表 配种(PZ)→分娩(FM) 的 Σduration_days/条数
        Map<String, Object> gestAgg = aggregateQueryMapper.sowGestationByStatus(tenantId, pigId);
        int gestSum = mapInt(gestAgg, "sumDays");
        int gestCount = mapInt(gestAgg, "cnt");

        Map<String, Object> we = aggregateQueryMapper.sowWeanAgg(tenantId, pigId);
        int totalWeaned = mapInt(we, "totalWeaned");
        int weanCount = mapInt(we, "weanCount");
        BigDecimal sumAvgWeanedWeight = mapBd(we, "sumAvgWeanedWeight");

        int abnormalTotal = aggregateQueryMapper.sowAbnormalCount(tenantId, pigId);

        // 断奶-配种天数（row97/183）：状态记录表 断奶(DN)→配种(PZ) 的 Σduration_days/条数
        Map<String, Object> wb = aggregateQueryMapper.sowWeanBreedByStatus(tenantId, pigId);
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
        // NPD（row113 母猪性能，邓博 2026-07-05 = admin row202）= 状态变更记录表中 old_status ∈
        // {LC/KH/FQ/DN} 且（new_status=PZ 或 event ∈ {DIE/ELIMINATE}）的 Σduration_days（每段非生产
        // 状态持续到再配种/死淘的天数总和），保留 2 位小数。
        sp.setNpd(scale2(aggregateQueryMapper.sumSowNpdDurationDays(tenantId, pigId)));

        // 定位现有行：唯一键是 (tenant_id, pig_id, parity, del_unique)，同 pig 允许多行（不同 parity），
        // 取 id 最小行 UPDATE（每猪一行的展示口径），避免 selectOne 命中多行抛异常。
        List<SowPerformance> existingRows = sowPerformanceMapper.selectList(
            new LambdaQueryWrapper<SowPerformance>()
                .eq(SowPerformance::getPigId, pigId)
                .orderByAsc(SowPerformance::getId));
        if (existingRows.isEmpty()) {
            sp.setDelFlag("0");
            sp.setDelUnique(0L);
            sowPerformanceMapper.insert(sp);
        } else {
            sp.setId(existingRows.get(0).getId());
            sowPerformanceMapper.updateById(sp);
        }
    }

    /**
     * 重算指定 stat_date 一行（UPSERT 语义：存在则 UPDATE，不存在则 INSERT）。
     */
    private void upsertSowRecord(String tenantId, LocalDate statDate) {
        LocalDateTime dayStart = statDate.atStartOfDay();
        LocalDateTime dayEnd = statDate.plusDays(1).atStartOfDay();

        // statDate 收盘时点的 sow / piglet 分布 —— 读当日快照（与 fillEndStock 同源）。
        // 原先读实时主表：近 7 天趋势里每一天都是「今天的分布」，且滚动重算会把今天的值刷满整段。
        int sowTotal = 0, sowPregnant = 0, sowFarrow = 0, sowWeaning = 0, sowIdle = 0, pigletTotal = 0;
        for (Map<String, Object> row : aggregateQueryMapper.snapshotByTypeStatusOnDate(tenantId, statDate)) {
            String type = Objects.toString(row.get("pigType"), "");
            String lc = Objects.toString(row.get("cs"), "");
            int cnt = mapInt(row, "cnt");
            if ("piglet".equals(type)) {
                pigletTotal += cnt;
            } else if ("sow".equals(type)) {
                sowTotal += cnt;
                switch (lc) {
                    case "PZ" -> sowPregnant += cnt;
                    case "FM" -> sowFarrow += cnt;
                    case "DN" -> sowWeaning += cnt;
                    default -> sowIdle += cnt; // KH/LC/FQ/HB
                }
            }
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
        // row43②：月度只统计「T-1」（不含今天及以后）——右开界 = min(下月 1 日, 今天)。
        //   历史完整月恒取全月，当月取到昨天为止；统计窗口只由 month 决定，不随触发日截断
        //   （对历史日重跑 = 重算该月完整口径，UPSERT 幂等）。
        LocalDate to = month.plusMonths(1).atDay(1);
        LocalDate today = LocalDate.now();
        if (today.isBefore(to)) {
            to = today;
        }
        LocalDateTime dtFrom = from.atStartOfDay();
        LocalDateTime dtTo = to.atStartOfDay();

        // row43①：基础指标「日表有单日数据就取日表 Σ」，避免与日表口径分叉、且天然按 T-1 收口
        //   （日表无未来行）；日表整段无行（历史未落盘）才回落业务表兜底。
        boolean useDaily = aggregateQueryMapper.countIndicatorDays(tenantId, from, to) > 0;

        // ---- row13 高级指标（从已落盘 farm_indicator 日表 Σ 回读 + 部分实时算） ----
        Map<String, Object> sum = aggregateQueryMapper.sumIndicatorRange(tenantId, from, to);

        // row14：引种母猪数 = Σ日表 introduce_sow_count；引种公猪数 = Σ日表 introduce_boar_count（当日外部引种公猪）。
        //   日表无行则回落业务表按性别聚合（母=internal+external-F，公=external-M），与日表口径一致。
        int introduceCount = useDaily
            ? mapInt(sum, "sumIntroduceSow")
            : aggregateQueryMapper.sumIntroducedSowInRange(tenantId, from, to);
        int introduceBoarCount = useDaily
            ? mapInt(sum, "sumIntroduceBoar")
            : aggregateQueryMapper.sumIntroducedBoarInRange(tenantId, from, to);

        int bornCount;
        int weanedCount;
        int deathCount;
        int cullingCount;
        int marketingCount;
        BigDecimal marketingWeight;
        if (useDaily) {
            // 活仔/断奶/死亡/淘汰/出栏 = Σ日表列（born_count 口径 = 活仔 live_born_count，沿用既有语义）
            bornCount = mapInt(sum, "sumLiveBorn");
            weanedCount = mapInt(sum, "sumWeanedPiglet");
            deathCount = mapInt(sum, "sumDeathPig");
            cullingCount = mapInt(sum, "sumCullingPig");
            marketingCount = mapInt(sum, "sumMarketingCount");
            marketingWeight = mapBd(sum, "sumMarketingWeight");
        } else {
            // 兜底：日表整段无行 → 回落业务表实时聚合（保持历史未落盘月不丢数）
            bornCount = aggregateQueryMapper.sumLiveBornInRange(tenantId, from, to);
            weanedCount = aggregateQueryMapper.sumWeanedInRange(tenantId, from, to);
            deathCount = aggregateQueryMapper.countStatusEventInRange(tenantId, "DIE", dtFrom, dtTo);
            cullingCount = aggregateQueryMapper.countStatusEventInRange(tenantId, "ELIMINATE", dtFrom, dtTo);
            Map<String, Object> marketing = aggregateQueryMapper.aggregateMarketingInRange(tenantId, from, to);
            marketingCount = marketing == null ? 0 : ((Number) marketing.getOrDefault("cnt", 0)).intValue();
            marketingWeight = marketing == null
                ? BigDecimal.ZERO
                : Optional.ofNullable((BigDecimal) marketing.get("weight")).orElse(BigDecimal.ZERO);
        }
        int sumFarrowSow = mapInt(sum, "sumFarrowSow");
        int sumWeaningSow = mapInt(sum, "sumWeaningSow");
        int sumTotalBorn = mapInt(sum, "sumTotalBorn");
        int sumLiveBorn = mapInt(sum, "sumLiveBorn");
        int sumWeanedPiglet = mapInt(sum, "sumWeanedPiglet");
        int sumAbnormal = mapInt(sum, "sumAbnormal");
        int sumEndProdSow = mapInt(sum, "sumEndProductionSow");
        int sumEndReserve230 = mapInt(sum, "sumEndReserve230");
        int sumEndNonprodSow = mapInt(sum, "sumEndNonprodSow");

        int daysInMonth = month.lengthOfMonth();
        // 分娩率（配种批次口径 BRD-STAT-COHORT-001）：收「判定日 = 配种日 + judgeDays 落在本月」的批次，
        //   分母 = 这些批次数、分子 = 其中在 judgeDays 内分娩的头数 —— 分子分母是同一批猪。
        //   直扫底表，不经日表（日表 7/31 才起且不回补，分娩窝数实测漏 40%）。
        int judgeDays = farrowJudgeDeadlineDays();
        Map<String, Object> cohort = aggregateQueryMapper.selectCohortOutcome(tenantId, from, to, judgeDays);
        int mateLitter = mapInt(cohort, "bred");
        int cohortFarrow = mapInt(cohort, "farrow");
        // 当月配种母猪头数（实时，COUNT(DISTINCT pig_id) —— spec「配种母猪头数」按头去重）
        int breedingSowCount = aggregateQueryMapper.countDistinctBreedingSowInRange(tenantId, dtFrom, dtTo);

        BigDecimal farrowRate = pct(ratio(cohortFarrow, mateLitter));
        // 配种率% = 当月配种母猪头数 / ((Σ日生产母猪 + Σ日230后备)/当月天数) × 100
        //   配种率分母用 230 后备（与 NPD 分母口径不同，各公式不共用变量）
        BigDecimal breedDenom = divide(new BigDecimal(sumEndProdSow + sumEndReserve230), daysInMonth);
        BigDecimal breedRate = breedDenom.signum() == 0
            ? BigDecimal.ZERO
            : scale3(new BigDecimal(breedingSowCount).divide(breedDenom, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100")));
        // 断配间隔（实时配对）
        Map<String, Object> wbMonth = aggregateQueryMapper.sumWeanMateIntervalRange(tenantId, dtFrom, dtTo);
        int wbDays = mapInt(wbMonth, "totalDays");
        int wbCnt = mapInt(wbMonth, "totalCount");
        BigDecimal weanBreedInterval = wbCnt > 0 ? scale3(divide(new BigDecimal(wbDays), wbCnt)) : BigDecimal.ZERO;
        // 月均生产母猪存栏（row114 新列）= Σ日期末生产母猪头数 / 当月「已历天数」（= 当月已落盘日表天数，
        //   与年表 daysElapsed 同口径；非自然月天数——当月未走完时按已历天数，避免分母虚大拉低月均存栏）
        int daysElapsedInMonth = aggregateQueryMapper.countIndicatorDays(tenantId, from, to);
        BigDecimal avgProdSowStock = scale3(divide(new BigDecimal(sumEndProdSow), daysElapsedInMonth));
        // 月均NPD天数（row114 口径重构）= Σ日非生产母猪 / 月均生产母猪存栏
        //   分子去掉 230 后备（纯非生产母猪 = Σ日 npd_days = Σ日 end_nonprod_sow_count）；
        //   分母改纯生产母猪存栏（avgProdSowStock）；分母 0 → 0。
        BigDecimal npdDays = avgProdSowStock.signum() == 0
            ? BigDecimal.ZERO
            : scale3(new BigDecimal(sumEndNonprodSow).divide(avgProdSowStock, 6, RoundingMode.HALF_UP));
        // 窝均
        BigDecimal avgBornPerLitter = scale3(divide(new BigDecimal(sumTotalBorn), sumFarrowSow));
        BigDecimal avgLiveBornPerLitter = scale3(divide(new BigDecimal(sumLiveBorn), sumFarrowSow));
        BigDecimal avgWeanedPerLitter = scale3(divide(new BigDecimal(sumWeanedPiglet), sumWeaningSow));
        // 分娩舍损失率% = 按窝配对（该窝活仔 − 该窝断奶）/ 该窝活仔，只统计当月已断奶的窝。
        //   旧口径分子取 pig_type='piglet' 的 DIE 事件，要求仔猪有个体档案；哺乳仔猪多数还没打耳标，
        //   分子恒 0。窝级配对后分子分母同属一批窝，也不会把「已分娩未到断奶期」的窝误算成损失。
        Map<String, Object> loss = aggregateQueryMapper.selectFarrowHouseLoss(tenantId, from, to);
        BigDecimal farrowLossRate = farrowHouseLossRate(loss);

        MonthlyProduction m = existingOrNew(selectMonth(tenantId, month), MonthlyProduction::new);
        m.setStatMonth(month.toString());
        m.setIntroduceCount(introduceCount);
        m.setIntroduceBoarCount(introduceBoarCount);
        m.setBornCount(bornCount);
        m.setWeanedCount(weanedCount);
        m.setDeathCount(deathCount);
        m.setCullingCount(cullingCount);
        m.setMarketingCount(marketingCount);
        m.setMarketingWeight(marketingWeight);
        m.setMateLitterCount(mateLitter);
        m.setCohortFarrowCount(cohortFarrow);
        m.setFarrowRate(farrowRate);
        m.setBreedRate(breedRate);
        m.setWeanBreedInterval(weanBreedInterval);
        m.setAbnormalCount(sumAbnormal);
        m.setAvgProdSowStock(avgProdSowStock);
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
        // row44②：年度只统计「T-1」（不含今天及以后）——右开界 = min(次年 1 月 1 日, 今天)。
        //   历史年恒取全年，当年取到昨天为止；统计窗口只由 year 决定，不随触发日截断。
        LocalDate to = LocalDate.of(year + 1, 1, 1);
        LocalDate today = LocalDate.now();
        if (today.isBefore(to)) {
            to = today;
        }
        LocalDateTime dtFrom = from.atStartOfDay();
        LocalDateTime dtTo = to.atStartOfDay();

        // row44①（邓博 test 流程性问题 row44 明确要求）：基础指标**取月表 Σ，不直接扫业务表**。
        //   月表各行已按日表 Σ 落盘（当月行为 T-1 口径，caller 先跑 upsertMonthlyProduction 刷新当月行），
        //   Σ 月即得 T-1 年度总量，年↔月逐层可对账（年 = 其各月之和）。仅汇总有月行的月份；
        //   无月行的月份（如日/月统计上线前）不计入——年表口径 = 月表已跟踪运营月之和。
        String fromMonth = String.format("%04d-01", year);
        // 月行汇总右闭界 = min(当年 12 月, T-1 所在月)：历史年恒取全年 12 个月，当年只到昨天所在月。
        YearMonth endMonth = YearMonth.of(year, 12);
        YearMonth tMinus1Month = YearMonth.from(LocalDate.now().minusDays(1));
        if (tMinus1Month.isBefore(endMonth)) {
            endMonth = tMinus1Month;
        }
        String toMonth = endMonth.toString();
        Map<String, Object> mSum = aggregateQueryMapper.sumMonthlyProductionRange(tenantId, fromMonth, toMonth);
        int introduceCount = mapInt(mSum, "introduceCount");
        int introduceBoarCount = mapInt(mSum, "introduceBoarCount");
        int bornCount = mapInt(mSum, "bornCount");
        int weanedCount = mapInt(mSum, "weanedCount");
        int deathCount = mapInt(mSum, "deathCount");
        int cullingCount = mapInt(mSum, "cullingCount");
        int marketingCount = mapInt(mSum, "marketingCount");
        BigDecimal marketingWeight = mapBd(mSum, "marketingWeight");

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
        int sumDeathFattening = mapInt(sum, "sumDeathFattening"); // T7 肥猪死亡数
        int sumMarketingCount = mapInt(sum, "sumMarketingCount");
        BigDecimal sumMarketingWeight = mapBd(sum, "sumMarketingWeight");
        int sumEndProdSow = mapInt(sum, "sumEndProductionSow");
        int sumEndNonprodSow = mapInt(sum, "sumEndNonprodSow");

        // 已历天数 = 当年日表已落盘行数（年初到 T-1）；为 0 时分母兜底为 1 避免除 0
        int daysElapsed = aggregateQueryMapper.countIndicatorDays(tenantId, from, to);
        // 年均生产母猪存栏（row115 口径重构）= Σ日期末生产母猪头数 / 已历天数（去掉 230 后备，仅纯生产母猪）
        BigDecimal avgProdSowStock = scale3(divide(new BigDecimal(sumEndProdSow), daysElapsed));

        // 年配种头数（实时）
        int breedingCount = aggregateQueryMapper.countBreedingInRange(tenantId, dtFrom, dtTo);

        // 窝均活仔 = 总活仔 / 年分娩次数；窝均断奶 = 总断奶仔猪 / 总断奶母猪
        BigDecimal avgLiveBornPerLitter = scale3(divide(new BigDecimal(sumLiveBorn), sumFarrowSow));
        BigDecimal avgWeanedPerLitter = scale3(divide(new BigDecimal(sumWeanedPiglet), sumWeaningSow));

        // 断配间隔（实时配对）
        Map<String, Object> wbYear = aggregateQueryMapper.sumWeanMateIntervalRange(tenantId, dtFrom, dtTo);
        int wbDays = mapInt(wbYear, "totalDays");
        int wbCnt = mapInt(wbYear, "totalCount");
        BigDecimal weanBreedInterval = wbCnt > 0 ? scale3(divide(new BigDecimal(wbDays), wbCnt)) : BigDecimal.ZERO;

        // ---- PSY / 平均非生产天数：统计区间 + 年化（BRD-STAT-COHORT-001） ----
        // 区间起点 = 年初与「断奶记录最早业务日」的较晚者。断奶登记 2026-08 才启用，
        //   分子（断奶仔猪数）只有两个月而分母（母猪头日）取全年的话，年化会把这个残缺比值再放大
        //   365/N 倍。分子分母锚同一区间，年化才有意义。
        LocalDate minWeaning = aggregateQueryMapper.selectMinWeaningDate(tenantId, from, to);
        LocalDate psyFrom = minWeaning != null && minWeaning.isAfter(from) ? minWeaning : from;
        Map<String, Object> psyBase = aggregateQueryMapper.selectSowDaysInRange(tenantId, psyFrom, to);
        // 母猪头日 = Σ日期末生产母猪头数；psyStatDays = 该区间已落盘日表行数（年化乘数的分母）
        int psySowDays = mapInt(psyBase, "sowDays");
        int psyStatDays = mapInt(psyBase, "dayRows");
        // PSY = 区间断奶仔猪总数 × 365 / 母猪头日。
        //   甲方给的两种算法（「现算法年化」与「按头天数算」）展开后是同一个式子，此处用后者的写法。
        //   分子直扫底表 t_farm_pig_weaning，不走日表 Σ（日表断奶头数实测漏 24%）。
        //   旧式 (年分娩窝数 / 年均存栏) × 窝均断奶 还有个跨 cohort 混用：分娩窝与断奶窝差一个哺乳期，
        //   拿 A 批的窝数乘 B 批的窝均，此处一并消掉。
        int psyWeanedPiglet = aggregateQueryMapper.sumWeanedInRange(tenantId, psyFrom, to);
        BigDecimal psy = psySowDays <= 0
            ? BigDecimal.ZERO
            : scale3(new BigDecimal(psyWeanedPiglet).multiply(DAYS_PER_YEAR)
                .divide(new BigDecimal(psySowDays), 6, RoundingMode.HALF_UP));

        // 全年总NPD天数 = Σ日非生产母猪（去掉 230 后备）；年均NPD = 总NPD / 年均生产母猪存栏，再年化成「天/年」。
        // ⚠️ 年化只修量纲。该值当前仍显著低于行业区间，根因是断奶/配种事件录入不全 —— 母猪长期卡在
        //    FM(哺乳)/PZ(配种) 态被算作生产态，非生产段压根没产生。属数据完整度问题，不在本次口径修复内。
        int totalNpdDays = sumEndNonprodSow;
        BigDecimal avgNpdDays = avgProdSowStock.signum() == 0
            ? BigDecimal.ZERO
            : annualize(scale3(new BigDecimal(totalNpdDays)
                .divide(avgProdSowStock, 6, RoundingMode.HALF_UP)), daysElapsed);

        // 年分娩率（配种批次口径）：判定日落在本年且已到期的批次为分母，其中按期分娩的为分子。
        //   旧口径分子走日表 Σ 且带 YEAR(分娩日−N)=当年 过滤（把 1-4 月分娩整段排除、配种却照算分母），
        //   分母直取全年配种次数 —— 分子分母既不同批也不同窗口。
        Map<String, Object> yearCohort = aggregateQueryMapper.selectCohortOutcome(
            tenantId, from, to, farrowJudgeDeadlineDays());
        int cohortMatured = mapInt(yearCohort, "bred");
        int cohortFarrow = mapInt(yearCohort, "farrow");
        BigDecimal yearFarrowRate = pct(ratio(cohortFarrow, cohortMatured));
        // 平均出栏重 = Σ日出栏总重 / Σ日出栏头数
        BigDecimal avgMarketingWeight = scale3(divide(sumMarketingWeight, sumMarketingCount));
        // 分娩舍损失率% = 按窝配对（该窝活仔 − 该窝断奶）/ 该窝活仔，只统计已断奶的窝
        BigDecimal farrowLossRate = farrowHouseLossRate(
            aggregateQueryMapper.selectFarrowHouseLoss(tenantId, from, to));

        AnnualIndicator a = existingOrNew(selectYear(tenantId, year), AnnualIndicator::new);
        a.setStatYear(year);
        a.setIntroduceCount(introduceCount);
        a.setIntroduceBoarCount(introduceBoarCount);
        a.setBornCount(bornCount);
        a.setWeanedCount(weanedCount);
        a.setDeathCount(deathCount);
        a.setCullingCount(cullingCount);
        a.setMarketingCount(marketingCount);
        a.setMarketingWeight(marketingWeight);
        a.setPsy(psy);
        a.setMortalityRate(mortalityRate);
        a.setAvgProdSowStock(avgProdSowStock);
        a.setBreedingCount(breedingCount);
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
        a.setYearBatchFarrowCount(cohortFarrow);
        a.setCohortMaturedCount(cohortMatured);
        a.setYearFarrowRate(yearFarrowRate);
        a.setPsyStatFrom(psyFrom);
        a.setPsyStatDays(psyStatDays);
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
