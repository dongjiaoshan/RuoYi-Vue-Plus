package org.dromara.djs.warehouse.dashboard.service.impl;

import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.dashboard.domain.vo.LocationOverviewItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;
import org.dromara.djs.warehouse.dashboard.mapper.WarehouseDashboardMapper;
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

    private WarehouseDashboardServiceImpl service;

    private MockedStatic<TenantHelper> tenantHelperMock;

    @BeforeEach
    void setUp() {
        service = new WarehouseDashboardServiceImpl(dashboardMapper);
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

}
