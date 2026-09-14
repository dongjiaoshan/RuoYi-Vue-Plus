package org.dromara.djs.breed.dashboard.service.impl;

import org.dromara.djs.breed.dashboard.domain.AnnualIndicator;
import org.dromara.djs.breed.dashboard.domain.FarmIndicatorRecord;
import org.dromara.djs.breed.dashboard.domain.MonthlyProduction;
import org.dromara.djs.breed.dashboard.domain.SowRecord;
import org.dromara.djs.breed.dashboard.domain.vo.Activity7dVo;
import org.dromara.djs.breed.dashboard.domain.vo.AgeBucketVo;
import org.dromara.djs.breed.dashboard.domain.vo.AnnualIndicatorVo;
import org.dromara.djs.breed.dashboard.domain.vo.BreedingAnnualVo;
import org.dromara.djs.breed.dashboard.domain.vo.DailyOverviewVo;
import org.dromara.djs.breed.dashboard.domain.vo.InventoryVo;
import org.dromara.djs.breed.dashboard.domain.vo.FarmIndicatorRecordVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthlyComparisonVo;
import org.dromara.djs.breed.dashboard.mapper.AggregateQueryMapper;
import org.dromara.djs.breed.dashboard.mapper.AnnualIndicatorMapper;
import org.dromara.djs.breed.dashboard.mapper.FarmIndicatorRecordMapper;
import org.dromara.djs.breed.dashboard.mapper.MonthlyProductionMapper;
import org.dromara.djs.breed.dashboard.mapper.SowRecordMapper;
import org.dromara.djs.breed.production.mapper.SowPerformanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DashboardServiceImpl} 单元测试（BRD-DASH-001）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>getCurrentInventory：分组 COUNT 5 类 + sow 4 lifecycle</li>
 *   <li>getMonthlyComparison：当月 vs 上月 trend 判定（better/worse/flat）+ deathCount 反向（升=worse）</li>
 *   <li>getActivity7d：近 7 天 list 升序 + 字段映射</li>
 *   <li>getAnnualIndicator：psy + mortalityRate 4 位小数</li>
 *   <li>triggerAggregate：UPSERT sow_record + monthly + annual 三表全调用</li>
 *   <li>error path：MonthlyProduction null 时 KpiCompare current=0 prev=0 trend=flat</li>
 * </ul>
 *
 * <p><b>关键断言（契约层）</b>：deathCount 来源于 status_record event_type=DIE 调用（不查 pig.end_date）。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DashboardServiceImpl 单元测试")
class DashboardServiceImplTest {

    @Mock
    private SowRecordMapper sowRecordMapper;
    @Mock
    private MonthlyProductionMapper monthlyProductionMapper;
    @Mock
    private AnnualIndicatorMapper annualIndicatorMapper;
    @Mock
    private AggregateQueryMapper aggregateQueryMapper;
    @Mock
    private FarmIndicatorRecordMapper farmIndicatorRecordMapper;
    @Mock
    private SowPerformanceMapper sowPerformanceMapper;
    @Mock
    private org.dromara.djs.breed.production.service.IProductionCycleConfigService productionCycleConfigService;
    @Mock
    private org.dromara.djs.breed.production.service.IFattenAgeStageService fattenAgeStageService;

    private DashboardServiceImpl service;

    @BeforeEach
    void setup() {
        service = new DashboardServiceImpl(
            sowRecordMapper, monthlyProductionMapper, annualIndicatorMapper, aggregateQueryMapper,
            farmIndicatorRecordMapper, sowPerformanceMapper, productionCycleConfigService,
            fattenAgeStageService);
    }

    @Test
    @DisplayName("getCurrentInventory: 5 类 pig_type COUNT + sow lifecycle 4 项分组")
    void testGetCurrentInventory() {
        when(aggregateQueryMapper.countInventoryByType(anyString())).thenReturn(List.of(
            mapOf("pigType", "sow", "cnt", 23),
            mapOf("pigType", "boar", "cnt", 5),
            mapOf("pigType", "piglet", "cnt", 43),
            mapOf("pigType", "fattening", "cnt", 454),
            mapOf("pigType", "reserve", "cnt", 5)
        ));
        when(aggregateQueryMapper.countByLifecycle(anyString(), eq("sow"))).thenReturn(List.of(
            mapOf("lifecycle", "PZ", "cnt", 13),
            mapOf("lifecycle", "FM", "cnt", 6),
            mapOf("lifecycle", "KH", "cnt", 4)
        ));

        InventoryVo vo = service.getCurrentInventory();

        assertThat(vo.getInventoryByType())
            .containsEntry("sow", 23)
            .containsEntry("boar", 5)
            .containsEntry("piglet", 43)
            .containsEntry("fattening", 454)
            .containsEntry("reserve", 5);
        assertThat(vo.getSowByLifecycle())
            .containsEntry("PZ", 13)
            .containsEntry("FM", 6)
            .containsEntry("KH", 4);
    }

    @Test
    @DisplayName("getMonthlyComparison: introduceCount 上升 → trend=better（红）")
    void testMonthlyComparisonIntroduceUpIsBetter() {
        MonthlyProduction curr = newMonth(YearMonth.of(2026, 5));
        curr.setIntroduceCount(100);
        MonthlyProduction prev = newMonth(YearMonth.of(2026, 4));
        prev.setIntroduceCount(80);

        stubMonthlySelectByMonth(YearMonth.of(2026, 5), curr, YearMonth.of(2026, 4), prev);

        MonthlyComparisonVo vo = service.getMonthlyComparison(YearMonth.of(2026, 5));

        assertThat(vo.getCurrentMonth()).isEqualTo("2026-05");
        assertThat(vo.getPreviousMonth()).isEqualTo("2026-04");
        assertThat(vo.getIntroduceCount().getCurrent()).isEqualTo(new BigDecimal("100"));
        assertThat(vo.getIntroduceCount().getPrevious()).isEqualTo(new BigDecimal("80"));
        assertThat(vo.getIntroduceCount().getDiff()).isEqualTo(new BigDecimal("20"));
        assertThat(vo.getIntroduceCount().getTrend()).isEqualTo("better");
    }

