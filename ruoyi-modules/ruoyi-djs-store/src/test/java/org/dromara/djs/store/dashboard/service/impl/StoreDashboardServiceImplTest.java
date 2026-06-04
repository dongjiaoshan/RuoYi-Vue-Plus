package org.dromara.djs.store.dashboard.service.impl;

import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardDailyVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardSummaryVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreGroupCountVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreProductRankItemVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreTrendPointVo;
import org.dromara.djs.store.dashboard.mapper.StoreDashboardMapper;
import org.dromara.djs.store.dashboard.mapper.StoreDashboardMapper.DailyCategoryRow;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link StoreDashboardServiceImpl} 单测（STR-DASH-001）。
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>summary happy：各聚合返非空 → 6 KPI 透传 + 趋势客单价 service 计算</li>
 *   <li>summary 全空兜底：各聚合返 null → 计数全 0、列表全空、不抛 NPE</li>
 *   <li>daily happy：猪肉 / 果蔬速览 + 客单价；退回率 / 损耗率为 null（占位）</li>
 *   <li>daily 全空兜底：mapper 返 null row → 速览 empty（销售额 0 / 订单 0）</li>
 *   <li>客单价除零：订单数 0 → avgPrice 0，不抛 ArithmeticException</li>
 *   <li>租户回退：TenantHelper 抛异常 → 回退 DEFAULT_TENANT '1001'</li>
 * </ol>
 *
 * <p>service 不用 LambdaWrapper（纯 Mapper 注解 SQL），故无需 entity cache 预热。
 * {@code MockedStatic(TenantHelper)} stub 当前租户。</p>
 *
 * @author djs
 * @since STR-DASH-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreDashboardServiceImpl 单元测试")
class StoreDashboardServiceImplTest {

    @Mock
    private StoreDashboardMapper dashboardMapper;

    private StoreDashboardServiceImpl service;

    private MockedStatic<TenantHelper> tenantHelperMock;

