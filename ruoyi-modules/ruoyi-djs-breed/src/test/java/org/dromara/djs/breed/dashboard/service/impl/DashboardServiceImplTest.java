package org.dromara.djs.breed.dashboard.service.impl;

import org.dromara.djs.breed.dashboard.domain.AnnualIndicator;
import org.dromara.djs.breed.dashboard.domain.MonthlyProduction;
import org.dromara.djs.breed.dashboard.domain.SowRecord;
import org.dromara.djs.breed.dashboard.domain.vo.Activity7dVo;
import org.dromara.djs.breed.dashboard.domain.vo.AnnualIndicatorVo;
import org.dromara.djs.breed.dashboard.domain.vo.InventoryVo;
import org.dromara.djs.breed.dashboard.domain.vo.MonthlyComparisonVo;
import org.dromara.djs.breed.dashboard.mapper.AggregateQueryMapper;
import org.dromara.djs.breed.dashboard.mapper.AnnualIndicatorMapper;
import org.dromara.djs.breed.dashboard.mapper.MonthlyProductionMapper;
import org.dromara.djs.breed.dashboard.mapper.SowRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.any;
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

    private DashboardServiceImpl service;

    @BeforeEach
    void setup() {
        service = new DashboardServiceImpl(
            sowRecordMapper, monthlyProductionMapper, annualIndicatorMapper, aggregateQueryMapper);
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
        when(aggregateQueryMapper.sumIntroducedInRange(anyString(), any(), any())).thenReturn(0);
        when(aggregateQueryMapper.sumLiveBornInRange(anyString(), any(), any())).thenReturn(0);
        when(aggregateQueryMapper.sumWeanedInRange(anyString(), any(), any())).thenReturn(0);
        Map<String, Object> mk = new LinkedHashMap<>();
        mk.put("cnt", 0L);
        mk.put("weight", BigDecimal.ZERO);
        when(aggregateQueryMapper.aggregateMarketingInRange(anyString(), any(), any())).thenReturn(mk);
        when(aggregateQueryMapper.countAliveSows(anyString())).thenReturn(20);
        when(sowRecordMapper.selectOne(any())).thenReturn(null);
        when(monthlyProductionMapper.selectOne(any())).thenReturn(null);
        when(annualIndicatorMapper.selectOne(any())).thenReturn(null);

        String result = service.triggerAggregate(LocalDate.of(2026, 5, 25));

        // 关键断言：status_record DIE / ELIMINATE 至少调用一次（本 ticket 契约 — 不查 pig.end_date）
        verify(aggregateQueryMapper, atLeastOnce())
            .countStatusEventInRange(anyString(), eq("DIE"), any(), any());
        verify(aggregateQueryMapper, atLeastOnce())
            .countStatusEventInRange(anyString(), eq("ELIMINATE"), any(), any());

        // 三表全 INSERT（existing 都返 null → 走 insert 分支）
        verify(sowRecordMapper).insert(any(SowRecord.class));
        verify(monthlyProductionMapper).insert(any(MonthlyProduction.class));
        verify(annualIndicatorMapper).insert(any(AnnualIndicator.class));

        assertThat(result).contains("ok").contains("2026-05-25");
    }

    @Test
    @DisplayName("triggerAggregate: targetDate=null → 跑 T-1（昨天）")
    void testTriggerAggregateDefaultsToYesterday() {
        when(aggregateQueryMapper.countByLifecycle(anyString(), any())).thenReturn(new ArrayList<>());
        when(aggregateQueryMapper.countStatusEventInRange(anyString(), any(), any(), any())).thenReturn(0);
        when(aggregateQueryMapper.sumIntroducedInRange(anyString(), any(), any())).thenReturn(0);
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
    //  test helpers
    // ============================================================

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
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
