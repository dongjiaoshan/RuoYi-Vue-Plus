package org.dromara.djs.warehouse.dashboard.service.impl;

import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.dashboard.domain.vo.LocationOverviewItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardChartsVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehousePorkEfficiencyVo;
import org.dromara.djs.warehouse.dashboard.mapper.WarehouseDashboardMapper;
import org.dromara.djs.warehouse.dashboard.mapper.WarehouseProductionDashboardMapper;
import org.dromara.djs.warehouse.stat.domain.WarehouseIndicatorRecord;
import org.dromara.djs.warehouse.stat.domain.WarehouseMonthlyRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link WarehouseDashboardServiceImpl} 单测。
 *
 * <p>覆盖 3 个核心场景：</p>
 * <ol>
 *   <li>happy：mapper 各聚合返非空 → VO 字段逐项透传 + 库位列表直传</li>
 *   <li>全空兜底：mapper 各聚合返 null → 计数全 0、库位列表空、不抛 NPE</li>
 *   <li>租户回退：TenantHelper 抛异常 → 回退 DEFAULT_TENANT '1001' 调 mapper</li>
 *   <li>年度送宰均重 / 屠宰率 / 白条出品率平均非NULL日值（V6-R281）</li>
 * </ol>
 *
 * <p>service 不用 LambdaWrapper（纯 Mapper 注解 SQL），故无需 entity cache 预热。
 * {@code MockedStatic(TenantHelper)} stub 当前租户。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WarehouseDashboardServiceImpl 单元测试")
class WarehouseDashboardServiceImplTest {

    @Mock
    private WarehouseDashboardMapper dashboardMapper;

    @Mock
    private WarehouseProductionDashboardMapper productionDashboardMapper;

    private WarehouseDashboardServiceImpl service;

    private MockedStatic<TenantHelper> tenantHelperMock;

    @BeforeEach
    void setUp() {
        service = new WarehouseDashboardServiceImpl(dashboardMapper, productionDashboardMapper);
        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn("1001");
    }

    @AfterEach
    void tearDown() {
        if (tenantHelperMock != null) {
            tenantHelperMock.close();
        }
    }

    @Test
    @DisplayName("happy：各聚合非空 → VO 字段逐项透传")
    void getSummary_happy() {
        when(dashboardMapper.sumTodayWhiteBarDemand(eq("1001"))).thenReturn(new BigDecimal("123.500"));
        when(dashboardMapper.countTodayProduction(eq("1001"))).thenReturn(7);
        when(dashboardMapper.countLatestCheckNormal(eq("1001"))).thenReturn(5);
        when(dashboardMapper.countLatestCheckAbnormal(eq("1001"))).thenReturn(2);
        when(dashboardMapper.countLatestCheckLoss(eq("1001"))).thenReturn(1);
        when(dashboardMapper.countMonthAbnormalLocation(eq("1001"))).thenReturn(3);

        LocationOverviewItemVo item = new LocationOverviewItemVo();
        item.setLocationId(1001L);
        item.setLocationName("冷藏-01");
        item.setLocationType("frozen");
        item.setCurrentStock(new BigDecimal("88.00"));
        item.setStatus("normal");
        when(dashboardMapper.selectLocationOverview(eq("1001"))).thenReturn(List.of(item));

        WarehouseDashboardSummaryVo vo = service.getSummary();

        assertThat(vo.getTodayDemandQuantity()).isEqualByComparingTo("123.500");
        assertThat(vo.getTodayProductionCount()).isEqualTo(7);
        assertThat(vo.getStockCheckNormal()).isEqualTo(5);
        assertThat(vo.getStockCheckAbnormal()).isEqualTo(2);
        assertThat(vo.getStockCheckLoss()).isEqualTo(1);
        assertThat(vo.getMonthAbnormalLocationCount()).isEqualTo(3);
        assertThat(vo.getLocationOverview()).hasSize(1);
        assertThat(vo.getLocationOverview().get(0).getLocationName()).isEqualTo("冷藏-01");
        assertThat(vo.getLocationOverview().get(0).getStatus()).isEqualTo("normal");
    }

