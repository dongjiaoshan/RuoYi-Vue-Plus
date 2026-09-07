package org.dromara.djs.warehouse.stat.service.impl;

import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.loss.service.IProductionLossService;
import org.dromara.djs.warehouse.stat.domain.WarehouseIndicatorRecord;
import org.dromara.djs.warehouse.stat.domain.WarehouseMonthlyRecord;
import org.dromara.djs.warehouse.stat.mapper.WarehouseCroppRecordMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseIndicatorRecordMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseMonthlyRecordMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseStatAggregateMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WarehouseStatServiceImpl} 单测（V6-R172 仓库日指标三 cohort 口径）。
 *
 * <ol>
 *   <li>屠宰率只算称重 cohort 里「有出栏重量」的子集；接收重量仍算整批</li>
 *   <li>白条出品率 = 同一批处理完成猪的 Σ白条重 ÷ Σ出栏重量（不是接收重量、不是送宰总重）；
 *       白条均重的分母是处理完成头数</li>
 *   <li>各 cohort 为空 / 分母 0 → 比率落 null（不造假）</li>
 *   <li>月表比率从日表落下的 cohort 基数列 Σ 后重算</li>
 * </ol>
 *
 * @author djs
 * @since V6-R172
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WarehouseStatServiceImpl 单元测试（V6-R172 三 cohort 口径）")
class WarehouseStatServiceImplTest {

