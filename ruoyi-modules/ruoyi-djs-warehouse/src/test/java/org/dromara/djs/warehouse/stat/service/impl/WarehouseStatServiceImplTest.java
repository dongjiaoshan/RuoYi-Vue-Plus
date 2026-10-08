package org.dromara.djs.warehouse.stat.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.loss.service.IProductionLossService;
import org.dromara.djs.warehouse.stat.domain.WarehouseIndicatorRecord;
import org.dromara.djs.warehouse.stat.domain.WarehouseMonthlyRecord;
import org.dromara.djs.warehouse.stat.mapper.WarehouseCroppRecordMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseIndicatorRecordMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseMonthlyRecordMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseStatAggregateMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WarehouseStatServiceImpl} 单测（仓库日指标猪肉段：接收 cohort，D-0140 / 甲方 V6 行284）。
 *
 * <ol>
 *   <li>猪肉段全部取「当日接收」的那批猪：屠宰头数 / 送宰均重 / 接收均重 / 白条均重都以接收头数为分母</li>
 *   <li>屠宰出品率、白条出品率取完整接收批次的重量之和</li>
 *   <li>处理完成头数 / 其接收重量之和只落盘，不参与任何比率或均值</li>
 *   <li>cohort 为空 / 分母 0 → 比率与均值落 null（不造假）</li>
 *   <li>回补只改猪肉段那几列（不把日表其它段清空），并刷新涉及的月表</li>
 *   <li>月表比率从日表落下的基数列 Σ 后重算</li>
 * </ol>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WarehouseStatServiceImpl 单元测试（猪肉段接收 cohort）")
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

    @BeforeAll
    static void initMpEntityCache() {
        // 回补用 LambdaUpdateWrapper<WarehouseIndicatorRecord> 按列 SET，无 Spring 上下文时先注册 TableInfo
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, WarehouseIndicatorRecord.class);
    }

    @BeforeEach
    void setUp() {
        tenantHelper = Mockito.mockStatic(TenantHelper.class);
        tenantHelper.when(TenantHelper::getTenantId).thenReturn(TENANT);
        // 非本次关注的聚合项统一给 0/空，聚焦猪肉段三 cohort
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
     * 甲方行284 原文的六个指标，用一批 3 头的接收 cohort 逐个钉：
     * 出栏重量之和 400、接收重量之和 300、白条产品重量之和 240（3 头都有出栏重量）。
     */
    @Test
    @DisplayName("六个日指标都按当日接收的那批猪算：头数 3 作送宰/接收/白条均重的分母")
    void testPorkMetricsUseReceivedCohort() {
        stubCohort(3, bd("400"), bd("300"), bd("240"), bd("300"), bd("400"), bd("240"));
        stubFinished(1, bd("95"));

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        assertThat(saved.getSlaughterCount()).isEqualTo(3);
        assertThat(saved.getSlaughterWeight()).isEqualByComparingTo("400.000");
        assertThat(saved.getAvgSlaughterWeight()).isEqualByComparingTo("133.333");
        assertThat(saved.getArriveWeight()).isEqualByComparingTo("300.000");
        assertThat(saved.getArrivePigCount()).isEqualTo(3);
        assertThat(saved.getAvgArriveWeight()).isEqualByComparingTo("100.000");
        assertThat(saved.getBarTotalWeight()).isEqualByComparingTo("240.000");
        assertThat(saved.getBarPigCount()).isEqualTo(3);
        assertThat(saved.getAvgBarWeight()).isEqualByComparingTo("80.000");
        // 屠宰出品率 300/400 = 75%；白条出品率 240/400 = 60%
        assertThat(saved.getSlaughterRate()).isEqualByComparingTo("75.000");
        assertThat(saved.getBarYieldRate()).isEqualByComparingTo("60.000");
        // 处理完成两列照落，但和上面任何一个数都无关
        assertThat(saved.getFinishedCount()).isEqualTo(1);
        assertThat(saved.getFinishedArriveWeight()).isEqualByComparingTo("95.000");
    }

    /**
     * 3 头里有 1 头没有出栏重量：D-0140 分子仍取整批接收/白条总重，不添加剔除条件。
     * 分母为已录出栏重量之和；均重除以接收头数 3。
     */
    @Test
    @DisplayName("缺出栏重量不缩小整批接收/白条分子，均重仍除以接收头数")
    void testRatesUseWholeReceivedCohort() {
        stubCohort(3, bd("230"), bd("305"), bd("190"), bd("210"), bd("230"), bd("150"));
        stubFinished(0, BigDecimal.ZERO);

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        assertThat(saved.getSlaughterRateArriveWeight()).isEqualByComparingTo("305.000");
        assertThat(saved.getSlaughterRateBaseWeight()).isEqualByComparingTo("230.000");
        assertThat(saved.getSlaughterRate()).isEqualByComparingTo("132.609");
        assertThat(saved.getBarYieldNumerWeight()).isEqualByComparingTo("190.000");
        assertThat(saved.getBarYieldBaseWeight()).isEqualByComparingTo("230.000");
        assertThat(saved.getBarYieldRate()).isEqualByComparingTo("82.609");
        // 白条总重展示列仍是整批 190，均重 = 190 / 3
        assertThat(saved.getBarTotalWeight()).isEqualByComparingTo("190.000");
        assertThat(saved.getAvgBarWeight()).isEqualByComparingTo("63.333");
        assertThat(saved.getAvgArriveWeight()).isEqualByComparingTo("101.667");
    }

    /** 当天没接收任何猪：所有均值 / 比率落 null，重量与头数落 0（ALWAYS 策略会覆盖旧值）。 */
    @Test
    @DisplayName("当天无接收 → 均重、出品率全落 null，头数与重量落 0")
    void testEmptyCohortYieldsNulls() {
        stubCohort(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        stubFinished(0, BigDecimal.ZERO);

        WarehouseIndicatorRecord saved = runAggregateAndCaptureDaily();

        assertThat(saved.getSlaughterCount()).isZero();
        assertThat(saved.getAvgSlaughterWeight()).isNull();
        assertThat(saved.getAvgArriveWeight()).isNull();
        assertThat(saved.getAvgBarWeight()).isNull();
        assertThat(saved.getSlaughterRate()).isNull();
        assertThat(saved.getBarYieldRate()).isNull();
        assertThat(saved.getBarTotalWeight()).isEqualByComparingTo("0.000");
    }

    /** 已有日表行时整行更新：头数归零后均重写 NULL（不残留旧值）。 */
    @Test
    @DisplayName("日表已存在 → updateById，接收头数为 0 时接收均重写 NULL")
    void testExistingRowUpdatedWithNullAverage() {
        stubCohort(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        stubFinished(0, BigDecimal.ZERO);
        WarehouseIndicatorRecord existing = new WarehouseIndicatorRecord();
        existing.setId(1L);
        existing.setAvgArriveWeight(bd("123"));
        when(indicatorMapper.selectOne(any())).thenReturn(existing);

        service.aggregate(DATE);

        ArgumentCaptor<WarehouseIndicatorRecord> captor = ArgumentCaptor.forClass(WarehouseIndicatorRecord.class);
        verify(indicatorMapper).updateById(captor.capture());
        WarehouseIndicatorRecord saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getArrivePigCount()).isZero();
        assertThat(saved.getAvgArriveWeight()).isNull();
    }

    /**
     * 回补：已有日表行只 set 猪肉段那几列 —— 日表实体里分割段等列标了 ALWAYS，
     * 用实体 updateById 会把它们清成 NULL。这里钉住「不走 updateById、SET 里没有分割列」。
     */
    @Test
    @DisplayName("回补已有日表行：只 SET 猪肉段列，不用 updateById（避免把分割段等列清空），并刷新月表")
    void testRefreshUpdatesOnlyPorkColumns() {
        stubCohort(2, bd("260"), bd("220"), bd("200"), bd("220"), bd("260"), bd("200"));
        WarehouseIndicatorRecord existing = new WarehouseIndicatorRecord();
        existing.setId(9L);
        when(indicatorMapper.selectOne(any())).thenReturn(existing);

        service.refreshPorkSegment(DATE, DATE);

        ArgumentCaptor<Wrapper<WarehouseIndicatorRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(indicatorMapper).update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        verify(indicatorMapper, never()).updateById(any(WarehouseIndicatorRecord.class));
        String sqlSet = captor.getValue().getSqlSet();
        assertThat(sqlSet).contains("slaughter_count", "avg_slaughter_weight", "avg_arrive_weight",
            "slaughter_rate", "bar_total_weight", "avg_bar_weight", "bar_yield_rate");
        assertThat(sqlSet).doesNotContain("cut_bar_count", "cut_rate", "veg_loss", "finished_count");
        // 回补不碰作物日表和生产损耗
        verify(croppMapper, never()).insert(any(org.dromara.djs.warehouse.stat.domain.WarehouseCroppRecord.class));
        verify(productionLossService, never()).aggregate(any());
        // 涉及的月份刷新一次
        verify(aggregateMapper).sumMonthlyFromDaily(TENANT, MONTH);
    }

    /** 回补遇到夜跑漏掉、还没有日表行的日子：整行补算一次。 */
    @Test
    @DisplayName("回补遇到没有日表行的日子 → 整行插入")
    void testRefreshInsertsMissingDay() {
        stubCohort(1, bd("120"), bd("100"), bd("90"), bd("100"), bd("120"), bd("90"));
        stubFinished(0, BigDecimal.ZERO);

        String msg = service.refreshPorkSegment(DATE, DATE);

        verify(indicatorMapper).insert(any(WarehouseIndicatorRecord.class));
        assertThat(msg).contains("created=1");
    }

    @Test
    @DisplayName("回补区间倒置 → 直接拒绝")
    void testRefreshRejectsInvertedRange() {
        assertThatThrownBy(() -> service.refreshPorkSegment(DATE, DATE.minusDays(1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 月表：屠宰出品率 = Σ分子/Σ分母，白条出品率 = Σ出品率分子/Σ出品率分母。
     * 用与「Σ接收/Σ送宰」明显不同的数字，保证走的是基数列而不是展示列；
     * 出品率分母取 sumBarYieldBase（不是 sumFinishedArrive），验月表侧与日率同口径。
     */
    @Test
    @DisplayName("月表比率从日表 cohort 基数列 Σ 后重算（出品率分母取 barYieldBase）")
    void testMonthlyRatesFromCohortBases() {
        stubCohort(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        stubFinished(0, BigDecimal.ZERO);
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
        stubCohort(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        stubFinished(0, BigDecimal.ZERO);
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

    /** 接收 cohort 聚合桩（字段名与 selectReceivedCohortAgg 的列别名一一对应）。 */
    private void stubCohort(int received, BigDecimal base, BigDecimal arrive, BigDecimal bar,
                            BigDecimal rateArrive, BigDecimal rateBase, BigDecimal barYieldNumer) {
        when(aggregateMapper.selectReceivedCohortAgg(TENANT, DATE_STR)).thenReturn(Map.of(
            "receivedCount", received, "baseWeight", base, "arriveWeight", arrive, "barWeight", bar,
            "rateArrive", rateArrive, "rateBase", rateBase, "barYieldNumer", barYieldNumer));
    }

    private void stubFinished(int finishedCount, BigDecimal finishedArrive) {
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