    @Test
    @DisplayName("全空兜底：各聚合返 null → 计数全 0、库位列表空、不抛 NPE")
    void getSummary_allNull_fallbackZero() {
        when(dashboardMapper.sumTodayWhiteBarDemand(eq("1001"))).thenReturn(null);
        when(dashboardMapper.countTodayProduction(eq("1001"))).thenReturn(null);
        when(dashboardMapper.countLatestCheckNormal(eq("1001"))).thenReturn(null);
        when(dashboardMapper.countLatestCheckAbnormal(eq("1001"))).thenReturn(null);
        when(dashboardMapper.countLatestCheckLoss(eq("1001"))).thenReturn(null);
        when(dashboardMapper.countMonthAbnormalLocation(eq("1001"))).thenReturn(null);
        when(dashboardMapper.selectLocationOverview(eq("1001"))).thenReturn(null);

        WarehouseDashboardSummaryVo vo = service.getSummary();

        assertThat(vo.getTodayDemandQuantity()).isEqualByComparingTo("0");
        assertThat(vo.getTodayProductionCount()).isZero();
        assertThat(vo.getStockCheckNormal()).isZero();
        assertThat(vo.getStockCheckAbnormal()).isZero();
        assertThat(vo.getStockCheckLoss()).isZero();
        assertThat(vo.getMonthAbnormalLocationCount()).isZero();
        assertThat(vo.getLocationOverview()).isEmpty();
    }

    @Test
    @DisplayName("租户回退：TenantHelper 抛异常 → 用 DEFAULT_TENANT 1001 调 mapper")
    void getSummary_tenantError_fallbackDefault() {
        tenantHelperMock.when(TenantHelper::getTenantId).thenThrow(new RuntimeException("no tenant ctx"));
        when(dashboardMapper.sumTodayWhiteBarDemand(eq("1001"))).thenReturn(new BigDecimal("10"));
        when(dashboardMapper.countTodayProduction(eq("1001"))).thenReturn(0);
        when(dashboardMapper.countLatestCheckNormal(eq("1001"))).thenReturn(0);
        when(dashboardMapper.countLatestCheckAbnormal(eq("1001"))).thenReturn(0);
        when(dashboardMapper.countLatestCheckLoss(eq("1001"))).thenReturn(0);
        when(dashboardMapper.countMonthAbnormalLocation(eq("1001"))).thenReturn(0);
        when(dashboardMapper.selectLocationOverview(eq("1001"))).thenReturn(List.of());

        WarehouseDashboardSummaryVo vo = service.getSummary();

        assertThat(vo.getTodayDemandQuantity()).isEqualByComparingTo("10");
        assertThat(vo.getLocationOverview()).isEmpty();
    }

    @Test
    @DisplayName("图表指标：送宰头数与分割产品流水总重透传，并对空值兜零")
    void getCharts_mapsSlaughterAndCutProductMetrics() {
        when(dashboardMapper.countTodaySlaughterPigs(eq("1001"))).thenReturn(1);
        when(dashboardMapper.sumTodayCutProductWeight(eq("1001"))).thenReturn(new BigDecimal("16.500"));

        WarehouseDashboardChartsVo vo = service.getCharts();

        assertThat(vo.getTodaySlaughterPigCount()).isEqualTo(1);
        assertThat(vo.getTodayCutProductWeight()).isEqualByComparingTo("16.500");
    }