    private static final String TENANT = "1001";
    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);
    private static final String DATE_STR = "2026-09-01";
    private static final String MONTH = "2026-09";

    @Mock
    private WarehouseStatAggregateMapper aggregateMapper;
    @Mock
    private WarehouseIndicatorRecordMapper indicatorMapper;
    @Mock
    private WarehouseCroppRecordMapper croppMapper;
    @Mock
    private WarehouseMonthlyRecordMapper monthlyMapper;
    @Mock
    private IProductionLossService productionLossService;

    @InjectMocks
    private WarehouseStatServiceImpl service;

    /** TenantHelper 是静态工具且依赖 Spring 上下文，单测里固定成 V1 租户 '1001'。 */
    private MockedStatic<TenantHelper> tenantHelper;

    @BeforeEach
    void setUp() {
        tenantHelper = Mockito.mockStatic(TenantHelper.class);
        tenantHelper.when(TenantHelper::getTenantId).thenReturn(TENANT);
        // 非本次关注的聚合项统一给 0/空，聚焦猪肉段三 cohort
        when(aggregateMapper.sumMarketingWeight(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(aggregateMapper.sumOutsourceWeight(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(aggregateMapper.countCutBar(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(aggregateMapper.sumCutBarWeight(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(aggregateMapper.sumCutProductWeight(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(aggregateMapper.sumLossByType(anyString(), anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(aggregateMapper.sumVegLossByType(anyString(), anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(aggregateMapper.sumVegWeighWeight(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(aggregateMapper.sumSendPlatformWeight(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(aggregateMapper.sumReceivePlatformWeight(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(aggregateMapper.sumVegProdPackUsage(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(aggregateMapper.selectVegProdFlow(anyString(), anyString())).thenReturn(Map.of());
        when(aggregateMapper.selectActiveCropIds(anyString(), anyString())).thenReturn(List.of());
        when(aggregateMapper.sumMonthlyFromDaily(anyString(), anyString())).thenReturn(Map.of());
        when(indicatorMapper.selectOne(any())).thenReturn(null);
        when(monthlyMapper.selectOne(any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        tenantHelper.close();
    }

    /**
     * 称重 cohort 3 头（接收重 90+95+120=305），其中只有 2 头有出栏重量（90+120=210 / 100+130=230）。
     * 屠宰率只能用那 2 头：210/230×100 = 91.304%；接收重量仍是 3 头的 305。
     */
    @Test
    @DisplayName("屠宰率只算称重 cohort 里有出栏重量的子集，接收重量仍算整批")
    void testSlaughterRateUsesOnlySubsetWithMarketingWeight() {
        stubEmptyFinishedCohort(
            /* slaughterCount */ 2,
            /* arrive */ bd("305"),
            /* rateArrive */ bd("210"), /* rateBase */ bd("230"));

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        assertThat(saved.getArriveWeight()).isEqualByComparingTo("305.000");
        assertThat(saved.getSlaughterRateArriveWeight()).isEqualByComparingTo("210.000");
        assertThat(saved.getSlaughterRateBaseWeight()).isEqualByComparingTo("230.000");
        assertThat(saved.getSlaughterRate()).isEqualByComparingTo("91.304");
        // 屠宰头数来自出栏 cohort，与称重 cohort 的 3 头无关
        assertThat(saved.getSlaughterCount()).isEqualTo(2);
    }

    /**
     * 处理完成 cohort 2 头：白条总重 190、这批猪的出栏重量之和 250；接收重量之和 210、送宰总重 999
     * （两个都故意跟分母拉开）。白条出品率必须是 190/250×100 = 76.000 —— 不是 190/210（接收重量，旧分母）、
     * 不是 190/999（送宰总重，更旧的分母）。白条均重 190/2 = 95（不是 ÷屠宰头数 5）。
     */
    @Test
    @DisplayName("白条出品率分母是同一批处理完成猪的出栏重量之和，白条均重分母是处理完成头数")
    void testBarYieldRateAndAvgBarWeightUseFinishedCohort() {
        when(aggregateMapper.sumMarketingWeight(TENANT, DATE_STR)).thenReturn(bd("999"));
        stubCohorts(
            /* slaughterCount */ 5,
            /* arrive */ bd("300"),
            /* rateArrive */ bd("300"), /* rateBase */ bd("400"),
            /* finishedCount */ 2, /* barTotal */ bd("190"), /* finishedArrive */ bd("210"),
            /* barYieldNumer */ bd("190"), /* barYieldBase */ bd("250"));

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        assertThat(saved.getSlaughterWeight()).isEqualByComparingTo("999.000");
        assertThat(saved.getBarTotalWeight()).isEqualByComparingTo("190.000");
        assertThat(saved.getFinishedCount()).isEqualTo(2);
        // 接收重量之和仍落盘（诊断列），但不再是分母
        assertThat(saved.getFinishedArriveWeight()).isEqualByComparingTo("210.000");
        assertThat(saved.getBarYieldBaseWeight()).isEqualByComparingTo("250.000");
        assertThat(saved.getAvgBarWeight()).isEqualByComparingTo("95.000");
        assertThat(saved.getBarYieldRate()).isEqualByComparingTo("76.000");
    }

    /** 三组 cohort 全空：所有比率 / 均值落 null，重量落 0（分母 ≤0 不造假，ALWAYS 策略覆盖旧值）。 */
    @Test
    @DisplayName("cohort 为空 / 分母 0 → 屠宰率、白条出品率、白条均重全落 null")
    void testEmptyCohortsYieldNullRates() {
        stubEmptyFinishedCohort(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        assertThat(saved.getSlaughterRate()).isNull();
        assertThat(saved.getBarYieldRate()).isNull();
        assertThat(saved.getAvgBarWeight()).isNull();
        assertThat(saved.getAvgSlaughterWeight()).isNull();
        assertThat(saved.getArriveWeight()).isEqualByComparingTo("0.000");
        assertThat(saved.getBarTotalWeight()).isEqualByComparingTo("0.000");
        assertThat(saved.getFinishedCount()).isZero();
    }

    /**
     * 白条总重非 0 但处理完成头数 / 出栏重量之和为 0（整批都没录出栏重量）→ 比率仍落 null，不除零、不造假。
     */
    @Test
    @DisplayName("白条总重非 0 但处理完成 cohort 分母为 0 → 比率仍 null")
    void testZeroFinishedDenominatorStillNull() {
        stubCohorts(3, bd("100"), bd("100"), bd("120"),
            /* finishedCount */ 0, /* barTotal */ bd("88"), /* finishedArrive */ BigDecimal.ZERO,
            /* barYieldNumer */ BigDecimal.ZERO, /* barYieldBase */ BigDecimal.ZERO);

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        assertThat(saved.getBarTotalWeight()).isEqualByComparingTo("88.000");
        assertThat(saved.getAvgBarWeight()).isNull();
        assertThat(saved.getBarYieldRate()).isNull();
    }

    /**
     * 对称剔除：处理完成 cohort 里有一头拿不到出栏重量的猪（自养漏录 marketing_weight / 外购查不到台账）。
     * 白条总重含该头（不能漏），但出品率的分子分母同时把它剔掉 → 率 ≤100%。
     * 若只剔分母不剔分子（430/450），率会被这头的白条重顶高；若只剔分子不剔分母，率会被压低。
     */
    @Test
    @DisplayName("完成 cohort 含无出栏重量的猪 → 白条总重含它、出品率两边同时剔它，率 ≤100%")
    void testBarYieldRateExcludesPigWithoutMarketingWeightOnBothSides() {
        // 3 头处理完成：A(出栏230,白条200) B(出栏220,白条190) C(出栏 NULL,白条40)
        // barTotal = 200+190+40 = 430（全含）；出品率分子 = 200+190 = 390、分母 = 230+220 = 450（C 两边都剔）
        stubCohorts(
            /* slaughterCount */ 3,
            /* arrive */ bd("410"),
            /* rateArrive */ bd("410"), /* rateBase */ bd("450"),
            /* finishedCount */ 3, /* barTotal */ bd("430"), /* finishedArrive */ bd("410"),
            /* barYieldNumer */ bd("390"), /* barYieldBase */ bd("450"));

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        // 白条总重含那头，一头都不漏
        assertThat(saved.getBarTotalWeight()).isEqualByComparingTo("430.000");
        assertThat(saved.getFinishedCount()).isEqualTo(3);
        assertThat(saved.getBarYieldNumerWeight()).isEqualByComparingTo("390.000");
        assertThat(saved.getBarYieldBaseWeight()).isEqualByComparingTo("450.000");
        // 出品率 = 390/450×100 = 86.667；白条重 < 出栏活重 恒成立 → 天然 ≤100%
        assertThat(saved.getBarYieldRate()).isEqualByComparingTo("86.667");
        assertThat(saved.getBarYieldRate()).isLessThanOrEqualTo(bd("100"));
        // 白条均重仍按全 cohort：430/3
        assertThat(saved.getAvgBarWeight()).isEqualByComparingTo("143.333");
    }

    /**
     * 月表：屠宰率 = Σ屠宰率分子/Σ屠宰率分母，白条出品率 = Σ出品率分子/Σ出品率分母。
     * 用与「Σ接收/Σ送宰」明显不同的数字，保证走的是 cohort 基数列而不是旧的两列；
     * 出品率分母取 sumBarYieldBase（不是 sumFinishedArrive），验月表侧与日率同口径。
     */
    @Test
    @DisplayName("月表比率从日表 cohort 基数列 Σ 后重算（出品率分母取 barYieldBase）")
    void testMonthlyRatesFromCohortBases() {
        stubEmptyFinishedCohort(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(aggregateMapper.sumMonthlyFromDaily(TENANT, MONTH)).thenReturn(Map.of(
            "slaughterCount", 7,
            "sumRateArrive", bd("420"),
            "sumRateBase", bd("500"),
            "sumBarYieldNumer", bd("380"),
            "sumBarYieldBase", bd("400"),
            "sumCutProduct", bd("150"),
            "sumCutBar", bd("200")));

        service.aggregate(DATE);

        ArgumentCaptor<WarehouseMonthlyRecord> captor = ArgumentCaptor.forClass(WarehouseMonthlyRecord.class);
        verify(monthlyMapper).insert(captor.capture());
        WarehouseMonthlyRecord m = captor.getValue();

        assertThat(m.getStatMonth()).isEqualTo(MONTH);
        assertThat(m.getSlaughterCount()).isEqualTo(7);
        assertThat(m.getSlaughterRate()).isEqualByComparingTo("84.000");
        assertThat(m.getBarYieldRate()).isEqualByComparingTo("95.000");
        assertThat(m.getCutYieldRate()).isEqualByComparingTo("75.000");
    }

    /** 月表分母为 0 → 比率 null（配 ALWAYS 更新策略真覆盖旧值）。 */
    @Test
    @DisplayName("月表 cohort 基数全 0 → 比率 null")
    void testMonthlyZeroBasesYieldNull() {
        stubEmptyFinishedCohort(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(aggregateMapper.sumMonthlyFromDaily(TENANT, MONTH)).thenReturn(Map.of(
            "slaughterCount", 0,
            "sumRateArrive", BigDecimal.ZERO,
            "sumRateBase", BigDecimal.ZERO,
            "sumBarYieldNumer", bd("380"),
            "sumBarYieldBase", BigDecimal.ZERO,
            "sumCutProduct", BigDecimal.ZERO,
            "sumCutBar", BigDecimal.ZERO));

        service.aggregate(DATE);

        ArgumentCaptor<WarehouseMonthlyRecord> captor = ArgumentCaptor.forClass(WarehouseMonthlyRecord.class);
        verify(monthlyMapper).insert(captor.capture());
        WarehouseMonthlyRecord m = captor.getValue();

        assertThat(m.getSlaughterRate()).isNull();
        assertThat(m.getBarYieldRate()).isNull();
        assertThat(m.getCutYieldRate()).isNull();
    }

    // ============================================================
    //  helpers
    // ============================================================

    /** 当日没有猪处理完成（F 为空）时的桩：只关心送宰 / 称重两组 cohort。 */
    private void stubEmptyFinishedCohort(int slaughterCount, BigDecimal arrive,
                                         BigDecimal rateArrive, BigDecimal rateBase) {
        stubCohorts(slaughterCount, arrive, rateArrive, rateBase,
            0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** 三组 cohort 全量桩；出品率分子/分母显式给，不由别的量推导（口径就是它俩独立落盘）。 */
    private void stubCohorts(int slaughterCount, BigDecimal arrive,
                             BigDecimal rateArrive, BigDecimal rateBase,
                             int finishedCount, BigDecimal barTotal, BigDecimal finishedArrive,
                             BigDecimal barYieldNumer, BigDecimal barYieldBase) {
        when(aggregateMapper.countSlaughter(TENANT, DATE_STR)).thenReturn(slaughterCount);
        when(aggregateMapper.sumArriveWeight(TENANT, DATE_STR)).thenReturn(arrive);
        when(aggregateMapper.selectSlaughterRateBase(TENANT, DATE_STR)).thenReturn(Map.of(
            "rateArrive", rateArrive, "rateBase", rateBase));
        when(aggregateMapper.selectFinishedAgg(TENANT, DATE_STR)).thenReturn(Map.of(
            "finishedCount", finishedCount, "barTotalWeight", barTotal,
            "finishedArriveWeight", finishedArrive,
            "barYieldNumerWeight", barYieldNumer, "barYieldBaseWeight", barYieldBase));
    }

    private WarehouseIndicatorRecord runAggregateAndCaptureDaily() {
        service.aggregate(DATE);
        ArgumentCaptor<WarehouseIndicatorRecord> captor = ArgumentCaptor.forClass(WarehouseIndicatorRecord.class);
        verify(indicatorMapper).insert(captor.capture());
        return captor.getValue();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
