package org.dromara.djs.plant.dashboard.service.impl;

import org.dromara.djs.plant.dashboard.domain.vo.CropPlantStatItemVo;
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
 *   <li>getSummary happy：土地总览 5 计数 + 总面积 + 今日工作 6 格 + 当月完成率 + 证书一览组装正确</li>
 *   <li>getSummary null 兜底：mapper 全返 null → 计数全 0 / 面积 ZERO / list 空 / 证书字段 null/0，不抛错</li>
 *   <li>getGantt 种植段：PlantGanttCropRow 有起止日期 → plant 段按作物聚合 + 进度按完成占比</li>
 *   <li>getGantt 采摘段：PickGanttCropRow 有采摘日期 → pick 段组装正确 + 进度比例计算</li>
 *   <li>getGantt 空：两 mapper 均返 null → 空列表</li>
 *   <li>getGantt 种植段 endDate 兜底：endDate 为 null → 用 startDate 兜底，进度比例四舍五入</li>
 *   <li>getGantt 名称为空时用占位文本 '未知作物'</li>
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

    // ======================== getSummary ========================

    @Test
    @DisplayName("getSummary happy：土地总览 + 今日工作 6 格 + 当月完成率 + 证书一览组装正确")
    void getSummaryHappyPath() {
        // Arrange — 土地总览
        PlantDashboardMapper.PlotOverviewRow overview = new PlantDashboardMapper.PlotOverviewRow();
        overview.setTotalCount(10);
        overview.setIdleCount(4);
        overview.setPlantingCount(3);
        overview.setHarvestingCount(2);
        overview.setTotalArea(new BigDecimal("123.45"));
        when(dashboardMapper.selectPlotOverview(anyString())).thenReturn(overview);
        when(dashboardMapper.countPendingPlot(anyString())).thenReturn(1);
        when(dashboardMapper.selectCurrentPlantingArea(anyString())).thenReturn(new BigDecimal("66.00"));
        when(dashboardMapper.selectCurrentExpectedYield(anyString())).thenReturn(new BigDecimal("5000"));

        // Arrange — 今日工作 6 格
        when(dashboardMapper.countTodayPlanting(anyString())).thenReturn(2);
        when(dashboardMapper.countTodayHarvest(anyString())).thenReturn(3);
        when(dashboardMapper.countTodayIdleMgmt(anyString())).thenReturn(1);
        when(dashboardMapper.countTodayPlantMgmt(anyString())).thenReturn(4);
        when(dashboardMapper.countTodayDisaster(anyString())).thenReturn(0);
        when(dashboardMapper.selectTodayPickActivityWeight(anyString())).thenReturn(new BigDecimal("88.50"));

        // Arrange — 当月完成率
        MonthCompletionItemVo mc = new MonthCompletionItemVo();
        mc.setCropName("番茄");
        mc.setActualYield(new BigDecimal("80"));
        mc.setExpectedYield(new BigDecimal("100"));
        when(dashboardMapper.selectMonthCompletion(anyString())).thenReturn(List.of(mc));

        // Arrange — 实时种植物统计
        CropPlantStatItemVo cs = new CropPlantStatItemVo();
        cs.setCropName("番茄");
        cs.setPlotCount(3);
        cs.setExpectedYield(new BigDecimal("2000"));
        when(dashboardMapper.selectCropPlantStat(anyString())).thenReturn(List.of(cs));

        // Arrange — 证书一览
        PlantDashboardMapper.CropCertLatestRow certRow = new PlantDashboardMapper.CropCertLatestRow();
        certRow.setCertDate(LocalDate.of(2027, 6, 30));
        certRow.setDaysToExpiry(380);
        when(dashboardMapper.selectLatestCropCert(anyString())).thenReturn(certRow);
        when(dashboardMapper.selectInLatestCertCropCount(anyString())).thenReturn(5);
        when(dashboardMapper.selectActiveCropCount(anyString())).thenReturn(8);

        // Act
        PlantDashboardSummaryVo vo = service.getSummary();

        // Assert — 土地总览
        assertThat(vo.getTotalPlotCount()).isEqualTo(10);
        assertThat(vo.getIdlePlotCount()).isEqualTo(4);
        assertThat(vo.getPlantingPlotCount()).isEqualTo(3);
        assertThat(vo.getHarvestingPlotCount()).isEqualTo(2);
        assertThat(vo.getPendingPlotCount()).isEqualTo(1);
        assertThat(vo.getTotalPlotArea()).isEqualByComparingTo("123.45");
        assertThat(vo.getCurrentPlantingArea()).isEqualByComparingTo("66.00");
        assertThat(vo.getCurrentExpectedYield()).isEqualByComparingTo("5000");

        // Assert — 今日工作 6 格
        assertThat(vo.getTodayPlantingPlotCount()).isEqualTo(2);
        assertThat(vo.getTodayHarvestPlotCount()).isEqualTo(3);
        assertThat(vo.getTodayIdleMgmtPlotCount()).isEqualTo(1);
        assertThat(vo.getTodayPlantMgmtPlotCount()).isEqualTo(4);
        assertThat(vo.getTodayDisasterPlotCount()).isEqualTo(0);
        assertThat(vo.getTodayPickActivityWeight()).isEqualByComparingTo("88.50");

        // Assert — 当月完成率
        assertThat(vo.getMonthCompletion()).hasSize(1);
        assertThat(vo.getMonthCompletion().get(0).getCropName()).isEqualTo("番茄");

        // Assert — 实时种植物统计
        assertThat(vo.getCropPlantStat()).hasSize(1);
        assertThat(vo.getCropPlantStat().get(0).getPlotCount()).isEqualTo(3);

        // Assert — 证书一览
        assertThat(vo.getOrganicCertOverview().getCropCertExpiryDate()).isEqualTo("2027-06-30");
        assertThat(vo.getOrganicCertOverview().getCropCertDaysToExpiry()).isEqualTo(380);
        // inCert=5，activeTotal=8 → noCert=max(8-5,0)=3，certCategory=5
        assertThat(vo.getOrganicCertOverview().getCropNoCertCount()).isEqualTo(3);
        assertThat(vo.getOrganicCertOverview().getCropCertCategoryCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("getSummary null 兜底：mapper 全返 null → 计数全 0 / ZERO / 空 list / 证书字段 null/0，不抛错")
    void getSummaryNullSafe() {
        // Arrange — 全 null
        when(dashboardMapper.selectPlotOverview(anyString())).thenReturn(null);
        when(dashboardMapper.countPendingPlot(anyString())).thenReturn(null);
        when(dashboardMapper.selectCurrentPlantingArea(anyString())).thenReturn(null);
        when(dashboardMapper.selectCurrentExpectedYield(anyString())).thenReturn(null);
        when(dashboardMapper.countTodayPlanting(anyString())).thenReturn(null);
        when(dashboardMapper.countTodayHarvest(anyString())).thenReturn(null);
        when(dashboardMapper.countTodayIdleMgmt(anyString())).thenReturn(null);
        when(dashboardMapper.countTodayPlantMgmt(anyString())).thenReturn(null);
        when(dashboardMapper.countTodayDisaster(anyString())).thenReturn(null);
        when(dashboardMapper.selectTodayPickActivityWeight(anyString())).thenReturn(null);
        when(dashboardMapper.selectMonthCompletion(anyString())).thenReturn(null);
        when(dashboardMapper.selectCropPlantStat(anyString())).thenReturn(null);
        when(dashboardMapper.selectLatestCropCert(anyString())).thenReturn(null);
        when(dashboardMapper.selectInLatestCertCropCount(anyString())).thenReturn(null);
        when(dashboardMapper.selectActiveCropCount(anyString())).thenReturn(null);

        // Act
        PlantDashboardSummaryVo vo = service.getSummary();

        // Assert — 土地总览全 0
        assertThat(vo.getTotalPlotCount()).isZero();
        assertThat(vo.getIdlePlotCount()).isZero();
        assertThat(vo.getPlantingPlotCount()).isZero();
        assertThat(vo.getHarvestingPlotCount()).isZero();
        assertThat(vo.getPendingPlotCount()).isZero();
        assertThat(vo.getTotalPlotArea()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getCurrentPlantingArea()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getCurrentExpectedYield()).isEqualByComparingTo(BigDecimal.ZERO);

        // Assert — 今日工作 6 格全 0 / ZERO
        assertThat(vo.getTodayPlantingPlotCount()).isZero();
        assertThat(vo.getTodayHarvestPlotCount()).isZero();
        assertThat(vo.getTodayIdleMgmtPlotCount()).isZero();
        assertThat(vo.getTodayPlantMgmtPlotCount()).isZero();
        assertThat(vo.getTodayDisasterPlotCount()).isZero();
        assertThat(vo.getTodayPickActivityWeight()).isEqualByComparingTo(BigDecimal.ZERO);

        // Assert — list 空
        assertThat(vo.getMonthCompletion()).isEmpty();
        assertThat(vo.getCropPlantStat()).isEmpty();

        // Assert — 证书一览：到期日/天数 null，计数全 0
        assertThat(vo.getOrganicCertOverview().getCropCertExpiryDate()).isNull();
        assertThat(vo.getOrganicCertOverview().getCropCertDaysToExpiry()).isNull();
        assertThat(vo.getOrganicCertOverview().getCropNoCertCount()).isZero();
        assertThat(vo.getOrganicCertOverview().getCropCertCategoryCount()).isZero();
    }

    // ======================== getGantt ========================

    @Test
    @DisplayName("getGantt 种植段 happy：PlantGanttCropRow 有起止日期 → plant 段按作物聚合 + 进度 completed/total=100")
    void getGanttPlantSegmentHappy() {
        // Arrange — 按作物聚合，cropId 为 snowflake（契约：不转 number 丢精度）
        PlantDashboardMapper.PlantGanttCropRow row = new PlantDashboardMapper.PlantGanttCropRow();
        row.setCropId(2058525064717926401L);
        row.setCropName("番茄");
        row.setStartDate(LocalDate.of(2026, 6, 1));
        row.setEndDate(LocalDate.of(2026, 6, 20));
        row.setTotalCount(3);
        row.setCompletedCount(3);
        when(dashboardMapper.selectPlantGanttByCrop(anyString())).thenReturn(List.of(row));
        when(dashboardMapper.selectPickGanttByCrop(anyString())).thenReturn(null);

        // Act
        List<GanttItemVo> items = service.getGantt();

        // Assert
        assertThat(items).hasSize(1);
        GanttItemVo plant = items.get(0);
        assertThat(plant.getType()).isEqualTo("plant");
        // id 拼接为 string，保留完整 snowflake cropId（契约：不转 number 丢精度）
        assertThat(plant.getId()).isEqualTo("2058525064717926401-plant");
        // y 轴类目 = 作物名（按作物聚合后不再含地块名）
        assertThat(plant.getText()).isEqualTo("番茄");
        // completed=3, total=3 → 100
        assertThat(plant.getProgress()).isEqualTo(100);
        assertThat(plant.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(plant.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 20));
    }

    @Test
    @DisplayName("getGantt 采摘段 happy：PickGanttCropRow → pick 段 + 进度按完成/总数比例")
    void getGanttPickSegmentHappy() {
        // Arrange
        PlantDashboardMapper.PickGanttCropRow row = new PlantDashboardMapper.PickGanttCropRow();
        row.setCropId(999L);
        row.setCropName("生菜");
        row.setBeginHarvestdate(LocalDate.of(2026, 6, 21));
        row.setEndHarvestdate(LocalDate.of(2026, 6, 30));
        row.setTotalCount(4);
        row.setCompletedCount(2);
        when(dashboardMapper.selectPlantGanttByCrop(anyString())).thenReturn(null);
        when(dashboardMapper.selectPickGanttByCrop(anyString())).thenReturn(List.of(row));

        // Act
        List<GanttItemVo> items = service.getGantt();

        // Assert
        assertThat(items).hasSize(1);
        GanttItemVo pick = items.get(0);
        assertThat(pick.getType()).isEqualTo("pick");
        assertThat(pick.getId()).isEqualTo("999-pick");
        assertThat(pick.getText()).isEqualTo("生菜");
        // pickRatioProgress: completed=2, total=4 → round(2*100/4)=50
        assertThat(pick.getProgress()).isEqualTo(50);
        assertThat(pick.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 21));
        assertThat(pick.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    @DisplayName("getGantt 空：两 mapper 均返 null → 空列表，不抛错")
    void getGanttEmpty() {
        when(dashboardMapper.selectPlantGanttByCrop(anyString())).thenReturn(null);
        when(dashboardMapper.selectPickGanttByCrop(anyString())).thenReturn(null);

        assertThat(service.getGantt()).isEmpty();
    }

    @Test
    @DisplayName("getGantt 种植段 endDate 兜底：endDate=null → 用 startDate 兜底，进度比例 1/2=50")
    void getGanttPlantEndDateFallback() {
        // Arrange — 整段未结束（endDate=null），用 startDate 兜底画出起点
        PlantDashboardMapper.PlantGanttCropRow row = new PlantDashboardMapper.PlantGanttCropRow();
        row.setCropId(100L);
        row.setCropName("黄瓜");
        row.setStartDate(LocalDate.of(2026, 6, 1));
        row.setEndDate(null);
        row.setTotalCount(2);
        row.setCompletedCount(1);
        when(dashboardMapper.selectPlantGanttByCrop(anyString())).thenReturn(List.of(row));
        when(dashboardMapper.selectPickGanttByCrop(anyString())).thenReturn(null);

        // Act
        List<GanttItemVo> items = service.getGantt();

        // Assert — endDate 兜底 = startDate，completed=1/total=2 → round(50)=50
        assertThat(items).hasSize(1);
        GanttItemVo item = items.get(0);
        assertThat(item.getType()).isEqualTo("plant");
        assertThat(item.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(item.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(item.getProgress()).isEqualTo(50);
    }

    @Test
    @DisplayName("getGantt 种植段名称为空 → 占位 '未知作物'，total=0 进度=0")
    void getGanttBlankNamePlaceholder() {
        // Arrange — cropName 空 + total 0
        PlantDashboardMapper.PlantGanttCropRow row = new PlantDashboardMapper.PlantGanttCropRow();
        row.setCropId(101L);
        row.setCropName(null);
        row.setStartDate(LocalDate.of(2026, 6, 1));
        row.setEndDate(LocalDate.of(2026, 6, 10));
        row.setTotalCount(0);
        row.setCompletedCount(0);
        when(dashboardMapper.selectPlantGanttByCrop(anyString())).thenReturn(List.of(row));
        when(dashboardMapper.selectPickGanttByCrop(anyString())).thenReturn(null);

        // Act
        List<GanttItemVo> items = service.getGantt();

        // Assert
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getText()).isEqualTo("未知作物");
        assertThat(items.get(0).getProgress()).isZero();
    }

}