    @Test
    @DisplayName("getMonthlyComparison: deathCount 上升 → trend=worse（绿）—— 反向 KPI")
    void testMonthlyComparisonDeathUpIsWorse() {
        MonthlyProduction curr = newMonth(YearMonth.of(2026, 5));
        curr.setDeathCount(8);
        MonthlyProduction prev = newMonth(YearMonth.of(2026, 4));
        prev.setDeathCount(3);
        stubMonthlySelectByMonth(YearMonth.of(2026, 5), curr, YearMonth.of(2026, 4), prev);

        MonthlyComparisonVo vo = service.getMonthlyComparison(YearMonth.of(2026, 5));

        assertThat(vo.getDeathCount().getTrend()).isEqualTo("worse");
    }

    @Test
    @DisplayName("getMonthlyComparison: 持平 → trend=flat（黑）")
    void testMonthlyComparisonFlat() {
        MonthlyProduction curr = newMonth(YearMonth.of(2026, 5));
        curr.setBornCount(50);
        MonthlyProduction prev = newMonth(YearMonth.of(2026, 4));
        prev.setBornCount(50);
        stubMonthlySelectByMonth(YearMonth.of(2026, 5), curr, YearMonth.of(2026, 4), prev);

        MonthlyComparisonVo vo = service.getMonthlyComparison(YearMonth.of(2026, 5));

        assertThat(vo.getBornCount().getTrend()).isEqualTo("flat");
    }

    @Test
    @DisplayName("getMonthlyComparison: 上月数据缺失 → current=N prev=0 diff=N trend=better")
    void testMonthlyComparisonPrevMissing() {
        MonthlyProduction curr = newMonth(YearMonth.of(2026, 5));
        curr.setIntroduceCount(10);
        stubMonthlySelectByMonth(YearMonth.of(2026, 5), curr, YearMonth.of(2026, 4), null);

        MonthlyComparisonVo vo = service.getMonthlyComparison(YearMonth.of(2026, 5));

        assertThat(vo.getIntroduceCount().getCurrent()).isEqualTo(new BigDecimal("10"));
        assertThat(vo.getIntroduceCount().getPrevious()).isEqualTo(BigDecimal.ZERO);
        assertThat(vo.getIntroduceCount().getDiff()).isEqualTo(new BigDecimal("10"));
        assertThat(vo.getIntroduceCount().getTrend()).isEqualTo("better");
    }

    @Test
    @DisplayName("getActivity7d: 升序 list 字段映射完整")
    void testGetActivity7d() {
        SowRecord r1 = new SowRecord();
        r1.setStatDate(LocalDate.of(2026, 5, 20));
        r1.setSowTotal(23);
        r1.setSowPregnant(5);
        r1.setSowFarrow(6);
        r1.setSowWeaning(2);
        r1.setSowIdle(10);
        r1.setSowCullingCount(0);
        r1.setSowDeathCount(0);
        r1.setPigletTotal(43);
        when(sowRecordMapper.selectRangeAsc(anyString(), any(LocalDate.class)))
            .thenReturn(List.of(r1));

        Activity7dVo vo = service.getActivity7d();

        assertThat(vo.getRows()).hasSize(1);
        Activity7dVo.DailyRow row = vo.getRows().get(0);
        assertThat(row.getStatDate()).isEqualTo("2026-05-20");
        assertThat(row.getSowTotal()).isEqualTo(23);
        assertThat(row.getSowPregnant()).isEqualTo(5);
        assertThat(row.getPigletTotal()).isEqualTo(43);
    }

    @Test
    @DisplayName("getAnnualIndicator: psy / mortalityRate 4 位小数 + null → 0")
    void testGetAnnualIndicator() {
        AnnualIndicator ai = new AnnualIndicator();
        ai.setStatYear((short) 2026);
        ai.setIntroduceCount(100);
        ai.setBornCount(800);
        ai.setWeanedCount(750);
        ai.setDeathCount(5);
        ai.setMarketingWeight(new BigDecimal("12500.50"));
        ai.setPsy(new BigDecimal("12.5000"));
        ai.setMortalityRate(new BigDecimal("0.0123"));
        when(annualIndicatorMapper.selectOne(any())).thenReturn(ai);

        AnnualIndicatorVo vo = service.getAnnualIndicator(2026);

        assertThat(vo.getStatYear()).isEqualTo((short) 2026);
        assertThat(vo.getIntroduceCount()).isEqualTo(100);
        assertThat(vo.getBornCount()).isEqualTo(800);
        assertThat(vo.getPsy()).isEqualTo(new BigDecimal("12.5000"));
        assertThat(vo.getMortalityRate()).isEqualTo(new BigDecimal("0.0123"));
        assertThat(vo.getMarketingWeight()).isEqualTo(new BigDecimal("12500.50"));
    }

