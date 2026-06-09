package org.dromara.djs.plant.dashboard.applet.service.impl;

import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantManageOverviewVo;
import org.dromara.djs.plant.dashboard.applet.mapper.AppletPlantManageDashboardMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link AppletPlantManageDashboardServiceImpl} mp 管理·种植看板聚合单测（FIX-MGMT-MP-PLT-001）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>getOverview happy：6 KPI（3 地块计数 + 已种品种 + 当前面积 + 可采品种）组装正确</li>
 *   <li>getOverview null 兜底：mapper 全返 null → 计数全 0 / 面积 ZERO，不抛错</li>
 * </ul>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppletPlantManageDashboardServiceImplTest {

    @Mock
    private AppletPlantManageDashboardMapper dashboardMapper;

    @InjectMocks
    private AppletPlantManageDashboardServiceImpl service;

    @Test
    @DisplayName("getOverview happy：6 KPI 组装正确")
    void getOverviewHappyPath() {
        AppletPlantManageDashboardMapper.PlotCountRow plot = new AppletPlantManageDashboardMapper.PlotCountRow();
        plot.setTotalPlotCount(20);
        plot.setPlantingPlotCount(8);
        plot.setIdlePlotCount(7);
        when(dashboardMapper.selectPlotCount(anyString())).thenReturn(plot);
        when(dashboardMapper.countPlantedCrop(anyString())).thenReturn(5);
        when(dashboardMapper.sumCurrentPlantArea(anyString())).thenReturn(new BigDecimal("66.50"));
        when(dashboardMapper.countHarvestableCrop(anyString())).thenReturn(3);

        PlantManageOverviewVo vo = service.getOverview();

        assertThat(vo.getTotalPlotCount()).isEqualTo(20);
        assertThat(vo.getPlantingPlotCount()).isEqualTo(8);
        assertThat(vo.getIdlePlotCount()).isEqualTo(7);
        assertThat(vo.getPlantedCropCount()).isEqualTo(5);
        assertThat(vo.getCurrentPlantArea()).isEqualByComparingTo("66.50");
        assertThat(vo.getHarvestableCropCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("getOverview null 兜底：mapper 全返 null → 全 0 / ZERO，不抛错")
    void getOverviewNullSafe() {
        when(dashboardMapper.selectPlotCount(anyString())).thenReturn(null);
        when(dashboardMapper.countPlantedCrop(anyString())).thenReturn(null);
        when(dashboardMapper.sumCurrentPlantArea(anyString())).thenReturn(null);
        when(dashboardMapper.countHarvestableCrop(anyString())).thenReturn(null);

        PlantManageOverviewVo vo = service.getOverview();

        assertThat(vo.getTotalPlotCount()).isZero();
        assertThat(vo.getPlantingPlotCount()).isZero();
        assertThat(vo.getIdlePlotCount()).isZero();
        assertThat(vo.getPlantedCropCount()).isZero();
        assertThat(vo.getCurrentPlantArea()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getHarvestableCropCount()).isZero();
    }

}
