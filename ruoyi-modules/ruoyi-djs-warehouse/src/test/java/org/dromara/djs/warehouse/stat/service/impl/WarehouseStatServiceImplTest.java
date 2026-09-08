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
 * {@link WarehouseStatServiceImpl} 单测（仓库日指标猪肉段取数口径）。
 *
 * <ol>
 *   <li>屠宰率只算称重 cohort 里「有出栏重量」的子集；接收重量仍算整批</li>
 *   <li>白条总重 = 当日入白条库的白条产品入库量；白条均重的分母是当日入白条库的猪只耳号去重数；
 *       白条出品率 = 同一个白条总重 ÷ 屠宰率那个分母（一个白条总重、三处共用）</li>
 *   <li>处理完成头数 / 其接收重量之和只落盘，不参与任何比率或均值</li>
 *   <li>各 cohort 为空 / 分母 0 → 比率落 null（不造假）</li>
 *   <li>月表比率从日表落下的 cohort 基数列 Σ 后重算</li>
 * </ol>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WarehouseStatServiceImpl 单元测试（猪肉段取数口径）")
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
     * 甲方 2026-09-08 口径：白条总重 = 当日入白条库的白条产品入库量（190，来自 4 行流水 / 2 头猪），
     * 白条均重 = 190 / 2 头 = 95.000（分母是<b>猪只耳号去重数</b>，不是流水行数 4、不是处理完成头数 3、
     * 也不是屠宰头数 5）。出品率分子必须是同一个 190，分母 400 与屠宰率共用。
     *
     * <p>处理完成 cohort 那两个量（3 头 / 接收重量 210）与送宰总重 999 都故意与上面拉开，
     * 谁把它们接回分子分母，这里当场红。</p>
     */
    @Test
    @DisplayName("白条总重 = 当日入白条库量；均重分母 = 猪只耳号去重数；出品率分子复用同一个白条总重")
    void testWhiteBarMetricsUseInboundCohort() {
        when(aggregateMapper.sumMarketingWeight(TENANT, DATE_STR)).thenReturn(bd("999"));
        stubCohorts(
            /* slaughterCount */ 5,
            /* arrive */ bd("300"),
            /* rateArrive */ bd("300"), /* rateBase */ bd("400"),
            /* barTotal */ bd("190"), /* barPigCount */ 2,
            /* finishedCount */ 3, /* finishedArrive */ bd("210"));

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        assertThat(saved.getSlaughterWeight()).isEqualByComparingTo("999.000");
        assertThat(saved.getBarTotalWeight()).isEqualByComparingTo("190.000");
        // 190 / 2 头猪 = 95.000（÷3 处理完成头数是 63.333、÷5 屠宰头数是 38，都不对）
        assertThat(saved.getAvgBarWeight()).isEqualByComparingTo("95.000");
        // 分母也要落盘：矩阵「累计」格按 Σ白条总重 ÷ Σ去重耳号数 重算，不落就只能拿日均重再平均
        assertThat(saved.getBarPigCount()).isEqualTo(2);
        // 分子 = 同一个白条总重（一处定义），分母 = 称重 cohort 的 Σ出栏重量，与屠宰率同一个
        assertThat(saved.getBarYieldNumerWeight()).isEqualByComparingTo("190.000");
        assertThat(saved.getBarYieldBaseWeight()).isEqualByComparingTo("400.000");
        assertThat(saved.getSlaughterRateBaseWeight()).isEqualByComparingTo("400.000");
        assertThat(saved.getBarYieldRate()).isEqualByComparingTo("47.500");
        // 处理完成 cohort 两列仍落盘，但不参与上面任何一个数
        assertThat(saved.getFinishedCount()).isEqualTo(3);
        assertThat(saved.getFinishedArriveWeight()).isEqualByComparingTo("210.000");
    }

    /**
     * 一头猪出两扇 = 两行入库流水，均重分母只能算 1 头。这条钉住「耳号去重」这个词：
     * 分母若误取流水行数，110/2 = 55.000 会当场红。
     */
    @Test
    @DisplayName("同一头猪的两扇白条只算 1 头：均重 = 110 / 1，不是 110 / 2 行")
    void testAvgBarWeightDedupesHalvesOfSamePig() {
        stubCohorts(1, bd("130"), bd("130"), bd("130"),
            /* barTotal */ bd("110"), /* barPigCount */ 1,
            /* finishedCount */ 1, /* finishedArrive */ bd("130"));

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        assertThat(saved.getBarTotalWeight()).isEqualByComparingTo("110.000");
        assertThat(saved.getAvgBarWeight()).isEqualByComparingTo("110.000");
        // 110/130×100 = 84.615
        assertThat(saved.getBarYieldRate()).isEqualByComparingTo("84.615");
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
     * 白条出品率的分子 = 白条总重，<b>只有一处定义</b>：谁给出品率单独留一套旧口径的白条总重
     * （如退回 Σ bar.in_weight），这条就红。刻意让展示列与分子在同一个断言里比。
     */
    @Test
    @DisplayName("白条总重一处定义三处共用：展示列 / 均重分子 / 出品率分子必须是同一个数")
    void testBarTotalWeightHasSingleDefinition() {
        stubCohorts(2, bd("200"), bd("200"), bd("250"),
            /* barTotal */ bd("123.456"), /* barPigCount */ 2,
            /* finishedCount */ 9, /* finishedArrive */ bd("888"));

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        assertThat(saved.getBarYieldNumerWeight()).isEqualByComparingTo(saved.getBarTotalWeight());
        // 均重 = 123.456 / 2 = 61.728，确认走的也是同一个分子
        assertThat(saved.getAvgBarWeight()).isEqualByComparingTo("61.728");
    }

    /**
     * 白条总重非 0 但当天一头都没完成接收（出栏重量之和为 0）→ 出品率落 null，不除零、不造假；
     * 均重有自己的分母（猪只数 2），照常算得出。
     */
    @Test
    @DisplayName("白条总重非 0 但当天没有称重 cohort（分母 0）→ 出品率仍 null，不造假")
    void testZeroFinishedDenominatorStillNull() {
        stubCohorts(3, bd("100"), BigDecimal.ZERO, BigDecimal.ZERO,
            /* barTotal */ bd("88"), /* barPigCount */ 2,
            /* finishedCount */ 2, /* finishedArrive */ BigDecimal.ZERO);

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        assertThat(saved.getBarTotalWeight()).isEqualByComparingTo("88.000");
        assertThat(saved.getAvgBarWeight()).isEqualByComparingTo("44.000");
        assertThat(saved.getBarYieldRate()).isNull();
    }

    /**
     * 甲方定的口径下，分子（白条入库 cohort）与分母（称重 cohort）**不是同一批猪**，
     * 所以出品率可以 &gt;100%：当天入白条库的白条多、完成接收的猪少时就会出现。
     *
     * <p>这个后果在写回里已明确告知甲方（「只改一半会让分子分母不是同一批猪、出品率仍会超过 100%」），
     * 甲方看到后仍指定本口径。这条用例把它钉住：谁为了「让率好看」偷偷改回同批取数，这里当场红。</p>
     */
    @Test
    @DisplayName("分子分母跨 cohort：白条入库多、完成接收少时出品率会 >100%，按甲方口径如实落盘不夹")
    void testBarYieldRateMayExceed100AcrossCohorts() {
        // 当天入白条库 3 头猪的白条（总重 430），但只有 1 头完成接收（出栏重 250）
        stubCohorts(
            /* slaughterCount */ 3,
            /* arrive */ bd("210"),
            /* rateArrive */ bd("210"), /* rateBase */ bd("250"),
            /* barTotal */ bd("430"), /* barPigCount */ 3,
            /* finishedCount */ 3, /* finishedArrive */ bd("410"));

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        assertThat(saved.getBarTotalWeight()).isEqualByComparingTo("430.000");
        assertThat(saved.getBarYieldNumerWeight()).isEqualByComparingTo("430.000");
        assertThat(saved.getBarYieldBaseWeight()).isEqualByComparingTo("250.000");
        // 430/250×100 = 172.000 —— 不夹到 100，如实反映甲方选的口径
        assertThat(saved.getBarYieldRate()).isEqualByComparingTo("172.000");
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

    /** 当日没有白条入库、也没有猪处理完成时的桩：只关心送宰 / 称重两组 cohort。 */
    private void stubEmptyFinishedCohort(int slaughterCount, BigDecimal arrive,
                                         BigDecimal rateArrive, BigDecimal rateBase) {
        stubCohorts(slaughterCount, arrive, rateArrive, rateBase,
            BigDecimal.ZERO, 0, 0, BigDecimal.ZERO);
    }

    /** 全量桩。出品率分子 = barTotal、分母 = rateBase（与屠宰率共用），不再单独打桩。 */
    private void stubCohorts(int slaughterCount, BigDecimal arrive,
                             BigDecimal rateArrive, BigDecimal rateBase,
                             BigDecimal barTotal, int barPigCount,
                             int finishedCount, BigDecimal finishedArrive) {
        when(aggregateMapper.countSlaughter(TENANT, DATE_STR)).thenReturn(slaughterCount);
        when(aggregateMapper.sumArriveWeight(TENANT, DATE_STR)).thenReturn(arrive);
        when(aggregateMapper.selectSlaughterRateBase(TENANT, DATE_STR)).thenReturn(Map.of(
            "rateArrive", rateArrive, "rateBase", rateBase));
        when(aggregateMapper.selectWhiteBarInAgg(TENANT, DATE_STR)).thenReturn(Map.of(
            "barTotalWeight", barTotal, "barPigCount", barPigCount));
        when(aggregateMapper.selectFinishedAgg(TENANT, DATE_STR)).thenReturn(Map.of(
            "finishedCount", finishedCount, "finishedArriveWeight", finishedArrive));
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
