package org.dromara.djs.plant.overview.service.impl;

import org.dromara.djs.plant.overview.domain.vo.CropOverviewCardVo;
import org.dromara.djs.plant.overview.domain.vo.CropOverviewExportVo;
import org.dromara.djs.plant.overview.mapper.PlantOverviewMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlantOverviewServiceImpl} 作物卡片搜索 + 导出单测（row147）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>getCropCardExportList happy：9 列逐一映射 + 完成率 25.00 / 100.00 + 升序排列</li>
 *   <li>getCropCardExportList 边界：计划地块 0 → 完成率 0.00 不抛除零；mapper 返 null → 空列表</li>
 *   <li>关键字归一：前后空白被 trim；null 传给 mapper 的是空串（裸 @Select 不接受 null）</li>
 *   <li>getSummary 透传关键字给卡片查询</li>
 * </ul>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlantOverviewServiceImpl 作物卡片搜索/导出单元测试")
class PlantOverviewServiceImplTest {

    @Mock
    private PlantOverviewMapper overviewMapper;

    @InjectMocks
    private PlantOverviewServiceImpl service;

    private CropOverviewCardVo card(String name, String code, int planPlot, int donePlot) {
        CropOverviewCardVo c = new CropOverviewCardVo();
        c.setCropId(1L);
        c.setCropName(name);
        c.setCropCode(code);
        c.setPlanPlotCount(planPlot);
        c.setPlanArea(new BigDecimal("4.74"));
        c.setPlanExpectedYield(new BigDecimal("2844.000"));
        c.setDonePlotCount(donePlot);
        c.setDoneArea(new BigDecimal("1.06"));
        c.setDoneHarvestYield(new BigDecimal("374.000"));
        return c;
    }

    @Test
    @DisplayName("getCropCardExportList happy：9 列映射正确 + 完成率升序（25.00 在 100.00 前）")
    void testExportHappyPath() {
        CropOverviewCardVo a = card("大白菜秧", "C024", 4, 1);
        CropOverviewCardVo b = card("上海青", "C001", 2, 2);
        when(overviewMapper.selectCropCards(anyString(), anyString())).thenReturn(List.of(a, b));

        List<CropOverviewExportVo> rows = service.getCropCardExportList(null);

        assertThat(rows).hasSize(2);
        // 升序：25.00 排在 100.00 前
        assertThat(rows.get(0).getCropName()).isEqualTo("大白菜秧");
        assertThat(rows.get(0).getCompletionRate()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(rows.get(1).getCropName()).isEqualTo("上海青");
        assertThat(rows.get(1).getCompletionRate()).isEqualByComparingTo(new BigDecimal("100.00"));

        CropOverviewExportVo first = rows.get(0);
        assertThat(first.getCropCode()).isEqualTo("C024");
        assertThat(first.getPlanPlotCount()).isEqualTo(4);
        assertThat(first.getPlanArea()).isEqualByComparingTo(new BigDecimal("4.74"));
        assertThat(first.getPlanExpectedYield()).isEqualByComparingTo(new BigDecimal("2844.000"));
        assertThat(first.getDonePlotCount()).isEqualTo(1);
        assertThat(first.getDoneArea()).isEqualByComparingTo(new BigDecimal("1.06"));
        assertThat(first.getDoneHarvestYield()).isEqualByComparingTo(new BigDecimal("374.000"));
    }

    @Test
    @DisplayName("getCropCardExportList 边界：计划地块 0 → 完成率 0.00；mapper 返 null → 空列表")
    void testExportEdgeCases() {
        CropOverviewCardVo zero = card("空计划作物", "C999", 0, 0);
        zero.setPlanArea(null);
        zero.setPlanExpectedYield(null);
        zero.setDoneArea(null);
        zero.setDoneHarvestYield(null);
        when(overviewMapper.selectCropCards(anyString(), anyString())).thenReturn(List.of(zero));

        List<CropOverviewExportVo> rows = service.getCropCardExportList("");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCompletionRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rows.get(0).getPlanArea()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rows.get(0).getDoneHarvestYield()).isEqualByComparingTo(BigDecimal.ZERO);

        when(overviewMapper.selectCropCards(anyString(), anyString())).thenReturn(null);
        assertThat(service.getCropCardExportList(null)).isEmpty();
    }

    @Test
    @DisplayName("关键字归一：前后空白 trim；null → 空串（裸 @Select 不接受 null）")
    void testKeywordNormalize() {
        when(overviewMapper.selectCropCards(anyString(), anyString())).thenReturn(List.of());

        service.getCropCardExportList("  上海青  ");
        service.getCropCardExportList(null);
        service.getCropCardExportList("   ");

        ArgumentCaptor<String> kw = ArgumentCaptor.forClass(String.class);
        verify(overviewMapper, times(3)).selectCropCards(anyString(), kw.capture());
        assertThat(kw.getAllValues()).containsExactly("上海青", "", "");
    }

    @Test
    @DisplayName("getSummary：关键字透传到卡片查询，KPI 查询不受影响")
    void testSummaryPassesKeyword() {
        when(overviewMapper.selectPlotCount(anyString())).thenReturn(null);
        when(overviewMapper.selectYieldSum(anyString())).thenReturn(null);
        when(overviewMapper.selectCropCards(anyString(), anyString())).thenReturn(List.of(card("丝瓜", "C044", 3, 3)));

        var vo = service.getSummary(" 丝瓜 ");

        assertThat(vo.getIdlePlotCount()).isZero();
        assertThat(vo.getCrops()).hasSize(1);
        verify(overviewMapper).selectCropCards(anyString(), org.mockito.ArgumentMatchers.eq("丝瓜"));
    }
}