    /** V6-R281：年度三项按各字段非 NULL 的日值平均，各项有效日独立计数。 */
    @Test
    @DisplayName("年度送宰均重 / 两项出品率分别平均非NULL日值，真实0计入")
    void getPorkEfficiency_yearlyAveragesUseEachMetricsNonNullDays() {
        WarehouseIndicatorRecord d1 = indicatorRow(LocalDate.of(2026, 3, 1), 1,
            "100", "90", "80", "90", "100", "80", "100");
        d1.setAvgSlaughterWeight(new BigDecimal("100"));
        d1.setSlaughterRate(new BigDecimal("90"));
        d1.setBarYieldRate(new BigDecimal("80"));
        WarehouseIndicatorRecord d2 = indicatorRow(LocalDate.of(2026, 3, 2), 9,
            "1800", "900", "900", "900", "1800", "900", "1800");
        d2.setAvgSlaughterWeight(new BigDecimal("200"));
        d2.setSlaughterRate(new BigDecimal("50"));
        d2.setBarYieldRate(null);
        WarehouseIndicatorRecord d3 = new WarehouseIndicatorRecord();
        d3.setStatDate(LocalDate.of(2026, 3, 3));
        d3.setSlaughterRate(BigDecimal.ZERO);
        d3.setBarYieldRate(new BigDecimal("40"));
        when(productionDashboardMapper.selectIndicatorRecordsInRange(
            eq("1001"), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31))))
            .thenReturn(List.of(d1, d2, d3));

        WarehousePorkEfficiencyVo vo = service.getPorkEfficiency(2026, "2026-01");

        assertThat(vo.getSlaughterCount()).isEqualTo(10);
        // 100/200各一天，不受1头/9头加权；NULL第三天不算。
        assertThat(vo.getAvgSlaughterWeight()).isEqualByComparingTo("150.00");
        // (90+50+0)/3 = 46.67，真实0不能被当成缺失。
        assertThat(vo.getSlaughterRate()).isEqualByComparingTo("46.67");
        // (80+40)/2 = 60.00，d2该字段NULL独立排除。
        assertThat(vo.getBarYieldRate()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("年度日值全NULL时保留NULL，即使旧基数列有值")
    void getPorkEfficiency_yearlyNullDailyMetricsStayNull() {
        WarehouseIndicatorRecord d = indicatorRow(LocalDate.of(2026, 3, 1), 1,
            "100", "90", "80", "90", "100", "80", "100");
        when(productionDashboardMapper.selectIndicatorRecordsInRange(
            eq("1001"), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31))))
            .thenReturn(List.of(d));

        WarehousePorkEfficiencyVo vo = service.getPorkEfficiency(2026, "2026-01");

        assertThat(vo.getAvgSlaughterWeight()).isNull();
        assertThat(vo.getSlaughterRate()).isNull();
        assertThat(vo.getBarYieldRate()).isNull();
    }

    /** 月度趋势折线直读月表已算好的比率，不受年度卡口径改动影响。 */
    @Test
    @DisplayName("月度趋势折线仍直读月表比率，不受年度均值改动影响")
    void getPorkEfficiency_monthlyTrendReadsMonthlyTableRates() {
        WarehouseMonthlyRecord m = new WarehouseMonthlyRecord();
        m.setStatMonth("2026-03");
        m.setSlaughterCount(6);
        m.setSlaughterRate(new BigDecimal("98.214"));
        m.setBarYieldRate(new BigDecimal("77.305"));
        m.setCutYieldRate(new BigDecimal("75.000"));
        when(productionDashboardMapper.selectMonthlyRecordsByYear(eq("1001"), eq("2026-%")))
            .thenReturn(List.of(m));

        WarehousePorkEfficiencyVo vo = service.getPorkEfficiency(2026, "2026-01");

        assertThat(vo.getTrendMonths()).containsExactly("2026-03");
        assertThat(vo.getTrendSlaughterCount()).containsExactly(6);
        assertThat(vo.getTrendSlaughterRate().get(0)).isEqualByComparingTo("98.214");
        assertThat(vo.getTrendBarYieldRate().get(0)).isEqualByComparingTo("77.305");
    }

    /** 当年无日表数据 → 两个率落 null（前端显「—」），不造 0。 */
    @Test
    @DisplayName("年度无数据 → 屠宰率 / 白条出品率 null，不造 0")
    void getPorkEfficiency_noYearRows_ratesNull() {
        when(productionDashboardMapper.selectIndicatorRecordsInRange(
            eq("1001"), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31))))
            .thenReturn(List.of());

        WarehousePorkEfficiencyVo vo = service.getPorkEfficiency(2026, "2026-01");

        assertThat(vo.getSlaughterCount()).isZero();
        assertThat(vo.getSlaughterRate()).isNull();
        assertThat(vo.getBarYieldRate()).isNull();
        assertThat(vo.getAvgSlaughterWeight()).isNull();
    }

    /**
     * 甲方 2026-09-08 红框横跨整行白条均重、含最右「累计」格：累计必须是
     * Σ白条总重 ÷ Σ当日入白条库猪只耳号去重数，<b>不是</b>「日均重再求平均」。
     *
     * <p>用两天拉开差距：D1 一头 110kg（均重 110.00），D2 十头 1240.9kg（均重 124.09）。
     * 正确累计 = (110+1240.9)/(1+10) = 1350.9/11 = 122.81；
     * 旧的「日均值平均」= (110.00+124.09)/2 = 117.05 —— 只有 11 头里 1 头的那天被当成
     * 与 10 头那天同等权重，这正是甲方圈出来的偏差。</p>
     */
    @Test
    @DisplayName("日矩阵「累计」：白条均重 = Σ白条总重 ÷ Σ去重耳号数，不是日均重再平均")
    void getPorkEfficiency_avgBarWeightTotalUsesCohortSums() {
        WarehouseIndicatorRecord d1 = barRow(LocalDate.of(2026, 8, 20), "110.000", 1, "110.000");
        WarehouseIndicatorRecord d2 = barRow(LocalDate.of(2026, 8, 21), "1240.900", 10, "124.090");
        when(productionDashboardMapper.selectIndicatorRecordsInRange(
            eq("1001"), eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31))))
            .thenReturn(List.of(d1, d2));

        WarehousePorkEfficiencyVo vo = service.getPorkEfficiency(2026, "2026-08");

        WarehousePorkEfficiencyVo.MatrixRow row = vo.getMatrixRows().stream()
            .filter(r -> "白条均重".equals(r.getMetric())).findFirst().orElseThrow();
        // 1350.900 / 11 = 122.81（日均重再平均会得到 117.05）
        assertThat(row.getTotal()).isEqualTo("122.81");
    }

    /** 整月一头白条都没入库（Σ分母 = 0）→ 累计兜 0.00，不除零、不留空。 */
    @Test
    @DisplayName("日矩阵「累计」：Σ去重耳号数为 0 → 白条均重累计兜 0.00，不除零")
    void getPorkEfficiency_avgBarWeightTotalZeroDenominator() {
        WarehouseIndicatorRecord d1 = barRow(LocalDate.of(2026, 8, 20), "0.000", 0, null);
        when(productionDashboardMapper.selectIndicatorRecordsInRange(
            eq("1001"), eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31))))
            .thenReturn(List.of(d1));

        WarehousePorkEfficiencyVo vo = service.getPorkEfficiency(2026, "2026-08");

        WarehousePorkEfficiencyVo.MatrixRow row = vo.getMatrixRows().stream()
            .filter(r -> "白条均重".equals(r.getMetric())).findFirst().orElseThrow();
        assertThat(row.getTotal()).isEqualTo("0.00");
    }

    /**
     * 回归钉子：<b>只有白条均重</b>改走 Σ分子/Σ分母，其余率/均值类指标的「累计」仍是
     * 「Σ日值 ÷ 有数据天数」。
     *
     * <p>{@code PorkMetric} 加了一对可选的 {@code totalNumer/totalDenom}，两个都不给就走原逻辑。
     * 「不给就等价于原逻辑」这件事光靠读代码支撑不住 —— 下一个人往这对取值器上接第二个指标时
     * 改坏了没人会发现，所以在这里钉死两个代表指标的现状值。</p>
     *
     * <p>用两天把两种算法拉开：D1 屠宰率 90%（90/100）、D2 屠宰率 50%（500/1000）。
     * 现状「日值平均」= (90+50)/2 = <b>70.00</b>；若误接成 Σ分子/Σ分母 = 590/1100 = 53.64。
     * 送宰均重同理：现状 (100+200)/2 = <b>150.00</b>；Σ/Σ = 1900/10 = 190.00。
     * 这两个数<b>不是</b>在断言现状更正确 —— 它只是「本次没动它们」的护栏，
     * 真要改口径请连这条用例一起改。</p>
     */
    @Test
    @DisplayName("回归：其余率/均值指标的累计仍是 Σ日值 ÷ 有数据天数（本次只动了白条均重）")
    void getPorkEfficiency_otherRateTotalsUnchanged() {
        WarehouseIndicatorRecord d1 = new WarehouseIndicatorRecord();
        d1.setStatDate(LocalDate.of(2026, 8, 20));
        d1.setSlaughterCount(1);
        d1.setSlaughterWeight(new BigDecimal("100.000"));
        d1.setAvgSlaughterWeight(new BigDecimal("100.000"));
        d1.setSlaughterRate(new BigDecimal("90.000"));
        d1.setSlaughterRateArriveWeight(new BigDecimal("90.000"));
        d1.setSlaughterRateBaseWeight(new BigDecimal("100.000"));
        // 同一份数据里白条段照新口径走，两条路径并存互不干扰
        d1.setBarTotalWeight(new BigDecimal("110.000"));
        d1.setBarPigCount(1);
        d1.setAvgBarWeight(new BigDecimal("110.000"));

        WarehouseIndicatorRecord d2 = new WarehouseIndicatorRecord();
        d2.setStatDate(LocalDate.of(2026, 8, 21));
        d2.setSlaughterCount(9);
        d2.setSlaughterWeight(new BigDecimal("1800.000"));
        d2.setAvgSlaughterWeight(new BigDecimal("200.000"));
        d2.setSlaughterRate(new BigDecimal("50.000"));
        d2.setSlaughterRateArriveWeight(new BigDecimal("500.000"));
        d2.setSlaughterRateBaseWeight(new BigDecimal("1000.000"));
        d2.setBarTotalWeight(new BigDecimal("1240.900"));
        d2.setBarPigCount(10);
        d2.setAvgBarWeight(new BigDecimal("124.090"));

        when(productionDashboardMapper.selectIndicatorRecordsInRange(
            eq("1001"), eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31))))
            .thenReturn(List.of(d1, d2));

        WarehousePorkEfficiencyVo vo = service.getPorkEfficiency(2026, "2026-08");

        // 未接 cohort 基数的指标：仍是日值平均（接成 Σ/Σ 会分别变成 53.64 / 190.00）
        assertThat(totalOf(vo, "屠宰率")).isEqualTo("70.00");
        assertThat(totalOf(vo, "送宰均重")).isEqualTo("150.00");
        // 已接 cohort 基数的白条均重：Σ分子/Σ分母 = 1350.900/11 = 122.81（日值平均是 117.05）
        assertThat(totalOf(vo, "白条均重")).isEqualTo("122.81");
    }

    @Test
    @DisplayName("接收均重矩阵取新日均重，累计保持日值平均")
    void getPorkEfficiency_arriveAverageReadsNewDailyField() {
        WarehouseIndicatorRecord d1 = new WarehouseIndicatorRecord();
        d1.setStatDate(LocalDate.of(2026, 8, 20));
        d1.setArriveWeight(new BigDecimal("305"));
        d1.setArrivePigCount(3);
        d1.setAvgArriveWeight(new BigDecimal("101.667"));
        WarehouseIndicatorRecord d2 = new WarehouseIndicatorRecord();
        d2.setStatDate(LocalDate.of(2026, 8, 21));
        d2.setArriveWeight(new BigDecimal("100"));
        d2.setArrivePigCount(1);
        d2.setAvgArriveWeight(new BigDecimal("100.000"));
        when(productionDashboardMapper.selectIndicatorRecordsInRange(
            eq("1001"), eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31))))
            .thenReturn(List.of(d1, d2));

        WarehousePorkEfficiencyVo vo = service.getPorkEfficiency(2026, "2026-08");

        WarehousePorkEfficiencyVo.MatrixRow row = vo.getMatrixRows().stream()
            .filter(r -> "接收均重".equals(r.getMetric())).findFirst().orElseThrow();
        assertThat(row.getDailyValues().get(vo.getMatrixDays().indexOf("08-20"))).isEqualTo("101.67");
        assertThat(row.getTotal()).isEqualTo("100.83");
    }

    @Test
    @DisplayName("接收均重缺失时显示0.00，不以接收总重冒充均重")
    void getPorkEfficiency_missingArriveAverageDoesNotUseTotalWeight() {
        WarehouseIndicatorRecord d = new WarehouseIndicatorRecord();
        d.setStatDate(LocalDate.of(2026, 8, 20));
        d.setArriveWeight(new BigDecimal("305"));
        when(productionDashboardMapper.selectIndicatorRecordsInRange(
            eq("1001"), eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31))))
            .thenReturn(List.of(d));

        WarehousePorkEfficiencyVo vo = service.getPorkEfficiency(2026, "2026-08");

        WarehousePorkEfficiencyVo.MatrixRow row = vo.getMatrixRows().stream()
            .filter(r -> "接收均重".equals(r.getMetric())).findFirst().orElseThrow();
        int dayIndex = vo.getMatrixDays().indexOf("08-20");
        assertThat(row.getDailyValues().get(dayIndex)).isEqualTo("0.00");
        assertThat(row.getTotal()).isEqualTo("0.00");
    }

    /** 取矩阵里某个指标行的「累计」格。 */
    private static String totalOf(WarehousePorkEfficiencyVo vo, String metric) {
        return vo.getMatrixRows().stream()
            .filter(r -> metric.equals(r.getMetric())).findFirst().orElseThrow().getTotal();
    }

    /** 白条段最小行：只填白条总重 / 去重耳号数 / 日均重，其余指标留空。 */
    private static WarehouseIndicatorRecord barRow(LocalDate statDate, String barTotalWeight,
                                                   int barPigCount, String avgBarWeight) {
        WarehouseIndicatorRecord r = new WarehouseIndicatorRecord();
        r.setStatDate(statDate);
        r.setBarTotalWeight(new BigDecimal(barTotalWeight));
        r.setBarPigCount(barPigCount);
        r.setAvgBarWeight(avgBarWeight == null ? null : new BigDecimal(avgBarWeight));
        return r;
    }

    private static WarehouseIndicatorRecord indicatorRow(LocalDate statDate, int slaughterCount,
                                                         String slaughterWeight, String arriveWeight,
                                                         String barTotalWeight,
                                                         String rateArrive, String rateBase,
                                                         String yieldNumer, String yieldBase) {
        WarehouseIndicatorRecord r = new WarehouseIndicatorRecord();
        r.setStatDate(statDate);
        r.setSlaughterCount(slaughterCount);
        r.setSlaughterWeight(new BigDecimal(slaughterWeight));
        r.setArriveWeight(new BigDecimal(arriveWeight));
        r.setBarTotalWeight(new BigDecimal(barTotalWeight));
        r.setSlaughterRateArriveWeight(new BigDecimal(rateArrive));
        r.setSlaughterRateBaseWeight(new BigDecimal(rateBase));
        r.setBarYieldNumerWeight(new BigDecimal(yieldNumer));
        r.setBarYieldBaseWeight(new BigDecimal(yieldBase));
        return r;
    }

}
