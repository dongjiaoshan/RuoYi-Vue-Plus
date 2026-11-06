package org.dromara.djs.plant.plot.service.impl;

import org.dromara.common.core.service.DictService;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.farm.mapper.FarmRecordsTeamMapper;
import org.dromara.djs.plant.plot.domain.vo.PlotPlantingStatVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotCardVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotFarmworkVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotPlantingVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotSummaryVo;
import org.dromara.djs.plant.plot.mapper.AppletPlotQueryMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link AppletPlotQueryServiceImpl} 单测（V7-R31 / V7-R32）：当前茬回填、空闲不回填、
 * 「最高」徽章与亩产规则、空闲农事作物置空 + 班组多选拼接。
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppletPlotQueryServiceImplTest {

    @Mock
    private AppletPlotQueryMapper plotQueryMapper;
    @Mock
    private FarmRecordsTeamMapper farmRecordsTeamMapper;
    @Mock
    private ImageUrlResolver imageUrlResolver;
    @Mock
    private DictService dictService;

    @InjectMocks
    private AppletPlotQueryServiceImpl service;

    private static AppletPlotCardVo plot(long id, int status) {
        AppletPlotCardVo p = new AppletPlotCardVo();
        p.setId(id);
        p.setPlotCode("P" + id);
        p.setPlotStatus(status);
        p.setPlotArea(new BigDecimal("1.2"));
        return p;
    }

    @Test
    @DisplayName("listPlots：种植地块取首个候选作当前茬，空闲地块不回填当前茬但带历史统计")
    void listPlotsFillsCurrentAndStats() {
        List<AppletPlotCardVo> plots = new ArrayList<>(List.of(plot(1L, 2), plot(2L, 1)));
        when(plotQueryMapper.selectPlots(eq("A1"), eq(null))).thenReturn(plots);

        AppletPlotCardVo latest = new AppletPlotCardVo();
        latest.setId(1L);
        latest.setCropName("红薯");
        latest.setPlantDate(LocalDate.of(2026, 7, 10));
        latest.setExpectedYield(new BigDecimal("800"));
        AppletPlotCardVo older = new AppletPlotCardVo();
        older.setId(1L);
        older.setCropName("青菜");
        AppletPlotCardVo idleLeft = new AppletPlotCardVo();
        idleLeft.setId(2L);
        idleLeft.setCropName("黄瓜");
        when(plotQueryMapper.selectCurrentCandidates(anyList())).thenReturn(List.of(latest, older, idleLeft));

        PlotPlantingStatVo s1 = new PlotPlantingStatVo();
        s1.setPlotId(1L);
        s1.setHistoryPlantCount(3L);
        s1.setMaxYieldCropName("青菜");
        s1.setMaxYieldPerMu(new BigDecimal("520.12345"));
        when(plotQueryMapper.selectStartedStats(anyList())).thenReturn(List.of(s1));

        List<AppletPlotCardVo> out = service.listPlots("  A1 ", null);

        assertThat(out.get(0).getCropName()).isEqualTo("红薯");
        assertThat(out.get(0).getPlantDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(out.get(0).getExpectedYield()).isEqualByComparingTo("800");
        assertThat(out.get(0).getHistoryPlantCount()).isEqualTo(3L);
        assertThat(out.get(0).getMaxYieldPerMu()).isEqualByComparingTo("520.12");
        assertThat(out.get(1).getCropName()).isNull();
        assertThat(out.get(1).getHistoryPlantCount()).isZero();
    }

    @Test
    @DisplayName("listPlantings：只有采摘完成行参与评比，亩产最高者 top=true")
    void listPlantingsMarksTop() {
        AppletPlotPlantingVo ongoing = planting(30L, "ongoing", "9999", "1");
        AppletPlotPlantingVo high = planting(20L, "completed", "780", "1.2");
        AppletPlotPlantingVo low = planting(10L, "completed", "310", "1.2");
        when(plotQueryMapper.selectPlantings(5L)).thenReturn(new ArrayList<>(List.of(ongoing, high, low)));
        when(imageUrlResolver.resolveList(any())).thenReturn(List.of("u1", "u2", "u3"));

        List<AppletPlotPlantingVo> out = service.listPlantings(5L);

        assertThat(out).extracting(AppletPlotPlantingVo::getTop).containsExactly(false, true, false);
        assertThat(out).extracting(AppletPlotPlantingVo::getCropImage).containsExactly("u1", "u2", "u3");
    }

    @Test
    @DisplayName("listFarmworks：空闲农事作物置空，多选班组拼接，类型译中文")
    void listFarmworksTeamsAndIdleCrop() {
        AppletPlotFarmworkVo idle = new AppletPlotFarmworkVo();
        idle.setId(1L);
        idle.setPlotType(1);
        idle.setCropId(9L);
        idle.setCropName("残留");
        idle.setFarmType("tillage_break");
        idle.setFarmByName("一组");
        AppletPlotFarmworkVo grow = new AppletPlotFarmworkVo();
        grow.setId(2L);
        grow.setPlotType(2);
        grow.setCropId(8L);
        grow.setCropName("红薯");
        grow.setFarmType("weed");
        when(plotQueryMapper.selectFarmworks(5L)).thenReturn(List.of(idle, grow));
        when(farmRecordsTeamMapper.selectTeamNamesByRecordIds(anyList())).thenReturn(List.of(
            Map.of("recordId", 2L, "teamName", "一组"),
            Map.of("recordId", 2L, "teamName", "二组")));
        when(dictService.getDictLabel("djs_farm_work_type", "weed")).thenReturn("除草");
        when(dictService.getDictLabel("djs_farm_work_type", "tillage_break")).thenReturn("翻耕");

        List<AppletPlotFarmworkVo> out = service.listFarmworks(5L);

        assertThat(out.get(0).getCropName()).isNull();
        assertThat(out.get(0).getCropId()).isNull();
        assertThat(out.get(0).getTeamNames()).isEqualTo("一组");
        assertThat(out.get(1).getTeamNames()).isEqualTo("一组,二组");
        assertThat(out.get(1).getFarmTypeLabel()).isEqualTo("除草");
    }

    @Test
    @DisplayName("summary：名称与状态都透传给统计 SQL（与 list 同一归一规则：trim / 空白=不过滤）")
    void summaryPassesSameFiltersAsList() {
        AppletPlotSummaryVo onlyPicking = new AppletPlotSummaryVo();
        onlyPicking.setPlantingCount(0L);
        onlyPicking.setPickingCount(4L);
        onlyPicking.setIdleCount(0L);
        when(plotQueryMapper.selectSummary(eq("A1"), eq(3))).thenReturn(onlyPicking);

        AppletPlotSummaryVo out = service.summary("  A1 ", 3);

        assertThat(out.getPickingCount()).isEqualTo(4L);
        assertThat(out.getPlantingCount()).isZero();
        assertThat(out.getIdleCount()).isZero();
    }

    @Test
    @DisplayName("summary：空白名称不过滤；SQL 返 null / 空计数时三项兜底为 0")
    void summaryBlankKeywordAndNullCountsDefaultZero() {
        AppletPlotSummaryVo partial = new AppletPlotSummaryVo();
        partial.setPlantingCount(7L);
        when(plotQueryMapper.selectSummary(eq(null), eq(null))).thenReturn(partial);

        AppletPlotSummaryVo out = service.summary("   ", null);
        assertThat(out.getPlantingCount()).isEqualTo(7L);
        assertThat(out.getPickingCount()).isZero();
        assertThat(out.getIdleCount()).isZero();

        when(plotQueryMapper.selectSummary(eq("无"), eq(1))).thenReturn(null);
        AppletPlotSummaryVo none = service.summary("无", 1);
        assertThat(none.getPlantingCount()).isZero();
        assertThat(none.getPickingCount()).isZero();
        assertThat(none.getIdleCount()).isZero();
    }

    private static AppletPlotPlantingVo planting(long id, String harvestStatus, String actual, String area) {
        AppletPlotPlantingVo v = new AppletPlotPlantingVo();
        v.setId(id);
        v.setHarvestStatus(harvestStatus);
        v.setActualYield(new BigDecimal(actual));
        v.setPlotArea(new BigDecimal(area));
        return v;
    }
}
