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
 *   <li>年度屠宰率 / 白条出品率走日表 cohort 基数列（与日表 / 月表同源，V6-R172）</li>
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

    /**
     * V6-R172：年度屠宰率 / 白条出品率必须走日表 cohort 基数列（Σ分子基数 ÷ Σ分母基数），
     * 与日表 / 月表同源。两行日表数据刻意让旧公式（Σ接收÷Σ送宰总重、Σ白条总重÷Σ送宰总重）
     * 双双破 100% —— 谁把年度卡改回旧公式，这条当场红。
     */
    @Test
    @DisplayName("年度率走 cohort 基数列：Σ分子÷Σ分母，不是 Σ接收/Σ白条总重 ÷ Σ送宰总重")
    void getPorkEfficiency_yearlyRatesUseCohortBaseColumns() {
        // 日1：送宰 4 头 360kg，接收 454，白条 443；屠宰率基数 354/360，出品率基数 365/475
        // 日2：送宰 2 头 200kg，接收 250，白条 210；屠宰率基数 196/200，出品率基数 180/230
        WarehouseIndicatorRecord d1 = indicatorRow(LocalDate.of(2026, 3, 1), 4,
            "360", "454", "443", "354", "360", "365", "475");
        WarehouseIndicatorRecord d2 = indicatorRow(LocalDate.of(2026, 3, 2), 2,
            "200", "250", "210", "196", "200", "180", "230");
        when(productionDashboardMapper.selectIndicatorRecordsInRange(
            eq("1001"), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31))))
            .thenReturn(List.of(d1, d2));

        WarehousePorkEfficiencyVo vo = service.getPorkEfficiency(2026, "2026-01");

        assertThat(vo.getSlaughterCount()).isEqualTo(6);
        // 送宰均重 = (360+200)/6 = 93.33（展示卡仍用 slaughter_weight，未受影响）
        assertThat(vo.getAvgSlaughterWeight()).isEqualByComparingTo("93.33");
        // 屠宰率 = (354+196)/(360+200)×100 = 550/560 = 98.21；旧公式 = 704/560 = 125.71（破 100）
        assertThat(vo.getSlaughterRate()).isEqualByComparingTo("98.21");
        // 白条出品率 = (365+180)/(475+230)×100 = 545/705 = 77.30；旧公式 = 653/560 = 116.61（破 100）
        assertThat(vo.getBarYieldRate()).isEqualByComparingTo("77.30");
        assertThat(vo.getSlaughterRate()).isLessThanOrEqualTo(new BigDecimal("100"));
        assertThat(vo.getBarYieldRate()).isLessThanOrEqualTo(new BigDecimal("100"));
    }

    /** 月度趋势折线直读月表已算好的比率，不受年度卡口径改动影响。 */
    @Test
    @DisplayName("月度趋势折线仍直读月表比率（与年度卡同源、互不干扰）")
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