    @Test
    @DisplayName("getAnnualIndicator: 该年无聚合数据 → 全 0")
    void testGetAnnualIndicatorEmpty() {
        when(annualIndicatorMapper.selectOne(any())).thenReturn(null);

        AnnualIndicatorVo vo = service.getAnnualIndicator(2025);

        assertThat(vo.getIntroduceCount()).isZero();
        assertThat(vo.getPsy()).isEqualTo(BigDecimal.ZERO);
        assertThat(vo.getMortalityRate()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getBreedingAnnual: 取年表（率类 ×100 百分比 / 总产仔=total_born_count 非活仔）")
    void testGetBreedingAnnual() {
        AnnualIndicator ai = new AnnualIndicator();
        ai.setStatYear((short) 2026);
        ai.setYearFarrowRate(new BigDecimal("55.560"));
        ai.setWeanBreedInterval(new BigDecimal("35.000"));
        ai.setAvgNpdDays(new BigDecimal("94.500"));
        ai.setTotalBornCount(31);
        ai.setTotalLiveBorn(21);
        ai.setAvgLiveBornPerLitter(new BigDecimal("4.200"));
        ai.setAvgWeanedPerLitter(new BigDecimal("10.333"));
        ai.setFarrowLossRate(new BigDecimal("9.520"));
        when(annualIndicatorMapper.selectOne(any())).thenReturn(ai);

        BreedingAnnualVo vo = service.getBreedingAnnual(2026);

        // 配种率 V1 口径同分娩率，均取 year_farrow_rate（已 ×100，前端直接拼 %）
        assertThat(vo.getMateRate()).isEqualByComparingTo("55.560");
        assertThat(vo.getFarrowRate()).isEqualByComparingTo("55.560");
        assertThat(vo.getWeanMateInterval()).isEqualByComparingTo("35.000");
        assertThat(vo.getAvgNonProductiveDays()).isEqualByComparingTo("94.500");
        assertThat(vo.getTotalBornCount()).isEqualByComparingTo("31");   // 总产仔（含死胎），非活仔 21
        assertThat(vo.getTotalLiveBorn()).isEqualByComparingTo("21");
        assertThat(vo.getFarrowingLossRate()).isEqualByComparingTo("9.520");
    }

    @Test
    @DisplayName("getBreedingAnnual: 该年无年表数据 → 全 0")
    void testGetBreedingAnnualEmpty() {
        when(annualIndicatorMapper.selectOne(any())).thenReturn(null);

        BreedingAnnualVo vo = service.getBreedingAnnual(2025);

        assertThat(vo.getFarrowRate()).isEqualByComparingTo("0");
        assertThat(vo.getTotalBornCount()).isEqualByComparingTo("0");
        assertThat(vo.getFarrowingLossRate()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("triggerAggregate: 调用 status_record COUNT (DIE/ELIMINATE) — 不查 pig.end_date")
    void testTriggerAggregateUsesStatusRecord() {
        // 准备：sow / piglet lifecycle 分组 + DIE/ELIMINATE COUNT
        when(aggregateQueryMapper.countByLifecycle(anyString(), eq("sow")))
            .thenReturn(List.of(mapOf("lifecycle", "PZ", "cnt", 2)));
        when(aggregateQueryMapper.countByLifecycle(anyString(), eq("piglet")))
            .thenReturn(List.of(mapOf("lifecycle", "HB", "cnt", 6)));
        when(aggregateQueryMapper.countStatusEventInRange(anyString(), eq("DIE"), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(1);
        when(aggregateQueryMapper.countStatusEventInRange(anyString(), eq("ELIMINATE"), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(0);
        when(aggregateQueryMapper.sumLiveBornInRange(anyString(), any(), any())).thenReturn(0);
        when(aggregateQueryMapper.sumWeanedInRange(anyString(), any(), any())).thenReturn(0);
        Map<String, Object> mk = new LinkedHashMap<>();
        mk.put("cnt", 0L);
        mk.put("weight", BigDecimal.ZERO);
        when(aggregateQueryMapper.aggregateMarketingInRange(anyString(), any(), any())).thenReturn(mk);
        when(aggregateQueryMapper.countAliveSows(anyString())).thenReturn(20);
        // BRD-STAT-001 新增链路 stub（无母猪 → sow_performance 不写；日表 existing null → insert）
        when(aggregateQueryMapper.selectAliveSows(anyString())).thenReturn(new ArrayList<>());
        when(aggregateQueryMapper.snapshotByTypeStatusOnDate(anyString(), any())).thenReturn(new ArrayList<>());
        when(sowRecordMapper.selectOne(any())).thenReturn(null);
        when(monthlyProductionMapper.selectOne(any())).thenReturn(null);
        when(annualIndicatorMapper.selectOne(any())).thenReturn(null);
        when(farmIndicatorRecordMapper.selectOne(any())).thenReturn(null);

        String result = service.triggerAggregate(LocalDate.of(2026, 5, 25));

        // 关键断言：status_record DIE / ELIMINATE 至少调用一次（本 ticket 契约 — 不查 pig.end_date）
        verify(aggregateQueryMapper, atLeastOnce())
            .countStatusEventInRange(anyString(), eq("DIE"), any(), any());
        verify(aggregateQueryMapper, atLeastOnce())
            .countStatusEventInRange(anyString(), eq("ELIMINATE"), any(), any());

        // 5 表全 INSERT（existing 都返 null → 走 insert 分支；sow_performance 无母猪故不写）
        verify(farmIndicatorRecordMapper).insert(any(FarmIndicatorRecord.class));
        verify(sowRecordMapper).insert(any(SowRecord.class));
        verify(monthlyProductionMapper).insert(any(MonthlyProduction.class));
        verify(annualIndicatorMapper).insert(any(AnnualIndicator.class));

        assertThat(result).contains("ok").contains("2026-05-25").contains("indicator_record");
    }

    @Test
    @DisplayName("triggerAggregate: targetDate=null → 跑 T-1（昨天）")
    void testTriggerAggregateDefaultsToYesterday() {
        when(aggregateQueryMapper.countByLifecycle(anyString(), any())).thenReturn(new ArrayList<>());
        when(aggregateQueryMapper.countStatusEventInRange(anyString(), any(), any(), any())).thenReturn(0);
        when(aggregateQueryMapper.sumLiveBornInRange(anyString(), any(), any())).thenReturn(0);
        when(aggregateQueryMapper.sumWeanedInRange(anyString(), any(), any())).thenReturn(0);
        Map<String, Object> mk = new LinkedHashMap<>();
        mk.put("cnt", 0L);
        mk.put("weight", BigDecimal.ZERO);
        when(aggregateQueryMapper.aggregateMarketingInRange(anyString(), any(), any())).thenReturn(mk);
        when(aggregateQueryMapper.countAliveSows(anyString())).thenReturn(0);

        String result = service.triggerAggregate(null);

        String yesterday = LocalDate.now().minusDays(1).toString();
        assertThat(result).contains(yesterday);
        // 三表至少各 insert / update 一次（existing null → insert）
        verify(sowRecordMapper, atLeastOnce()).insert(any(SowRecord.class));
    }

    // ============================================================
    //  FIX-MGMT-MP-BRD-001 新增端点
    // ============================================================

    @Test
    @DisplayName("getDailyOverview: 15 格读 t_farm_indicator_record 落盘值（r124 · 显示昨日/指定日指标）")
    void testGetDailyOverview() {
        // r124：养殖场日概览改读 t_farm_indicator_record（不再实时聚合）；mock 该日整行落盘值
        FarmIndicatorRecord rec = new FarmIndicatorRecord();
        rec.setStatDate(LocalDate.of(2026, 6, 9));
        rec.setFarrowSowCount(2);
        rec.setBreedingSowCount(3);
        rec.setWeaningSowCount(1);
        rec.setAbnormalSowCount(1);
        rec.setIntroduceSowCount(5);
        rec.setHeatNoBreedCount(6);
        rec.setDeathPigCount(0);
        rec.setCullingPigCount(0);
        rec.setTotalBornCount(15);
        rec.setLiveBornCount(12);
        rec.setPigletTagCount(45);
        rec.setWeanedPigletCount(11);
        rec.setGrowthRecordCount(7);
        rec.setCastratePigCount(4);
        rec.setMedicatedPigCount(9);
        when(farmIndicatorRecordMapper.selectOne(any())).thenReturn(rec);

        DailyOverviewVo vo = service.getDailyOverview(LocalDate.of(2026, 6, 9));

        assertThat(vo.getDate()).isEqualTo("2026-06-09");
        assertThat(vo.getCells()).hasSize(15);
        // 第 1/2 格分娩/配种
        assertThat(vo.getCells().get(0).getMetric()).isEqualTo("分娩母猪数");
        assertThat(vo.getCells().get(0).getValue()).isEqualTo(2);
        assertThat(vo.getCells().get(1).getMetric()).isEqualTo("配种母猪数");
        assertThat(vo.getCells().get(1).getValue()).isEqualTo(3);
        // r124 焦点：查情不配种数（第 6 格）读 heat_no_breed_count 列
        assertThat(vo.getCells().get(5).getMetric()).isEqualTo("查情不配种数");
        assertThat(vo.getCells().get(5).getValue()).isEqualTo(6);
        // 死亡 / 淘汰按原型作"猪只数"（第 7/8 格）
        assertThat(vo.getCells().get(6).getMetric()).isEqualTo("死亡猪只数");
        assertThat(vo.getCells().get(7).getMetric()).isEqualTo("淘汰猪只数");
        // 断奶仔猪数（第 12 格）
        assertThat(vo.getCells().get(11).getMetric()).isEqualTo("断奶仔猪数");
        assertThat(vo.getCells().get(11).getValue()).isEqualTo(11);
        // 末行 3 格 = 生长记录数 / 阉割猪只数 / 用药猪只数（13/14/15）
        assertThat(vo.getCells().get(12).getMetric()).isEqualTo("生长记录数");
        assertThat(vo.getCells().get(12).getValue()).isEqualTo(7);
        assertThat(vo.getCells().get(13).getMetric()).isEqualTo("阉割猪只数");
        assertThat(vo.getCells().get(13).getValue()).isEqualTo(4);
        assertThat(vo.getCells().get(14).getMetric()).isEqualTo("用药猪只数");
        assertThat(vo.getCells().get(14).getValue()).isEqualTo(9);
    }

    @Test
    @DisplayName("getFatteningAgeDistribution: 6 桶按 #7.6 边界正确归桶")
    void testGetFatteningAgeDistribution() {
        when(aggregateQueryMapper.selectFatteningAges(anyString())).thenReturn(List.of(
            ageRow(10),   // 保育期 <43
            ageRow(42),   // 保育期 <43
            ageRow(43),   // 43-70
            ageRow(70),   // 43-70
            ageRow(135),  // 71-135
            ageRow(210),  // 136-210
            ageRow(245),  // 211-245
            ageRow(400)   // 245+
        ));

        List<AgeBucketVo> list = service.getFatteningAgeDistribution();

        assertThat(list).hasSize(6);
        assertThat(list.get(0).getLabel()).isEqualTo("保育期(<43天)");
        assertThat(list.get(0).getCount()).isEqualTo(2);  // 10,42
        assertThat(list.get(1).getCount()).isEqualTo(2);  // 43,70
        assertThat(list.get(2).getCount()).isEqualTo(1);  // 135
        assertThat(list.get(3).getCount()).isEqualTo(1);  // 210
        assertThat(list.get(4).getCount()).isEqualTo(1);  // 245
        assertThat(list.get(5).getCount()).isEqualTo(1);  // 400
    }

    // ============================================================
    //  BRD-STAT-001 — 日表落盘 / 母猪性能 / 历史读端点
    // ============================================================

    @Test
    @DisplayName("upsertFarmIndicator: 期末存栏快照按 pig_type+current_status 正确归类（生产/后备/非生产母猪 + 公/肥/仔）")
    void testUpsertFarmIndicatorEndStock() {
        // 期末快照：sow PZ=3(生产) / sow HB=2(后备) / sow KH=1(生产且非生产) / boar=4 / fattening=30 / piglet=20
        when(aggregateQueryMapper.snapshotByTypeStatusOnDate(anyString(), any())).thenReturn(List.of(
            snap("sow", "PZ", 3),
            snap("sow", "HB", 2),
            snap("sow", "KH", 1),
            snap("boar", "", 4),
            snap("fattening", "", 30),
            snap("piglet", "", 20)
        ));
        when(aggregateQueryMapper.countReserve230OnSnapshot(anyString(), any())).thenReturn(1);
        // 出栏聚合 stub：2 头 / 200kg / 背膘 90mm 共 2 头有背膘
        Map<String, Object> mkt = new LinkedHashMap<>();
        mkt.put("cnt", 2L);
        mkt.put("weight", new BigDecimal("200"));
        mkt.put("backfatSum", new BigDecimal("90"));
        mkt.put("backfatCnt", 2L);
        when(aggregateQueryMapper.aggregateMarketingForDay(anyString(), any(), any())).thenReturn(mkt);
        // 断奶总重 / 饲养天数(日增重分母) / 生长天数(独立指标) stub
        Map<String, Object> wean = new LinkedHashMap<>();
        wean.put("weanWeightSum", new BigDecimal("40"));
        wean.put("marketingWeightWeaned", new BigDecimal("200"));
        wean.put("feedDaysSum", 200L);
        wean.put("growthDaysSum", 320L);
        when(aggregateQueryMapper.aggregateMarketingWeanForDay(anyString(), any(), any())).thenReturn(wean);
        // 不写月/年路径的 sow_performance（无母猪）
        when(aggregateQueryMapper.selectAliveSows(anyString())).thenReturn(new ArrayList<>());
        when(farmIndicatorRecordMapper.selectOne(any())).thenReturn(null);

        service.triggerAggregate(LocalDate.of(2026, 6, 25));

        org.mockito.ArgumentCaptor<FarmIndicatorRecord> cap = org.mockito.ArgumentCaptor.forClass(FarmIndicatorRecord.class);
        verify(farmIndicatorRecordMapper).insert(cap.capture());
        FarmIndicatorRecord r = cap.getValue();
        // 生产母猪 = 非后备非终止非空 = PZ(3) + KH(1) = 4
        assertThat(r.getEndProductionSowCount()).isEqualTo(4);
        // 后备 = HB = 2；非生产 = KH = 1
        assertThat(r.getEndReserveCount()).isEqualTo(2);
        assertThat(r.getEndNonprodSowCount()).isEqualTo(1);
        // 公/肥/仔
        assertThat(r.getEndBoarCount()).isEqualTo(4);
        assertThat(r.getEndFatteningCount()).isEqualTo(30);
        assertThat(r.getEndPigletCount()).isEqualTo(20);
        assertThat(r.getEndReserve230Count()).isEqualTo(1);
        // 出栏聚合：平均出栏重 = 200/2 = 100.000；平均背膘 = 90/2 = 45.000
        assertThat(r.getMarketingPigCount()).isEqualTo(2);
        assertThat(r.getAvgMarketingWeight()).isEqualByComparingTo(new BigDecimal("100.000"));
        assertThat(r.getAvgBackfatThickness()).isEqualByComparingTo(new BigDecimal("45.000"));
        // 净增重 = 出栏总重 200 - 断奶总重 40 = 160.000；
        // 日增重 = 净增重 160 / 饲养总天数 200 = 0.800（分母用饲养天数 feed，非生长天数 growth）
        assertThat(r.getNetGainWeight()).isEqualByComparingTo(new BigDecimal("160.000"));
        assertThat(r.getFeedTotalDays()).isEqualTo(200);
        assertThat(r.getGrowthTotalDays()).isEqualTo(320);
        assertThat(r.getDailyGainWeight()).isEqualByComparingTo(new BigDecimal("0.800"));
    }

    @Test
    @DisplayName("upsertSowPerformance: 单头母猪累计 + 窝均 + 平均怀孕天数 + NPD 公式")
    void testUpsertSowPerformance() {
        // 1 头活母猪 parity=4
        Map<String, Object> sow = new LinkedHashMap<>();
        sow.put("id", 1001L);
        sow.put("earNo", "0625-001");
        sow.put("parity", 4);
        when(aggregateQueryMapper.selectAliveSows(anyString())).thenReturn(List.of(sow));
        // 分娩累计：总产仔 48 / 总活仔 44 / 4 窝 / Σavg出生重 5.6 / 怀孕天数和 456 / 怀孕配对 4
        Map<String, Object> fa = new LinkedHashMap<>();
        fa.put("totalBorn", 48);
        fa.put("totalLiveBorn", 44);
        fa.put("litterCount", 4);
        fa.put("sumAvgBornWeight", new BigDecimal("5.6"));
        when(aggregateQueryMapper.sowFarrowAgg(anyString(), eq(1001L))).thenReturn(fa);
        // 平均怀孕天数（状态记录表 PZ→FM）：Σduration_days 456 / 4 条 → 114.00
        Map<String, Object> gest = new LinkedHashMap<>();
        gest.put("sumDays", 456);
        gest.put("cnt", 4);
        when(aggregateQueryMapper.sowGestationByStatus(anyString(), eq(1001L))).thenReturn(gest);
        // 断奶累计：总断奶 40 / 4 批 / Σavg断奶重 26.0
        Map<String, Object> we = new LinkedHashMap<>();
        we.put("totalWeaned", 40);
        we.put("weanCount", 4);
        we.put("sumAvgWeanedWeight", new BigDecimal("26.0"));
        when(aggregateQueryMapper.sowWeanAgg(anyString(), eq(1001L))).thenReturn(we);
        when(aggregateQueryMapper.sowAbnormalCount(anyString(), eq(1001L))).thenReturn(2);
        // 断奶-配种天数（状态记录表 DN→PZ）：Σduration_days 24 / 3 条 → 8.00
        Map<String, Object> wb = new LinkedHashMap<>();
        wb.put("sumDays", 24);
        wb.put("cnt", 3);
        when(aggregateQueryMapper.sowWeanBreedByStatus(anyString(), eq(1001L))).thenReturn(wb);
        // NPD（row113 邓博 2026-07-05 = admin row202）= Σduration_days where old∈{LC/KH/FQ/DN} 且 new=PZ 或 死淘
        when(aggregateQueryMapper.sumSowNpdDurationDays(anyString(), eq(1001L)))
            .thenReturn(new BigDecimal("62"));
        when(sowPerformanceMapper.selectOne(any())).thenReturn(null);
        // 让 trigger 其余路径不炸
        when(aggregateQueryMapper.snapshotByTypeStatusOnDate(anyString(), any())).thenReturn(new ArrayList<>());
        when(farmIndicatorRecordMapper.selectOne(any())).thenReturn(null);

        service.triggerAggregate(LocalDate.of(2026, 6, 25));

        org.mockito.ArgumentCaptor<org.dromara.djs.breed.production.domain.SowPerformance> cap =
            org.mockito.ArgumentCaptor.forClass(org.dromara.djs.breed.production.domain.SowPerformance.class);
        verify(sowPerformanceMapper).insert(cap.capture());
        org.dromara.djs.breed.production.domain.SowPerformance sp = cap.getValue();
        assertThat(sp.getPigId()).isEqualTo(1001L);
        assertThat(sp.getTotalBorn()).isEqualTo(48);
        assertThat(sp.getTotalLiveBorn()).isEqualTo(44);
        assertThat(sp.getTotalWeaned()).isEqualTo(40);
        assertThat(sp.getAbnormalTotal()).isEqualTo(2);
        // 平均出生重 = 5.6/4 = 1.400；平均断奶重 = 26.0/4 = 6.500
        assertThat(sp.getAvgBornWeight()).isEqualByComparingTo(new BigDecimal("1.400"));
        assertThat(sp.getAvgWeanedWeight()).isEqualByComparingTo(new BigDecimal("6.500"));
        // 平均怀孕天数 = 456/4 = 114.00（2 位）
        assertThat(sp.getAvgGestationDays()).isEqualByComparingTo(new BigDecimal("114.00"));
        // 断奶-配种天数 = 24/3 = 8.00
        assertThat(sp.getWeanBreedDays()).isEqualByComparingTo(new BigDecimal("8.00"));
        // 窝均（按 parity=4）：产仔 48/4=12.000，活仔 44/4=11.000，断奶 40/4=10.000
        assertThat(sp.getAvgBornPerLitter()).isEqualByComparingTo(new BigDecimal("12.000"));
        assertThat(sp.getAvgLiveBornPerLitter()).isEqualByComparingTo(new BigDecimal("11.000"));
        assertThat(sp.getAvgWeanedPerLitter()).isEqualByComparingTo(new BigDecimal("10.000"));
        // NPD（row113 = Σduration_days）= 62 → scale2 62.00
        assertThat(sp.getNpd()).isEqualByComparingTo(new BigDecimal("62.00"));
    }

    @Test
    @DisplayName("listIndicatorRecords: from/to 缺省查近 30 天 + 升序传递")
    void testListIndicatorRecords() {
        when(farmIndicatorRecordMapper.selectVoList(any())).thenReturn(new ArrayList<>());
        List<FarmIndicatorRecordVo> list = service.listIndicatorRecords(null, null);
        assertThat(list).isNotNull().isEmpty();
        // from 晚于 to → 空列表（不打 DB）
        List<FarmIndicatorRecordVo> bad = service.listIndicatorRecords(LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1));
        assertThat(bad).isEmpty();
    }

    // ============================================================
    //  BRD-STAT-003 / 004 — 存栏按业务时间重放 + 滚动重算
    // ============================================================

    @Test
    @DisplayName("triggerAggregate: 当日快照先删后按业务时间重放（不再「已有就跳过」冻结）")
    void testSnapshotIsRebuiltNotFrozen() {
        LocalDate d = LocalDate.of(2026, 9, 8);
        stubMinimalAggregatePaths();

        service.triggerAggregate(d);

        // 先删后建：补录晚于原采集时点也能把那一天重新算对（9/8 出栏 4 头、存栏没减就是被「跳过重采」坑的）
        verify(aggregateQueryMapper).deletePigSnapshotOnDate(anyString(), eq(d));
        verify(aggregateQueryMapper).rebuildPigSnapshotForDate(anyString(), eq(d));
        // 旧实现的 countSnapshotOnDate 门控已废：重放不看该日有没有旧快照
        verify(aggregateQueryMapper, never()).countSnapshotOnDate(anyString(), any());
    }

    @Test
    @DisplayName("fillEndStock: 该日无快照时期末存栏全 0，绝不回落实时主表（回落会把今天的猪群写进历史行）")
    void testEndStockNeverFallsBackToLiveMaster() {
        stubMinimalAggregatePaths();
        when(aggregateQueryMapper.snapshotByTypeStatusOnDate(anyString(), any())).thenReturn(new ArrayList<>());

        service.triggerAggregate(LocalDate.of(2026, 8, 1));

        org.mockito.ArgumentCaptor<FarmIndicatorRecord> cap = org.mockito.ArgumentCaptor.forClass(FarmIndicatorRecord.class);
        verify(farmIndicatorRecordMapper).insert(cap.capture());
        FarmIndicatorRecord r = cap.getValue();
        assertThat(r.getEndFatteningCount()).isZero();
        assertThat(r.getEndPigletCount()).isZero();
        assertThat(r.getEndProductionSowCount()).isZero();
        assertThat(r.getEndReserveCount()).isZero();
    }

    @Test
    @DisplayName("triggerAggregateRange: 区间非法 / 跨度超上限直接拒绝，不打 DB")
    void testTriggerAggregateRangeGuards() {
        assertThatThrownBy(() -> service.triggerAggregateRange(null, LocalDate.of(2026, 9, 13)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.triggerAggregateRange(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.triggerAggregateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 9, 13)))
            .isInstanceOf(IllegalArgumentException.class);
        verify(aggregateQueryMapper, never()).rebuildPigSnapshotForDate(anyString(), any());
    }

    /** triggerAggregate 全链路跑通所需的最小 stub（本节只关心快照 / 期末存栏，其余给空值不让它炸）。 */
    private void stubMinimalAggregatePaths() {
        Map<String, Object> mkt = new LinkedHashMap<>();
        mkt.put("cnt", 0L);
        mkt.put("weight", BigDecimal.ZERO);
        mkt.put("backfatSum", BigDecimal.ZERO);
        mkt.put("backfatCnt", 0L);
        when(aggregateQueryMapper.aggregateMarketingForDay(anyString(), any(), any())).thenReturn(mkt);
        Map<String, Object> wean = new LinkedHashMap<>();
        wean.put("weanWeightSum", BigDecimal.ZERO);
        wean.put("marketingWeightWeaned", BigDecimal.ZERO);
        wean.put("feedDaysSum", 0L);
        wean.put("growthDaysSum", 0L);
        when(aggregateQueryMapper.aggregateMarketingWeanForDay(anyString(), any(), any())).thenReturn(wean);
        when(aggregateQueryMapper.selectAliveSows(anyString())).thenReturn(new ArrayList<>());
        when(aggregateQueryMapper.snapshotByTypeStatusOnDate(anyString(), any())).thenReturn(new ArrayList<>());
        when(farmIndicatorRecordMapper.selectOne(any())).thenReturn(null);
        when(sowRecordMapper.selectOne(any())).thenReturn(null);
        when(monthlyProductionMapper.selectOne(any())).thenReturn(null);
        when(annualIndicatorMapper.selectOne(any())).thenReturn(null);
    }

    // ============================================================
    //  test helpers
    // ============================================================

    private static Map<String, Object> snap(String pigType, String cs, int cnt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pigType", pigType);
        m.put("cs", cs);
        m.put("cnt", cnt);
        return m;
    }

    private static Map<String, Object> ageRow(int age) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("age", age);
        return m;
    }

    // ============================================================
    //  BRD-STAT-COHORT-001：分娩率配种批次口径 + PSY 年化 + 产房损失率窝级配对
    // ============================================================

    @Test
    @DisplayName("cohort: 判定节点读 sow_farrow_judge_deadline_days，配成 0 时回退 119（0 会让所有批次瞬间到期）")
    void testFarrowJudgeDeadlineFallsBackOnZero() {
        stubAggregateSkeleton();
        when(productionCycleConfigService.getValue("sow_farrow_judge_deadline_days")).thenReturn(0);

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        verify(aggregateQueryMapper, atLeastOnce())
            .selectCohortOutcome(anyString(), any(), any(), eq(119));
        verify(aggregateQueryMapper, never())
            .selectCohortOutcome(anyString(), any(), any(), eq(0));
    }

    @Test
    @DisplayName("cohort: 判定节点取配置值（非 0 时不回退）")
    void testFarrowJudgeDeadlineUsesConfiguredValue() {
        stubAggregateSkeleton();
        when(productionCycleConfigService.getValue("sow_farrow_judge_deadline_days")).thenReturn(117);

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        verify(aggregateQueryMapper, atLeastOnce())
            .selectCohortOutcome(anyString(), any(), any(), eq(117));
    }

    @Test
    @DisplayName("年度: 分娩率 = cohort 分子/分母（不再用 年分娩头数/全年配种次数）")
    void testAnnualFarrowRateUsesCohort() {
        stubAggregateSkeleton();
        // 判定日落在本年且已到期的批次 35 头，其中按期分娩 34 头 → 97.14%
        when(aggregateQueryMapper.selectCohortOutcome(anyString(), any(), any(), anyInt()))
            .thenReturn(mapOfAll("bred", 35, "farrow", 34, "farrowLate", 0,
                "returnCount", 0, "emptyCount", 0, "abortCount", 0, "goneCount", 1, "undecided", 0));
        // 全年配种次数 199：旧口径会拿它当分母算出 17%，新口径不该再用它
        when(aggregateQueryMapper.countBreedingInRange(anyString(), any(), any())).thenReturn(199);

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        AnnualIndicator a = cap.getValue();
        assertThat(a.getCohortMaturedCount()).isEqualTo(35);
        assertThat(a.getYearBatchFarrowCount()).isEqualTo(34);
        assertThat(a.getYearFarrowRate()).isEqualByComparingTo("97.14");
        // breeding_count 语义不变，仍是全年配种次数原始计数，只是不再参与分娩率
        assertThat(a.getBreedingCount()).isEqualTo(199);
    }

    @Test
    @DisplayName("年度: PSY = 区间断奶仔猪 × 365 / 母猪头日，窗口锚断奶记录最早业务日并落盘")
    void testAnnualPsyAnnualizedOverWeaningWindow() {
        stubAggregateSkeleton();
        when(aggregateQueryMapper.selectMinWeaningDate(anyString(), any(), any()))
            .thenReturn(LocalDate.of(2026, 8, 8));
        when(aggregateQueryMapper.selectSowDaysInRange(anyString(), any(), any()))
            .thenReturn(mapOf("sowDays", 5430, "dayRows", 37));
        when(aggregateQueryMapper.sumWeanedInRange(anyString(), any(), any())).thenReturn(188);

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        AnnualIndicator a = cap.getValue();
        // 188 × 365 / 5430 = 12.637
        assertThat(a.getPsy()).isEqualByComparingTo("12.637");
        assertThat(a.getPsyStatFrom()).isEqualTo(LocalDate.of(2026, 8, 8));
        assertThat(a.getPsyStatDays()).isEqualTo(37);
    }

    @Test
    @DisplayName("年度: 无断奶记录时 PSY 窗口回落年初，母猪头日为 0 则 PSY=0 不炸")
    void testAnnualPsyZeroWhenNoSowDays() {
        stubAggregateSkeleton();
        when(aggregateQueryMapper.selectMinWeaningDate(anyString(), any(), any())).thenReturn(null);
        when(aggregateQueryMapper.selectSowDaysInRange(anyString(), any(), any()))
            .thenReturn(mapOf("sowDays", 0, "dayRows", 0));
        when(aggregateQueryMapper.sumWeanedInRange(anyString(), any(), any())).thenReturn(0);

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        AnnualIndicator a = cap.getValue();
        assertThat(a.getPsy()).isEqualByComparingTo("0");
        assertThat(a.getPsyStatFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("年度: 平均非生产天数年化（区间值 × 365/已历天数）")
    void testAnnualNpdAnnualized() {
        stubAggregateSkeleton();
        // Σ日非生产母猪 101，Σ日生产母猪 6409，已历天数 46 → 年均存栏 139.326
        // 区间 NPD = 101/139.326 = 0.725 → 年化 ×365/46 = 5.753
        when(aggregateQueryMapper.sumIndicatorRange(anyString(), any(), any()))
            .thenReturn(mapOfAll("sumEndProductionSow", 6409, "sumEndNonprodSow", 101));
        when(aggregateQueryMapper.countIndicatorDays(anyString(), any(), any())).thenReturn(46);

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        assertThat(cap.getValue().getAvgNpdDays()).isEqualByComparingTo("5.753");
    }

    @Test
    @DisplayName("产房损失率: Σ本窝哺乳期死淘数 / Σ本窝活仔数（D-0065）")
    void testFarrowHouseLossRateByLactationDeath() {
        stubAggregateSkeleton();
        when(aggregateQueryMapper.selectFarrowHouseLoss(anyString(), any(), any()))
            .thenReturn(mapOf("liveBorn", 200, "lactationDeath", 14));

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        // 14/200 = 7.00%
        assertThat(cap.getValue().getFarrowLossRate()).isEqualByComparingTo("7.00");
    }

    @Test
    @DisplayName("产房损失率: 没人填死淘数 → 0.00%（现存量数据即此形态）")
    void testFarrowHouseLossRateZeroWhenNoDeathRecorded() {
        stubAggregateSkeleton();
        when(aggregateQueryMapper.selectFarrowHouseLoss(anyString(), any(), any()))
            .thenReturn(mapOf("liveBorn", 188, "lactationDeath", 0));

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        assertThat(cap.getValue().getFarrowLossRate()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("产房损失率: 死淘数超过活仔数（脏数据）→ 100% 封顶，不越界")
    void testFarrowHouseLossRateCapsAtHundred() {
        stubAggregateSkeleton();
        when(aggregateQueryMapper.selectFarrowHouseLoss(anyString(), any(), any()))
            .thenReturn(mapOf("liveBorn", 10, "lactationDeath", 14));

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        assertThat(cap.getValue().getFarrowLossRate()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("月度: 分娩率分子分母来自同一批 cohort，并落 cohort_farrow_count / mate_litter_count")
    void testMonthlyFarrowRateUsesCohort() {
        stubAggregateSkeleton();
        when(aggregateQueryMapper.selectCohortOutcome(anyString(), any(), any(), anyInt()))
            .thenReturn(mapOfAll("bred", 15, "farrow", 14, "farrowLate", 0,
                "returnCount", 1, "emptyCount", 0, "abortCount", 0, "goneCount", 0, "undecided", 0));

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<MonthlyProduction> cap = ArgumentCaptor.forClass(MonthlyProduction.class);
        verify(monthlyProductionMapper).insert(cap.capture());
        MonthlyProduction m = cap.getValue();
        assertThat(m.getMateLitterCount()).isEqualTo(15);
        assertThat(m.getCohortFarrowCount()).isEqualTo(14);
        // 14/15 = 93.33%
        assertThat(m.getFarrowRate()).isEqualByComparingTo("93.33");
    }

    @Test
    @DisplayName("getCohortLedger: 各去向桶映射 + 分娩率按 farrow/matured 算（非 farrow/bred）")
    void testGetCohortLedgerMapsBucketsAndRate() {
        when(aggregateQueryMapper.selectCohortLedgerByBreedMonth(anyString(), any(), any(), anyInt(), any()))
            .thenReturn(List.of(mapOfAll(
                "breedMonth", "2026-07", "bred", 50, "matured", 11, "farrow", 9, "farrowLate", 1,
                "returnCount", 9, "emptyCount", 0, "abortCount", 2, "goneCount", 0,
                "undecided", 1, "pending", 28,
                "firstDeadline", java.sql.Date.valueOf("2026-10-29"),
                "lastDeadline", java.sql.Date.valueOf("2026-11-27"))));

        List<org.dromara.djs.breed.dashboard.domain.vo.CohortLedgerVo> rows = service.getCohortLedger(2026);

        assertThat(rows).hasSize(1);
        var r = rows.get(0);
        assertThat(r.getBreedMonth()).isEqualTo("2026-07");
        assertThat(r.getBred()).isEqualTo(50);
        assertThat(r.getMatured()).isEqualTo(11);
        assertThat(r.getReturnCount()).isEqualTo(9);
        assertThat(r.getAbortCount()).isEqualTo(2);
        assertThat(r.getPending()).isEqualTo(28);
        assertThat(r.getFirstDeadline()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(r.getLastDeadline()).isEqualTo(LocalDate.of(2026, 11, 27));
        // 分母是已到期数 11，不是配种数 50 —— 未到期的批次不该拉低分娩率
        assertThat(r.getFarrowRate()).isEqualByComparingTo("81.82");
    }

    @Test
    @DisplayName("listOverdueUndecided: 超期未定性清单字段映射")
    void testListOverdueUndecidedMapsRows() {
        when(aggregateQueryMapper.selectOverdueUndecided(anyString(), anyInt(), any()))
            .thenReturn(List.of(mapOfAll(
                "breedingId", 9323000000000067L, "earNo", "02-02-2-241112-002",
                "breedingDate", java.sql.Date.valueOf("2026-04-07"),
                "deadline", java.sql.Date.valueOf("2026-08-04"),
                "overdueDays", 41, "parity", 3,
                "barnName", "1号舍", "penName", "A3", "currentStatus", "PZ")));

        List<org.dromara.djs.breed.dashboard.domain.vo.OverdueUndecidedVo> rows = service.listOverdueUndecided();

        assertThat(rows).hasSize(1);
        var r = rows.get(0);
        assertThat(r.getBreedingId()).isEqualTo(9323000000000067L);
        assertThat(r.getEarNo()).isEqualTo("02-02-2-241112-002");
        assertThat(r.getBreedingDate()).isEqualTo(LocalDate.of(2026, 4, 7));
        assertThat(r.getDeadline()).isEqualTo(LocalDate.of(2026, 8, 4));
        assertThat(r.getOverdueDays()).isEqualTo(41);
        assertThat(r.getCurrentStatus()).isEqualTo("PZ");
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    /** 交替 key/value 建 Map（cohort 桶多，两两重载不够用）。 */
    private static Map<String, Object> mapOfAll(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** triggerAggregate 跑通所需的最小 stub 集（各 upsert 的通用依赖）。 */
    private void stubAggregateSkeleton() {
        when(aggregateQueryMapper.countByLifecycle(anyString(), any())).thenReturn(new ArrayList<>());
        when(aggregateQueryMapper.countStatusEventInRange(anyString(), any(), any(), any())).thenReturn(0);
        when(aggregateQueryMapper.sumLiveBornInRange(anyString(), any(), any())).thenReturn(0);
        when(aggregateQueryMapper.selectAliveSows(anyString())).thenReturn(new ArrayList<>());
        when(aggregateQueryMapper.snapshotByTypeStatusOnDate(anyString(), any())).thenReturn(new ArrayList<>());
        when(sowRecordMapper.selectOne(any())).thenReturn(null);
        when(monthlyProductionMapper.selectOne(any())).thenReturn(null);
        when(annualIndicatorMapper.selectOne(any())).thenReturn(null);
        when(farmIndicatorRecordMapper.selectOne(any())).thenReturn(null);
    }

    private MonthlyProduction newMonth(YearMonth ym) {
        MonthlyProduction m = new MonthlyProduction();
        m.setStatMonth(ym.toString());
        m.setIntroduceCount(0);
        m.setBornCount(0);
        m.setWeanedCount(0);
        m.setDeathCount(0);
        m.setCullingCount(0);
        m.setMarketingCount(0);
        m.setMarketingWeight(BigDecimal.ZERO);
        return m;
    }

    /**
     * Mock monthlyProductionMapper.selectOne：service 调用顺序固定为
     * 1) selectMonth(curr=ymA)  2) selectMonth(prev=ymB)，按顺序 stub。
     * （比 wrapper.paramNameValuePairs 检查更稳；MP 内部命名易变。）
     */
    private void stubMonthlySelectByMonth(YearMonth ymA, MonthlyProduction retA, YearMonth ymB, MonthlyProduction retB) {
        when(monthlyProductionMapper.selectOne(any()))
            .thenReturn(retA)
            .thenReturn(retB);
    }
}