    @BeforeEach
    void setUp() {
        service = new StoreDashboardServiceImpl(dashboardMapper);
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
    @DisplayName("summary happy：6 KPI 透传 + 趋势客单价 service 计算")
    void getSummary_happy() {
        when(dashboardMapper.sumTodaySaleAmount(eq("1001"), eq(null))).thenReturn(new BigDecimal("1200.50"));
        when(dashboardMapper.sumMonthSaleAmount(eq("1001"), eq(null))).thenReturn(new BigDecimal("35000.00"));
        when(dashboardMapper.countTodayOrders(eq("1001"), eq(null))).thenReturn(8);
        when(dashboardMapper.countMonthOrders(eq("1001"), eq(null))).thenReturn(210);
        when(dashboardMapper.countPendingShip(eq("1001"), eq(null))).thenReturn(3);
        when(dashboardMapper.countPendingPurchase(eq("1001"), eq(null))).thenReturn(2);

        StoreGroupCountVo struct = new StoreGroupCountVo();
        struct.setKey("white_bar");
        struct.setValue(new BigDecimal("50"));
        when(dashboardMapper.selectProductStructure(eq("1001"), eq(null))).thenReturn(List.of(struct));

        StoreProductRankItemVo rank = new StoreProductRankItemVo();
        rank.setProductId(2058525064717926401L);
        rank.setProductName("土猪五花肉");
        rank.setSaleAmount(new BigDecimal("8800.00"));
        rank.setSaleQty(new BigDecimal("120.000"));
        when(dashboardMapper.selectTop10Products(eq("1001"), eq(null))).thenReturn(List.of(rank));

        StoreTrendPointVo point = new StoreTrendPointVo();
        point.setDate("2026-06-02");
        point.setOrderCount(4);
        point.setSaleAmount(new BigDecimal("400.00"));
        List<StoreTrendPointVo> trend = new ArrayList<>();
        trend.add(point);
        when(dashboardMapper.selectTrend10Days(eq("1001"), eq(null))).thenReturn(trend);

        StoreDashboardSummaryVo vo = service.getSummary(null);

        assertThat(vo.getTodaySaleAmount()).isEqualByComparingTo("1200.50");
        assertThat(vo.getMonthSaleAmount()).isEqualByComparingTo("35000.00");
        assertThat(vo.getTodayOrderCount()).isEqualTo(8);
        assertThat(vo.getMonthOrderCount()).isEqualTo(210);
        assertThat(vo.getPendingShipCount()).isEqualTo(3);
        assertThat(vo.getPendingPurchaseCount()).isEqualTo(2);
        assertThat(vo.getProductStructure()).hasSize(1);
        assertThat(vo.getTop10Products()).hasSize(1);
        assertThat(vo.getTop10Products().get(0).getProductId()).isEqualTo(2058525064717926401L);
        assertThat(vo.getTrend10Days()).hasSize(1);
        // 客单价 service 计算：400 / 4 = 100.00
        assertThat(vo.getTrend10Days().get(0).getAvgPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("summary 全空兜底：各聚合返 null → 计数全 0、列表全空、不抛 NPE")
    void getSummary_allNull_fallbackZero() {
        when(dashboardMapper.sumTodaySaleAmount(eq("1001"), eq(null))).thenReturn(null);
        when(dashboardMapper.sumMonthSaleAmount(eq("1001"), eq(null))).thenReturn(null);
        when(dashboardMapper.countTodayOrders(eq("1001"), eq(null))).thenReturn(null);
        when(dashboardMapper.countMonthOrders(eq("1001"), eq(null))).thenReturn(null);
        when(dashboardMapper.countPendingShip(eq("1001"), eq(null))).thenReturn(null);
        when(dashboardMapper.countPendingPurchase(eq("1001"), eq(null))).thenReturn(null);
        when(dashboardMapper.selectProductStructure(eq("1001"), eq(null))).thenReturn(null);
        when(dashboardMapper.selectTop10Products(eq("1001"), eq(null))).thenReturn(null);
        when(dashboardMapper.selectTrend10Days(eq("1001"), eq(null))).thenReturn(null);

        StoreDashboardSummaryVo vo = service.getSummary(null);

        assertThat(vo.getTodaySaleAmount()).isEqualByComparingTo("0");
        assertThat(vo.getMonthSaleAmount()).isEqualByComparingTo("0");
        assertThat(vo.getTodayOrderCount()).isZero();
        assertThat(vo.getMonthOrderCount()).isZero();
        assertThat(vo.getPendingShipCount()).isZero();
        assertThat(vo.getPendingPurchaseCount()).isZero();
        assertThat(vo.getProductStructure()).isEmpty();
        assertThat(vo.getTop10Products()).isEmpty();
        assertThat(vo.getTrend10Days()).isEmpty();
    }

    @Test
    @DisplayName("daily happy：猪肉 / 果蔬速览 + 客单价；退回率 / 损耗率为 null 占位")
    void getDaily_happy() {
        DailyCategoryRow porkRow = new DailyCategoryRow();
        porkRow.setValue(new BigDecimal("600.00"));
        porkRow.setOrderCount(3);
        DailyCategoryRow vegRow = new DailyCategoryRow();
        vegRow.setValue(new BigDecimal("250.00"));
        vegRow.setOrderCount(5);
        when(dashboardMapper.selectDailyByBelongTypes(eq("1001"), eq(null), any())).thenReturn(porkRow, vegRow);

        StoreGroupCountVo demand = new StoreGroupCountVo();
        demand.setKey("CONFIRMED");
        demand.setValue(new BigDecimal("4"));
        when(dashboardMapper.countDemandByStatus(eq("1001"), eq(null))).thenReturn(List.of(demand));

        StoreGroupCountVo ship = new StoreGroupCountVo();
        ship.setKey("COMPLETED");
        ship.setValue(new BigDecimal("2"));
        when(dashboardMapper.countShipByStatus(eq("1001"), eq(null))).thenReturn(List.of(ship));

        StoreDashboardDailyVo vo = service.getDaily(null);

        assertThat(vo.getPork().getSalesAmount()).isEqualByComparingTo("600.00");
        assertThat(vo.getPork().getOrderCount()).isEqualTo(3);
        // 客单价 600 / 3 = 200.00
        assertThat(vo.getPork().getAvgOrderPrice()).isEqualByComparingTo("200.00");
        // 退回率 / 损耗率 上游无数据 → null 占位
        assertThat(vo.getPork().getReturnRate()).isNull();
        assertThat(vo.getPork().getLossRate()).isNull();
        assertThat(vo.getVegetable().getSalesAmount()).isEqualByComparingTo("250.00");
        assertThat(vo.getVegetable().getAvgOrderPrice()).isEqualByComparingTo("50.00");
        assertThat(vo.getDemandStat()).hasSize(1);
        assertThat(vo.getShipStat()).hasSize(1);
    }

    @Test
    @DisplayName("daily 全空兜底：mapper 返 null row → 速览 empty")
    void getDaily_allNull_fallbackEmpty() {
        when(dashboardMapper.selectDailyByBelongTypes(eq("1001"), eq(null), any())).thenReturn(null);
        when(dashboardMapper.countDemandByStatus(eq("1001"), eq(null))).thenReturn(null);
        when(dashboardMapper.countShipByStatus(eq("1001"), eq(null))).thenReturn(null);

        StoreDashboardDailyVo vo = service.getDaily(null);

        assertThat(vo.getPork().getSalesAmount()).isEqualByComparingTo("0");
        assertThat(vo.getPork().getOrderCount()).isZero();
        assertThat(vo.getPork().getAvgOrderPrice()).isEqualByComparingTo("0");
        assertThat(vo.getPork().getReturnRate()).isNull();
        assertThat(vo.getVegetable().getSalesAmount()).isEqualByComparingTo("0");
        assertThat(vo.getDemandStat()).isEmpty();
        assertThat(vo.getShipStat()).isEmpty();
    }

    @Test
    @DisplayName("客单价除零：订单数 0 → avgPrice 0，不抛异常")
    void getDaily_zeroOrders_avgZero() {
        DailyCategoryRow row = new DailyCategoryRow();
        row.setValue(new BigDecimal("0"));
        row.setOrderCount(0);
        when(dashboardMapper.selectDailyByBelongTypes(eq("1001"), eq(null), any())).thenReturn(row);
        when(dashboardMapper.countDemandByStatus(eq("1001"), eq(null))).thenReturn(List.of());
        when(dashboardMapper.countShipByStatus(eq("1001"), eq(null))).thenReturn(List.of());

        StoreDashboardDailyVo vo = service.getDaily(null);

        assertThat(vo.getPork().getAvgOrderPrice()).isEqualByComparingTo("0");
        assertThat(vo.getPork().getOrderCount()).isZero();
    }

    @Test
    @DisplayName("storeId 显式过滤：传 storeId 透传给 mapper")
    void getSummary_withStoreId() {
        Long storeId = 9001L;
        when(dashboardMapper.sumTodaySaleAmount(eq("1001"), eq(storeId))).thenReturn(new BigDecimal("99.00"));
        when(dashboardMapper.sumMonthSaleAmount(eq("1001"), eq(storeId))).thenReturn(BigDecimal.ZERO);
        when(dashboardMapper.countTodayOrders(eq("1001"), eq(storeId))).thenReturn(1);
        when(dashboardMapper.countMonthOrders(eq("1001"), eq(storeId))).thenReturn(1);
        when(dashboardMapper.countPendingShip(eq("1001"), eq(storeId))).thenReturn(0);
        when(dashboardMapper.countPendingPurchase(eq("1001"), eq(storeId))).thenReturn(0);

        StoreDashboardSummaryVo vo = service.getSummary(storeId);

        assertThat(vo.getTodaySaleAmount()).isEqualByComparingTo("99.00");
        assertThat(vo.getTodayOrderCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("租户回退：TenantHelper 抛异常 → 用 DEFAULT_TENANT 1001 调 mapper")
    void getSummary_tenantError_fallbackDefault() {
        tenantHelperMock.when(TenantHelper::getTenantId).thenThrow(new RuntimeException("no tenant ctx"));
        when(dashboardMapper.sumTodaySaleAmount(eq("1001"), eq(null))).thenReturn(new BigDecimal("10"));
        when(dashboardMapper.sumMonthSaleAmount(eq("1001"), eq(null))).thenReturn(BigDecimal.ZERO);
        when(dashboardMapper.countTodayOrders(eq("1001"), eq(null))).thenReturn(0);
        when(dashboardMapper.countMonthOrders(eq("1001"), eq(null))).thenReturn(0);
        when(dashboardMapper.countPendingShip(eq("1001"), eq(null))).thenReturn(0);
        when(dashboardMapper.countPendingPurchase(eq("1001"), eq(null))).thenReturn(0);

        StoreDashboardSummaryVo vo = service.getSummary(null);

        assertThat(vo.getTodaySaleAmount()).isEqualByComparingTo("10");
        assertThat(vo.getTop10Products()).isEmpty();
    }

}
