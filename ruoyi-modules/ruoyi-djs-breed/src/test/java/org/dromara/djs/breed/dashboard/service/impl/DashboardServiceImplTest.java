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
import org.dromara.djs.breed.dashboard.domain.vo.MonthlyProductionStatVo;
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
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
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
 *   <li>getAnnualIndicator：psy（decimal(8,2)，头/母猪·年）+ mortalityRate（4 位小数）</li>
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
    private org.dromara.djs.breed.dashboard.mapper.FarrowingRateMapper farrowingRateMapper;
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
            farmIndicatorRecordMapper, farrowingRateMapper, sowPerformanceMapper,
            productionCycleConfigService, fattenAgeStageService);
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
    @DisplayName("getAnnualIndicator: psy decimal(8,2) / mortalityRate 4 位小数 + null → 0")
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
    @DisplayName("getBreedingAnnual: 分娩率实时取台账（row231），其余仍取年表（率类 ×100 / 总产仔非活仔）")
    void testGetBreedingAnnual() {
        AnnualIndicator ai = new AnnualIndicator();
        ai.setStatYear((short) 2026);
        // 年表这列故意留成另一个值：分娩率若还在读它，55.560 会取代台账算出的 85.00
        ai.setYearFarrowRate(new BigDecimal("55.560"));
        ai.setWeanBreedInterval(new BigDecimal("35.000"));
        ai.setAvgNpdDays(new BigDecimal("94.500"));
        ai.setTotalBornCount(31);
        ai.setTotalLiveBorn(21);
        ai.setAvgLiveBornPerLitter(new BigDecimal("4.200"));
        ai.setAvgWeanedPerLitter(new BigDecimal("10.333"));
        ai.setFarrowLossRate(new BigDecimal("9.520"));
        when(annualIndicatorMapper.selectOne(any())).thenReturn(ai);
        // 台账全年：到期 40 / 按期分娩 34 → 85.00%
        when(farrowingRateMapper.selectFarrowRate(anyString(),
            eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2027, 1, 1)), any()))
            .thenReturn(mapOfAll("denom", 40, "numer", 34, "farrowLate", 0));

        BreedingAnnualVo vo = service.getBreedingAnnual(2026);

        // 配种率 V1 口径同分娩率，两格都取实时台账值（已 ×100，前端直接拼 %）
        assertThat(vo.getMateRate()).isEqualByComparingTo("85.00");
        assertThat(vo.getFarrowRate()).isEqualByComparingTo("85.00");
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
        // 期末快照：sow PZ=3(在怀) / sow FM=5(哺乳，生产但不在怀) / sow HB=2(后备) / sow KH=1(生产且非生产)
        //          / boar=4 / fattening=30 / piglet=20
        // FM 这一行是给 pregnant_sow_count 当对照的：它是「生产母猪」但不是「在怀」，
        // 少了它，PZ 桶把 FM 漏收进去也不会被任何断言发现。
        when(aggregateQueryMapper.snapshotByTypeStatusOnDate(anyString(), any())).thenReturn(List.of(
            snap("sow", "PZ", 3),
            snap("sow", "FM", 5),
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
        // 生产母猪 = 非后备非终止非空 = PZ(3) + FM(5) + KH(1) = 9
        assertThat(r.getEndProductionSowCount()).isEqualTo(9);
        // 后备 = HB = 2；非生产 = KH = 1
        assertThat(r.getEndReserveCount()).isEqualTo(2);
        assertThat(r.getEndNonprodSowCount()).isEqualTo(1);
        // 公/肥/仔
        assertThat(r.getEndBoarCount()).isEqualTo(4);
        assertThat(r.getEndFatteningCount()).isEqualTo(30);
        assertThat(r.getEndPigletCount()).isEqualTo(20);
        assertThat(r.getEndReserve230Count()).isEqualTo(1);
        // 在怀母猪 = 快照里 PZ 的头数（3），不按判定节点截断（D-0082）。
        // 它必须只收 PZ：FM(5) / HB(2) / KH(1) 任何一桶漏进来，这条都会当场红
        //（三个对照桶的头数各不相同，也各不等于 3，任一泄漏都算得出不同的值）。
        assertThat(r.getPregnantSowCount()).isEqualTo(3);
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

        // 判定节点现在流向台账刷新的第一步（预估分娩日 = 配种日 + judgeDays），不再经 selectCohortOutcome
        verify(farrowingRateMapper, atLeastOnce())
            .refreshStep1Breeding(anyString(), any(), any(), eq(119));
        verify(farrowingRateMapper, never())
            .refreshStep1Breeding(anyString(), any(), any(), eq(0));
    }

    @Test
    @DisplayName("cohort: 判定节点取配置值（非 0 时不回退）")
    void testFarrowJudgeDeadlineUsesConfiguredValue() {
        stubAggregateSkeleton();
        when(productionCycleConfigService.getValue("sow_farrow_judge_deadline_days")).thenReturn(117);

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        verify(farrowingRateMapper, atLeastOnce())
            .refreshStep1Breeding(anyString(), any(), any(), eq(117));
    }

    @Test
    @DisplayName("年度: 分娩率直取 t_farm_farrowing_rate 整年（V6 row231），Σ月表退为对账参考")
    void testAnnualFarrowRateFromLedger() {
        stubAggregateSkeleton();
        // 台账全年：到期 40 / 按期分娩 34 → 34/40 = 85.00%
        when(farrowingRateMapper.selectFarrowRate(anyString(),
            eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2027, 1, 1)), any()))
            .thenReturn(mapOfAll("denom", 40, "numer", 34, "farrowLate", 0));
        // Σ月表故意给一组完全不同的数：它若还被当分子分母，51/81 = 62.96% 会立刻露馅
        when(aggregateQueryMapper.sumMonthlyProductionRange(anyString(), anyString(), anyString()))
            .thenReturn(mapOfAll("mateLitterCount", 81, "cohortFarrowCount", 51, "rowCnt", 9));
        when(aggregateQueryMapper.countBreedingInRange(anyString(), any(), any())).thenReturn(199);

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        AnnualIndicator a = cap.getValue();
        assertThat(a.getYearFarrowRate()).isEqualByComparingTo("85.00");
        // 三列同取台账那一次查询，永远自洽
        assertThat(a.getYearBatchFarrowCount()).isEqualTo(34);
        assertThat(a.getCohortMaturedCount()).isEqualTo(40);
        assertThat(a.getBreedingCount()).isEqualTo(199);
    }

    @Test
    @DisplayName("年度: 月表整段缺行不再影响年分娩率 —— 直取台账的收益（旧 Σ月表口径此处会算成 0）")
    void testAnnualFarrowRateImmuneToMissingMonthlyRows() {
        stubAggregateSkeleton();
        // 一行月表都没有（滚动窗只刷最近几个月，1-6 月长期无行的真实场景）
        when(aggregateQueryMapper.sumMonthlyProductionRange(anyString(), anyString(), anyString()))
            .thenReturn(mapOfAll("mateLitterCount", 0, "cohortFarrowCount", 0, "rowCnt", 0));
        // 台账照常有数：到期 40 / 按期分娩 34
        when(farrowingRateMapper.selectFarrowRate(anyString(),
            eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2027, 1, 1)), any()))
            .thenReturn(mapOfAll("denom", 40, "numer", 34, "farrowLate", 0));

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        AnnualIndicator a = cap.getValue();
        // 旧口径（Σ月表）在这里会得 0 —— 正是 D-0087 时代要靠告警兜的那个静默少报
        assertThat(a.getYearFarrowRate()).isEqualByComparingTo("85.00");
        assertThat(a.getYearBatchFarrowCount()).isEqualTo(34);
        assertThat(a.getCohortMaturedCount()).isEqualTo(40);
    }

    @Test
    @DisplayName("年度: PSY =（Σ日妊娠母猪头数/母猪头日）×365/115×窝均断奶数（V6 row228），区间锚日表首行")
    void testAnnualPsyFromGestationDays() {
        stubAggregateSkeleton();
        // 窝均断奶数 = 总断奶仔猪 188 / 总断奶母猪 16 = 11.750
        when(aggregateQueryMapper.sumIndicatorRange(anyString(), any(), any()))
            .thenReturn(mapOfAll("sumWeanedPiglet", 188, "sumWeaningSow", 16));
        when(aggregateQueryMapper.selectSowDaysInRange(anyString(), any(), any()))
            .thenReturn(mapOfAll("pregDays", 5016, "sowDays", 5430, "dayRows", 37,
                "firstDay", LocalDate.of(2026, 8, 8)));

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        AnnualIndicator a = cap.getValue();
        // 5016/5430 = 0.923757 → ×365 ÷115 = 2.931924 → ×11.750 = 34.450
        assertThat(a.getPsy()).isEqualByComparingTo("34.450");
        assertThat(a.getPsyStatFrom()).isEqualTo(LocalDate.of(2026, 8, 8));
        assertThat(a.getPsyStatDays()).isEqualTo(37);
    }

    @Test
    @DisplayName("年度: 母猪头日为 0 则 PSY=0 不炸，区间起点回落年初")
    void testAnnualPsyZeroWhenNoSowDays() {
        stubAggregateSkeleton();
        when(aggregateQueryMapper.selectSowDaysInRange(anyString(), any(), any()))
            .thenReturn(mapOfAll("pregDays", 0, "sowDays", 0, "dayRows", 0, "firstDay", null));

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        AnnualIndicator a = cap.getValue();
        assertThat(a.getPsy()).isEqualByComparingTo("0");
        assertThat(a.getPsyStatFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("日表: 日分娩猪只妊娠天数按「当日 [00:00, 次日 00:00)」取并落 farrow_gestation_days（V6 row227）")
    void testDailyFarrowGestationDaysPersisted() {
        stubAggregateSkeleton();
        // judgeDays 必须来自配置（D-0088「上界复用 sow_farrow_judge_deadline_days，不另造阈值」）：
        // 这里给配置一个非缺省值 117，下面钉死实参 —— 写死 119/114 都会让这条红。
        when(productionCycleConfigService.getValue("sow_farrow_judge_deadline_days")).thenReturn(117);
        when(aggregateQueryMapper.sumFarrowGestationDaysForDay(anyString(), any(), any(), anyInt())).thenReturn(342);

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<FarmIndicatorRecord> cap = ArgumentCaptor.forClass(FarmIndicatorRecord.class);
        verify(farmIndicatorRecordMapper, atLeastOnce()).insert(cap.capture());
        assertThat(cap.getAllValues())
            .extracting(FarmIndicatorRecord::getFarrowGestationDays)
            .contains(342);
        // 区间必须是 [statDate, statDate+1) —— 传错成 [statDate, statDate) 会恒 0、传成整月会串日，
        // 两种都不会被上面的断言发现，所以这里把实参钉死。
        ArgumentCaptor<LocalDate> fromCap = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(aggregateQueryMapper, atLeastOnce())
            .sumFarrowGestationDaysForDay(anyString(), fromCap.capture(), toCap.capture(), anyInt());
        assertThat(fromCap.getAllValues()).contains(LocalDate.of(2026, 9, 13));
        assertThat(toCap.getAllValues()).contains(LocalDate.of(2026, 9, 14));
        // 上界实参钉死 = 配置值，不是写死的常量
        ArgumentCaptor<Integer> judgeCap = ArgumentCaptor.forClass(Integer.class);
        verify(aggregateQueryMapper, atLeastOnce())
            .sumFarrowGestationDaysForDay(anyString(), any(), any(), judgeCap.capture());
        assertThat(judgeCap.getAllValues()).containsOnly(117);
    }

    @Test
    @DisplayName("年度: 平均非生产天数年化（区间值 × 365/已历天数）")
    void testAnnualNpdAnnualized() {
        stubAggregateSkeleton();
        // Σ日非生产母猪 101，Σ日生产母猪 6409，已历天数 46 → 年均存栏 139.326
        // 区间 NPD = 101/139.326 = 0.725 → 年化 ×365/46 = 5.753
        // sumEndReserve230 给 37 作对照桶：甲方 row228 ① 要求「不计算 230 后备猪的数据」，
        // 一旦被加进分子，total_npd_days 会变 138、avg_npd_days 会变 7.859，两条断言同时红。
        when(aggregateQueryMapper.sumIndicatorRange(anyString(), any(), any()))
            .thenReturn(mapOfAll("sumEndProductionSow", 6409, "sumEndNonprodSow", 101,
                "sumEndReserve230", 37));
        when(aggregateQueryMapper.countIndicatorDays(anyString(), any(), any())).thenReturn(46);

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        assertThat(cap.getValue().getAvgNpdDays()).isEqualByComparingTo("5.753");
        // 全年总NPD天数 = Σ日非生产母猪，**不含** 230 后备（甲方 row228 ①）
        assertThat(cap.getValue().getTotalNpdDays()).isEqualTo(101);
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
    @DisplayName("月度: 分娩率取 t_farm_farrowing_rate 当月（V6 row230），并落 cohort_farrow_count / mate_litter_count")
    void testMonthlyFarrowRateFromLedger() {
        stubAggregateSkeleton();
        when(farrowingRateMapper.selectFarrowRate(anyString(),
            eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 10, 1)), any()))
            .thenReturn(mapOfAll("denom", 15, "numer", 14, "farrowLate", 0));

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

    // ============================================================
    //  mp 当月生产指标统计 —— 读取端（甲方 row230）
    //  这一段在 2026-09-18 对抗验收前是**零覆盖**：变异测试把当月/上月参数对调、
    //  把 T-1 收口钳位去掉，405 个单测全绿。下面两支专门钉这两处。
    // ============================================================

    /** 造一个只有分娩率有意义的月表行，其余字段不参与本段断言。 */
    private static MonthlyProduction monthRow(String farrowRate) {
        MonthlyProduction m = new MonthlyProduction();
        m.setFarrowRate(new BigDecimal(farrowRate));
        return m;
    }

    @Test
    @DisplayName("月度读取端: 分娩率取台账不取月表落盘值（V6 row230）")
    void testMonthlyStatsFarrowRateComesFromLedger() {
        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);
        // 月表故意塞显眼假值：还在读月表的话 77.77 会顶掉台账算出的数
        when(monthlyProductionMapper.selectOne(any()))
            .thenReturn(monthRow("77.770"))
            .thenReturn(monthRow("88.880"));
        when(farrowingRateMapper.selectFarrowRate(anyString(),
            eq(thisMonth.atDay(1)), eq(thisMonth.plusMonths(1).atDay(1)), any()))
            .thenReturn(mapOfAll("denom", 20, "numer", 13, "farrowLate", 0));
        when(farrowingRateMapper.selectFarrowRate(anyString(),
            eq(lastMonth.atDay(1)), eq(lastMonth.plusMonths(1).atDay(1)), any()))
            .thenReturn(mapOfAll("denom", 8, "numer", 2, "farrowLate", 0));

        MonthlyProductionStatVo vo = service.getMonthlyProductionStats(thisMonth);

        MonthlyProductionStatVo.StatRow row = vo.getRows().stream()
            .filter(r -> "分娩率".equals(r.getMetric())).findFirst().orElseThrow();
        // 13/20 = 65.00，2/8 = 25.00 —— 与月表里的 77.77 / 88.88 明显可区分
        assertThat(row.getCurrent()).isEqualByComparingTo("65.00");
        assertThat(row.getPrevious()).isEqualByComparingTo("25.00");
        // 当月/上月不能对调：对调后 current 会变成 25.00
        assertThat(row.getCurrent()).isNotEqualByComparingTo(row.getPrevious());
    }

    @Test
    @DisplayName("月度读取端: 当月窗口收口到 T-1，历史月收口到月末（D-0090 未到期不进分母）")
    void testMonthlyStatsAsOfClampedToYesterday() {
        YearMonth thisMonth = YearMonth.now();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(monthlyProductionMapper.selectOne(any())).thenReturn(monthRow("0.000"));
        when(farrowingRateMapper.selectFarrowRate(anyString(), any(), any(), any()))
            .thenReturn(mapOfAll("denom", 0, "numer", 0, "farrowLate", 0));

        service.getMonthlyProductionStats(thisMonth);

        ArgumentCaptor<LocalDate> asOf = ArgumentCaptor.forClass(LocalDate.class);
        verify(farrowingRateMapper, atLeastOnce())
            .selectFarrowRate(anyString(), any(), any(), asOf.capture());
        // 当月那次收口必须是 T-1：去掉钳位会变成月末（未来日期），把还没到期的批次算进分母
        assertThat(asOf.getAllValues())
            .as("当月收口日必须钳到昨天，不能是月末")
            .contains(yesterday);
        assertThat(asOf.getAllValues())
            .as("任何一次收口都不该晚于昨天")
            .allSatisfy(d -> assertThat(d).isBeforeOrEqualTo(yesterday));
    }

    @Test
    @DisplayName("年度读取端: 年表无该年行时，分娩率仍从台账取（不再被 ai==null 早退归零）")
    void testBreedingAnnualFarrowRateSurvivesMissingYearRow() {
        when(annualIndicatorMapper.selectOne(any())).thenReturn(null);
        when(farrowingRateMapper.selectFarrowRate(anyString(),
            eq(LocalDate.of(2025, 1, 1)), eq(LocalDate.of(2026, 1, 1)), any()))
            .thenReturn(mapOfAll("denom", 2, "numer", 1, "farrowLate", 0));

        BreedingAnnualVo vo = service.getBreedingAnnual(2025);

        // 甲方 row231 原话是「不再读取年表数据」——年表那行在不在都不该影响这一格
        assertThat(vo.getFarrowRate()).isEqualByComparingTo("50.00");
        assertThat(vo.getMateRate()).isEqualByComparingTo("50.00");
        // 其余字段没有来源，仍然归零
        assertThat(vo.getPsy()).isEqualByComparingTo("0");
        assertThat(vo.getTotalBornCount()).isEqualByComparingTo("0");
    }


    // ============================================================
    //  聚合编排接线 —— 2026-09-18 第二轮对抗验收的存活变异逐条补测
    //  这些行为此前零覆盖：改坏了 415 个测试照样全绿。
    // ============================================================

    @Test
    @DisplayName("年度: 覆盖面校验必须拿 live 底表比台账，不能用台账自比（派生值不能校验自己）")
    void testAnnualCoverageCheckUsesIndependentLiveSource() {
        stubAggregateSkeleton();
        // 台账说 40，live 底表说 46 —— 只有独立来源才能发现台账漏了 6 行
        when(farrowingRateMapper.selectFarrowRate(anyString(),
            eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2027, 1, 1)), any()))
            .thenReturn(mapOfAll("denom", 40, "numer", 34, "farrowLate", 0));
        when(aggregateQueryMapper.selectCohortOutcome(anyString(), any(), any(), anyInt()))
            .thenReturn(mapOfAll("bred", 46, "farrow", 34, "farrowLate", 0,
                "returnCount", 0, "emptyCount", 0, "abortCount", 0, "goneCount", 0, "undecided", 0));

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        // 把 liveMatured 换成 cohortMatured（台账自比）这条变异，两轮 QA 都证明能全绿逃逸 ——
        // 这里钉住「年度聚合必须真的去问一次 live 底表」。
        verify(aggregateQueryMapper, atLeastOnce())
            .selectCohortOutcome(anyString(), any(), any(), anyInt());
        // 落盘的分母取台账值（40），不是 live 值（46）：live 只用于告警，不参与计算
        ArgumentCaptor<AnnualIndicator> cap = ArgumentCaptor.forClass(AnnualIndicator.class);
        verify(annualIndicatorMapper).insert(cap.capture());
        assertThat(cap.getValue().getCohortMaturedCount()).isEqualTo(40);
    }

    @Test
    @DisplayName("刷新台账必须排在月/年 upsert 之前，且五步按甲方原文顺序")
    void testFarrowingRateRefreshOrdering() {
        stubAggregateSkeleton();

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        InOrder o = inOrder(farrowingRateMapper, monthlyProductionMapper, annualIndicatorMapper);
        // 甲方 row229 原文：配种 → 分娩 → 返空流 → 死淘
        o.verify(farrowingRateMapper).refreshStep1Breeding(anyString(), any(), any(), anyInt());
        // 1b 必须紧跟 1：一条配种被软删后又恢复且改了日期时，step1 的 ODKU 先把 del_flag 翻回 '0'，
        // step1b 的 WHERE r.del_flag='0' 才命中得到它。顺序反过来 → 行复活了但日期仍陈旧一整天。
        o.verify(farrowingRateMapper).refreshStep1bResync(anyString(), any(), any(), anyInt());
        o.verify(farrowingRateMapper).refreshStep2Farrow(anyString(), any(), any(), anyInt());
        o.verify(farrowingRateMapper).refreshStep3Abnormal(anyString(), any(), any(), anyInt());
        o.verify(farrowingRateMapper).refreshStep4Cull(anyString(), any(), any(), anyInt());
        // 源配种记录已软删 → 台账跟着软删，漏掉这一步会让撤销的配种永远占着分母
        o.verify(farrowingRateMapper).softDeleteOrphans(anyString(), any(), any(), anyInt());
        // 月/年汇总读的就是刚刷完的台账，顺序反了就是拿上一轮的快照出数
        o.verify(monthlyProductionMapper).insert(any(MonthlyProduction.class));
        o.verify(annualIndicatorMapper).insert(any(AnnualIndicator.class));
    }

    @Test
    @DisplayName("刷新窗口右开界是「最大月的下月1日」，不是最大月1日（少刷一整月）")
    void testFarrowingRateRefreshWindowRightBound() {
        stubAggregateSkeleton();

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(farrowingRateMapper).refreshStep1Breeding(anyString(), from.capture(), to.capture(), anyInt());
        assertThat(from.getValue()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(to.getValue())
            .as("右开界必须是下月1日；写成 max.atDay(1) 会让整个 9 月一行都刷不到")
            .isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("落盘端收口日也钳到 T-1（月表/年表），否则未到期批次进分母")
    void testUpsertAsOfClampedToYesterday() {
        stubAggregateSkeleton();
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // 🔴 触发日必须相对 now() 取，不能写死字面量：写死的话时钟走过那一年之后，
        //    被测窗口整个落在过去，asOf 恒等于月末、断言恒真 —— 测试不是变红而是变成永真。
        service.triggerAggregate(yesterday.minusDays(4));

        ArgumentCaptor<LocalDate> asOf = ArgumentCaptor.forClass(LocalDate.class);
        verify(farrowingRateMapper, atLeastOnce())
            .selectFarrowRate(anyString(), any(), any(), asOf.capture());
        assertThat(asOf.getAllValues())
            .as("落盘端任何一次取数的收口日都不该晚于昨天（D-0090：未到期的不进分母）")
            .allSatisfy(d -> assertThat(d).isBeforeOrEqualTo(yesterday));
    }

    @Test
    @DisplayName("年度读取端收口日同样钳到 T-1（月度那支已有，年度这支此前零覆盖）")
    void testBreedingAnnualAsOfClampedToYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(annualIndicatorMapper.selectOne(any())).thenReturn(null);
        when(farrowingRateMapper.selectFarrowRate(anyString(), any(), any(), any()))
            .thenReturn(mapOfAll("denom", 0, "numer", 0, "farrowLate", 0));

        service.getBreedingAnnual(LocalDate.now().getYear());

        ArgumentCaptor<LocalDate> asOf = ArgumentCaptor.forClass(LocalDate.class);
        verify(farrowingRateMapper).selectFarrowRate(anyString(), any(), any(), asOf.capture());
        assertThat(asOf.getValue())
            .as("当年查询收口到 T-1；去掉钳位会变成 12-31，把整年未到期批次算进分母")
            .isEqualTo(yesterday);
    }


    // ============================================================
    //  2026-09-19 第三轮对抗验收补测：这些行为改坏后 416 个用例全绿
    // ============================================================

    @Test
    @DisplayName("刷新五步的窗口实参必须逐个一致 —— 任一被写窄/写塌，对应那一列永远回填不进来")
    void testAllFiveRefreshCallsShareTheSameWindow() {
        stubAggregateSkeleton();

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        // B01：此前只捕了 step1 的 from/to，其余四次用 any()。把 step2 的 to 写成 from
        // （窗口塌成空）→ 分娩日期永远回填不进台账、分子恒 0、分娩率恒 0%，而测试全绿。
        ArgumentCaptor<LocalDate> f1 = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> t1 = ArgumentCaptor.forClass(LocalDate.class);
        verify(farrowingRateMapper).refreshStep1Breeding(anyString(), f1.capture(), t1.capture(), anyInt());
        LocalDate from = f1.getValue();
        LocalDate to = t1.getValue();
        assertThat(from).isBefore(to);

        verify(farrowingRateMapper).refreshStep1bResync(anyString(), eq(from), eq(to), anyInt());
        verify(farrowingRateMapper).refreshStep2Farrow(anyString(), eq(from), eq(to), anyInt());
        verify(farrowingRateMapper).refreshStep3Abnormal(anyString(), eq(from), eq(to), anyInt());
        verify(farrowingRateMapper).refreshStep4Cull(anyString(), eq(from), eq(to), anyInt());
        verify(farrowingRateMapper).softDeleteOrphans(anyString(), eq(from), eq(to), anyInt());
    }

    @Test
    @DisplayName("刷新与漂移探测都必须传当前租户，不得写死")
    void testRefreshUsesCurrentTenant() {
        stubAggregateSkeleton();

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        // B05/B25：所有断言都用 anyString() 时，写死任意租户号都能过。台账是跨租户共表。
        String tenant = "1001";   // DashboardServiceImpl.DEFAULT_TENANT，单测无租户上下文时的兜底值
        verify(farrowingRateMapper).refreshStep1Breeding(eq(tenant), any(), any(), anyInt());
        verify(farrowingRateMapper).refreshStep2Farrow(eq(tenant), any(), any(), anyInt());
        verify(farrowingRateMapper).countJudgeDaysDrift(eq(tenant), anyInt());
    }

    @Test
    @DisplayName("漂移探测传的是当前判定节点配置值，不是 0 或写死值")
    void testDriftProbeUsesConfiguredJudgeDays() {
        stubAggregateSkeleton();
        when(productionCycleConfigService.getValue("sow_farrow_judge_deadline_days")).thenReturn(117);

        service.triggerAggregate(LocalDate.of(2026, 9, 13));

        // B12：传错阈值 → 漂移探测永远报 0 或全表误报，而这是台账仅有的体检之一
        verify(farrowingRateMapper).countJudgeDaysDrift(anyString(), eq(117));
    }

    @Test
    @DisplayName("滚动窗跨多月时，刷新窗口必须覆盖到最后一个月（不能塌成第一个月）")
    void testRefreshWindowSpansAllMonths() {
        stubAggregateSkeleton();

        // B27：max 写成 Collections.min(months) → 夜跑的近三个月补刷静默只刷一个月。
        //      单日触发只走单月路径，结构上照不到这个 bug，必须用跨月区间。
        // 直接调 aggregateRollups：triggerAggregateRange 会走 SpringUtils.getAopProxy，单测无容器。
        // 这里要验的是「多月集合 → 刷新窗口」这一段映射，与事务代理无关。
        service.aggregateRollups("1001", LocalDate.of(2026, 9, 13),
            List.of(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9)),
            List.of((short) 2026));

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(farrowingRateMapper, atLeastOnce())
            .refreshStep1Breeding(anyString(), from.capture(), to.capture(), anyInt());
        assertThat(from.getValue()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(to.getValue())
            .as("跨 7/8/9 三个月 → 右开界必须是 10-01；塌成 8-01 会让 8、9 月整月刷不到")
            .isEqualTo(LocalDate.of(2026, 10, 1));
    }

}
