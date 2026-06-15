package org.dromara.djs.plant.dashboard.service.impl;

import org.dromara.djs.plant.dashboard.domain.vo.CropPlantStatItemVo;
import org.dromara.djs.plant.dashboard.domain.vo.FarmWorkCountVo;
import org.dromara.djs.plant.dashboard.domain.vo.GanttItemVo;
import org.dromara.djs.plant.dashboard.domain.vo.MonthCompletionItemVo;
import org.dromara.djs.plant.dashboard.domain.vo.PlantDashboardSummaryVo;
import org.dromara.djs.plant.dashboard.mapper.PlantDashboardMapper;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link PlantDashboardServiceImpl} 种植看板聚合单测（PLT-DASH-001）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>getSummary happy：土地总览 5 计数 + 总面积 + 今日农事 list + 总条数 + 当月完成率 list 组装正确</li>
 *   <li>getSummary null 兜底：mapper 全返 null → 计数全 0 / 面积 ZERO / list 空，不抛错</li>
 *   <li>getGantt happy：一行同时有 plant + harvest 日期 → 拆出 2 条（plant + pick）+ 进度映射正确</li>
 *   <li>getGantt 部分段：一行只有 plant 日期 → 只 1 条；全 null 日期行不出现（mapper 已过滤）</li>
 *   <li>getGantt 空：mapper 返 null → 空列表</li>
 *   <li>id 拼接为 string（snowflake 契约）</li>
 * </ul>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlantDashboardServiceImplTest {

    @Mock
    private PlantDashboardMapper dashboardMapper;

    @InjectMocks
    private PlantDashboardServiceImpl service;

    @Test
    @DisplayName("getSummary happy：土地总览 + 今日农事 + 当月完成率组装正确")
    void getSummaryHappyPath() {
        PlantDashboardMapper.PlotOverviewRow overview = new PlantDashboardMapper.PlotOverviewRow();
        overview.setTotalCount(10);
        overview.setIdleCount(4);
        overview.setPlantingCount(3);
        overview.setHarvestingCount(2);
        overview.setTotalArea(new BigDecimal("123.45"));
        when(dashboardMapper.selectPlotOverview(anyString())).thenReturn(overview);
        when(dashboardMapper.countPendingPlot(anyString())).thenReturn(1);

        FarmWorkCountVo fw = new FarmWorkCountVo();
        fw.setFarmType("fertilize");
        fw.setCount(5);
        when(dashboardMapper.selectTodayFarmWork(anyString())).thenReturn(List.of(fw));
        when(dashboardMapper.countTodayFarmWorkTotal(anyString())).thenReturn(5);

        MonthCompletionItemVo mc = new MonthCompletionItemVo();
        mc.setCropName("番茄");
        mc.setActualYield(new BigDecimal("80"));
        mc.setExpectedYield(new BigDecimal("100"));
        when(dashboardMapper.selectMonthCompletion(anyString())).thenReturn(List.of(mc));

        when(dashboardMapper.selectCurrentPlantingArea(anyString())).thenReturn(new BigDecimal("66.00"));
        when(dashboardMapper.selectCurrentExpectedYield(anyString())).thenReturn(new BigDecimal("5000"));

        CropPlantStatItemVo cs = new CropPlantStatItemVo();
        cs.setCropName("番茄");
        cs.setPlotCount(3);
        cs.setExpectedYield(new BigDecimal("2000"));
        when(dashboardMapper.selectCropPlantStat(anyString())).thenReturn(List.of(cs));

        when(dashboardMapper.selectPlotCertMinDays(anyString())).thenReturn(143);
        when(dashboardMapper.selectCropCertMinDays(anyString())).thenReturn(167);
        when(dashboardMapper.selectCropNoCertCount(anyString())).thenReturn(6);
        when(dashboardMapper.selectCropTotalCount(anyString())).thenReturn(8);

        PlantDashboardSummaryVo vo = service.getSummary();

        assertThat(vo.getTotalPlotCount()).isEqualTo(10);
        assertThat(vo.getIdlePlotCount()).isEqualTo(4);
        assertThat(vo.getPlantingPlotCount()).isEqualTo(3);
        assertThat(vo.getHarvestingPlotCount()).isEqualTo(2);
        assertThat(vo.getPendingPlotCount()).isEqualTo(1);
        assertThat(vo.getTotalPlotArea()).isEqualByComparingTo("123.45");
        assertThat(vo.getTodayFarmWork()).hasSize(1);
        assertThat(vo.getTodayFarmWork().get(0).getFarmType()).isEqualTo("fertilize");
        assertThat(vo.getTodayFarmWorkTotal()).isEqualTo(5);
        assertThat(vo.getMonthCompletion()).hasSize(1);
        assertThat(vo.getMonthCompletion().get(0).getCropName()).isEqualTo("番茄");
        assertThat(vo.getCurrentPlantingArea()).isEqualByComparingTo("66.00");
        assertThat(vo.getCurrentExpectedYield()).isEqualByComparingTo("5000");
        assertThat(vo.getCropPlantStat()).hasSize(1);
        assertThat(vo.getCropPlantStat().get(0).getPlotCount()).isEqualTo(3);
        assertThat(vo.getOrganicCertOverview().getPlotCertMinDays()).isEqualTo(143);
        assertThat(vo.getOrganicCertOverview().getCropCertMinDays()).isEqualTo(167);
        assertThat(vo.getOrganicCertOverview().getCropNoCertCount()).isEqualTo(6);
        assertThat(vo.getOrganicCertOverview().getCropReservedCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("getSummary null 兜底：mapper 全返 null → 全 0 / ZERO / 空 list，不抛错")
    void getSummaryNullSafe() {
        when(dashboardMapper.selectPlotOverview(anyString())).thenReturn(null);
        when(dashboardMapper.countPendingPlot(anyString())).thenReturn(null);
        when(dashboardMapper.selectTodayFarmWork(anyString())).thenReturn(null);
        when(dashboardMapper.countTodayFarmWorkTotal(anyString())).thenReturn(null);
        when(dashboardMapper.selectMonthCompletion(anyString())).thenReturn(null);

        PlantDashboardSummaryVo vo = service.getSummary();

        assertThat(vo.getIdlePlotCount()).isZero();
        assertThat(vo.getPlantingPlotCount()).isZero();
        assertThat(vo.getHarvestingPlotCount()).isZero();
        assertThat(vo.getPendingPlotCount()).isZero();
        assertThat(vo.getTotalPlotCount()).isZero();
        assertThat(vo.getTotalPlotArea()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getTodayFarmWork()).isEmpty();
        assertThat(vo.getTodayFarmWorkTotal()).isZero();
        assertThat(vo.getMonthCompletion()).isEmpty();
        // 新增字段 null 兜底：面积 / 产量 ZERO，统计空列表，证书一览各 count 0 + 到期天数 null
        assertThat(vo.getCurrentPlantingArea()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getCurrentExpectedYield()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getCropPlantStat()).isEmpty();
        assertThat(vo.getOrganicCertOverview().getPlotCertMinDays()).isNull();
        assertThat(vo.getOrganicCertOverview().getCropCertMinDays()).isNull();
        assertThat(vo.getOrganicCertOverview().getCropNoCertCount()).isZero();
        assertThat(vo.getOrganicCertOverview().getCropReservedCount()).isZero();
    }

    @Test
    @DisplayName("getGantt happy：一行同时有 plant + harvest 日期 → 拆出 2 条（plant + pick）+ 进度映射")
    void getGanttSplitTwoSegments() {
        PlantDashboardMapper.GanttRow row = new PlantDashboardMapper.GanttRow();
        row.setId(2058525064717926401L);
        row.setCropName("番茄");
        row.setPlotName("A 区 01 号");
        row.setBeginActualdate(LocalDate.of(2026, 6, 1));
        row.setEndActualdate(LocalDate.of(2026, 6, 20));
        row.setBeginHarvestdate(LocalDate.of(2026, 6, 21));
        row.setEndHarvestdate(LocalDate.of(2026, 6, 30));
        row.setPlantStatus("completed");
        row.setHarvestStatus("picking");
        when(dashboardMapper.selectGanttRows(anyString())).thenReturn(List.of(row));

        List<GanttItemVo> items = service.getGantt();

        assertThat(items).hasSize(2);
        GanttItemVo plant = items.stream().filter(i -> "plant".equals(i.getType())).findFirst().orElseThrow();
        GanttItemVo pick = items.stream().filter(i -> "pick".equals(i.getType())).findFirst().orElseThrow();
        // id 拼接为 string，保留完整 snowflake（契约 1：不转 number 丢精度）
        assertThat(plant.getId()).isEqualTo("2058525064717926401-plant");
        assertThat(pick.getId()).isEqualTo("2058525064717926401-pick");
        assertThat(plant.getText()).isEqualTo("番茄-A 区 01 号");
        assertThat(plant.getProgress()).isEqualTo(100); // completed
        assertThat(pick.getProgress()).isEqualTo(50);   // picking
    }

    @Test
    @DisplayName("getGantt 部分段：只有 plant 日期 → 只拆出 1 条 plant")
    void getGanttOnlyPlantSegment() {
        PlantDashboardMapper.GanttRow row = new PlantDashboardMapper.GanttRow();
        row.setId(100L);
        row.setCropName("生菜");
        row.setPlotName("B 区");
        row.setBeginActualdate(LocalDate.of(2026, 6, 1));
        row.setEndActualdate(LocalDate.of(2026, 6, 10));
        row.setBeginHarvestdate(null);
        row.setEndHarvestdate(null);
        row.setPlantStatus("ongoing");
        when(dashboardMapper.selectGanttRows(anyString())).thenReturn(List.of(row));

        List<GanttItemVo> items = service.getGantt();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getType()).isEqualTo("plant");
        assertThat(items.get(0).getProgress()).isEqualTo(50); // ongoing
    }

    @Test
    @DisplayName("getGantt 空：mapper 返 null → 空列表，不抛错")
    void getGanttEmpty() {
        when(dashboardMapper.selectGanttRows(anyString())).thenReturn(null);
        assertThat(service.getGantt()).isEmpty();
    }

    @Test
    @DisplayName("getGantt 名称为空时用占位文本")
    void getGanttBlankNamePlaceholder() {
        PlantDashboardMapper.GanttRow row = new PlantDashboardMapper.GanttRow();
        row.setId(101L);
        row.setCropName(null);
        row.setPlotName(null);
        row.setBeginActualdate(LocalDate.of(2026, 6, 1));
        row.setEndActualdate(LocalDate.of(2026, 6, 10));
        row.setPlantStatus("pending");
        when(dashboardMapper.selectGanttRows(anyString())).thenReturn(List.of(row));

        List<GanttItemVo> items = service.getGantt();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getText()).isEqualTo("未知作物-未知地块");
        assertThat(items.get(0).getProgress()).isZero(); // pending
    }

}
