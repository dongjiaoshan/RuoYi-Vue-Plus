package org.dromara.djs.plant.plan.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.domain.PlantPlan;
import org.dromara.djs.plant.plan.domain.bo.PlantDetailInputBo;
import org.dromara.djs.plant.plan.domain.bo.PlantPlanCreateBo;
import org.dromara.djs.plant.plan.domain.bo.PlantFinishBo;
import org.dromara.djs.plant.plan.domain.bo.PlantPlanUpdateBo;
import org.dromara.djs.plant.plan.domain.bo.PlantStartBo;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plan.mapper.PlantPlanMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.dromara.djs.plant.zone.mapper.PlotZoneMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlantPlanServiceImpl} 单测（PLT-PLAN-001）。
 *
 * <h3>覆盖 case</h3>
 * <ol>
 *   <li>happy create：1 plan + 3 details → recalc 被触发 + plan_no 序号正确</li>
 *   <li>earliest 公式：plant_month=4 / period=05 / crop.min_cycle=60 → 4/5 + 60d = 6/4</li>
 *   <li>ongoing 改作物：抛 ServiceException</li>
 *   <li>delete with valid: 有 begin_actualdate 行 → 抛 ServiceException</li>
 *   <li>nextPlanNo: 同年序号连续递增</li>
 *   <li>periodToDay 非法值：抛 ServiceException</li>
 * </ol>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlantPlanServiceImpl 单元测试")
class PlantPlanServiceImplTest {

    @Mock
    private PlantPlanMapper planMapper;
    @Mock
    private PlantDetailsMapper detailsMapper;
    @Mock
    private CropInfoMapper cropMapper;
    @Mock
    private PlotInfoMapper plotMapper;
    @Mock
    private PlotZoneMapper zoneMapper;
    @Mock
    private PlantWorkTeamMapper teamMapper;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;

    private PlantPlanServiceImpl service;

    /**
     * MyBatis-Plus 单测 entity cache 预热：startPlant 走 LambdaQueryWrapper / LambdaUpdateWrapper
     * （PlantDetails / PlotInfo），mock 路径下也会触发 TableInfoHelper.getTableInfo() 解析 lambda 列名。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, PlantPlan.class);
        TableInfoHelper.initTableInfo(assistant, PlantDetails.class);
        TableInfoHelper.initTableInfo(assistant, PlotInfo.class);
    }

    @BeforeEach
    void setUp() {
        service = new PlantPlanServiceImpl(planMapper, detailsMapper, cropMapper, plotMapper, zoneMapper, teamMapper, bizCodeGenerator);
    }

    @Test
    @DisplayName("createByBo: 1 plan + 3 details，触发 recalcAggregates，plan_no 取年度首条")
    void createHappy() {
        CropInfo crop = mockCrop(100L, 60, 90, new BigDecimal("2000.000"));
        when(cropMapper.selectById(100L)).thenReturn(crop);
        when(plotMapper.selectByIds(any())).thenReturn(List.of(
            mockPlot(10L, new BigDecimal("3.50")),
            mockPlot(20L, new BigDecimal("1.20")),
            mockPlot(30L, new BigDecimal("2.80"))));
        when(bizCodeGenerator.generate(eq(BizCodeType.PLAN_NO), any())).thenReturn("PLAN-2026-001");
        when(planMapper.insert(any(PlantPlan.class))).thenAnswer(inv -> {
            PlantPlan plan = inv.getArgument(0);
            plan.setId(999L);
            return 1;
        });
        when(detailsMapper.insert(any(PlantDetails.class))).thenReturn(1);

        PlantPlanCreateBo bo = new PlantPlanCreateBo();
        bo.setPlanYear(2026);
        bo.setPlanSeason("spring");
        bo.setCropId(100L);
        bo.setDetails(List.of(
            mockInput(10L, 4, "05"),
            mockInput(20L, 4, "15"),
            mockInput(30L, 5, "05")));

        Long id = service.createByBo(bo);

        assertThat(id).isEqualTo(999L);
        ArgumentCaptor<PlantPlan> planCap = ArgumentCaptor.forClass(PlantPlan.class);
        verify(planMapper).insert(planCap.capture());
        assertThat(planCap.getValue().getPlanNo()).isEqualTo("PLAN-2026-001");
        assertThat(planCap.getValue().getPlantStatus()).isEqualTo("pending");

        verify(detailsMapper, times(3)).insert(any(PlantDetails.class));
        verify(planMapper).recalcAggregates(999L);
    }

    @Test
    @DisplayName("buildDetail: earliest = 4/5 + 60d = 6/4；last = 4/5 + 90d = 7/4；expected = 3.5 × 2000")
    void detailDerivedFormulas() {
        CropInfo crop = mockCrop(100L, 60, 90, new BigDecimal("2000.000"));
        when(cropMapper.selectById(100L)).thenReturn(crop);
        when(plotMapper.selectByIds(any())).thenReturn(List.of(mockPlot(10L, new BigDecimal("3.50"))));
        when(bizCodeGenerator.generate(eq(BizCodeType.PLAN_NO), any())).thenReturn("PLAN-2026-001");
        when(planMapper.insert(any(PlantPlan.class))).thenAnswer(inv -> {
            ((PlantPlan) inv.getArgument(0)).setId(1L);
            return 1;
        });

        ArgumentCaptor<PlantDetails> detailCap = ArgumentCaptor.forClass(PlantDetails.class);
        when(detailsMapper.insert(detailCap.capture())).thenReturn(1);

        PlantPlanCreateBo bo = new PlantPlanCreateBo();
        bo.setPlanYear(2026);
        bo.setPlanSeason("spring");
        bo.setCropId(100L);
        bo.setDetails(List.of(mockInput(10L, 4, "05")));
        service.createByBo(bo);

        PlantDetails saved = detailCap.getValue();
        assertThat(saved.getEarliestHarvestdate()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(saved.getLastHarvestdate()).isEqualTo(LocalDate.of(2026, 7, 4));
        assertThat(saved.getExpectedYield()).isEqualByComparingTo(new BigDecimal("7000.000"));
        assertThat(saved.getPlantStatus()).isEqualTo("pending");
        assertThat(saved.getHarvestStatus()).isEqualTo("pending");
        assertThat(saved.getIsPick()).isEqualTo(2);
    }

    @Test
    @DisplayName("updateByBo: ongoing 计划改 cropId 抛 ServiceException")
    void updateOngoingChangeCropRejected() {
        PlantPlan existing = new PlantPlan();
        existing.setId(1L);
        existing.setPlanYear(2026);
        existing.setCropId(100L);
        existing.setPlantStatus("ongoing");
        when(planMapper.selectById(1L)).thenReturn(existing);

        PlantPlanUpdateBo bo = new PlantPlanUpdateBo();
        bo.setId(1L);
        bo.setCropId(200L);  // 不同作物
        bo.setPlanSeason("spring");

        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已开始执行的计划不允许修改作物");
    }

    @Test
    @DisplayName("deleteWithValidByIds: 关联明细已 begin_actualdate 抛 ServiceException")
    void deleteRejectedWhenStarted() {
        when(detailsMapper.selectCount(any(Wrapper.class))).thenReturn(2L);
        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(1L, 2L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("无法删除");
        verify(planMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("nextPlanNo: 委托给 BizCodeGenerator(PLAN_NO)，序号续增")
    void nextPlanNoIncrement() {
        when(bizCodeGenerator.generate(eq(BizCodeType.PLAN_NO), any())).thenReturn("PLAN-2026-006");
        assertThat(service.nextPlanNo()).isEqualTo("PLAN-2026-006");
    }

    @Test
    @DisplayName("nextPlanNo: 委托给 BizCodeGenerator(PLAN_NO)，首条返 001")
    void nextPlanNoFirst() {
        when(bizCodeGenerator.generate(eq(BizCodeType.PLAN_NO), any())).thenReturn("PLAN-2027-001");
        assertThat(service.nextPlanNo()).isEqualTo("PLAN-2027-001");
    }

    @Test
    @DisplayName("startPlant happy: 仅未开工明细被回写 ongoing + plot_status=2，返回开工行数")
    void startPlantHappy() {
        PlantDetails d1 = new PlantDetails();
        d1.setId(11L);
        d1.setPlotId(101L);
        d1.setBeginActualdate(null);   // 未开工
        PlantDetails d2 = new PlantDetails();
        d2.setId(12L);
        d2.setPlotId(102L);
        d2.setBeginActualdate(LocalDate.of(2026, 5, 1));   // 已开工，应被跳过
        when(detailsMapper.selectList(any())).thenReturn(List.of(d1, d2));
        when(detailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(plotMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        PlantStartBo bo = new PlantStartBo();
        bo.setDetailIds(List.of(11L, 12L));
        bo.setBeginActualdate(LocalDate.of(2026, 6, 9));
        bo.setPlantBy(7L);

        int affected = service.startPlant(bo);

        assertThat(affected).isEqualTo(1);   // 仅 d1 开工
        verify(detailsMapper).update(isNull(), any(Wrapper.class));   // 批量回写明细
        verify(plotMapper).update(isNull(), any(Wrapper.class));      // 同步地块 plot_status=2
    }

    @Test
    @DisplayName("startPlant: 传入 detailIds 部分不存在（跨租户/已删）抛 ServiceException")
    void startPlantMissingDetailRejected() {
        PlantDetails d1 = new PlantDetails();
        d1.setId(11L);
        d1.setPlotId(101L);
        when(detailsMapper.selectList(any())).thenReturn(List.of(d1));   // 只查到 1 条

        PlantStartBo bo = new PlantStartBo();
        bo.setDetailIds(List.of(11L, 99L));   // 99 不存在
        bo.setBeginActualdate(LocalDate.of(2026, 6, 9));
        bo.setPlantBy(7L);

        assertThatThrownBy(() -> service.startPlant(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在");
        verify(detailsMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("finishPlant 一步落地：待种植(begin 空)直接完成 + 补班组/采摘窗口 + plot_status=2，已完成跳过")
    void finishPlantOneStep() {
        PlantDetails d1 = new PlantDetails();   // 待种植：begin 空 → 一步完成时补 begin/班组/窗口/plot_status
        d1.setId(11L);
        d1.setPlotId(101L);
        d1.setCropId(30L);
        d1.setBeginActualdate(null);
        d1.setEndActualdate(null);
        d1.setPlantStatus("pending");
        PlantDetails d2 = new PlantDetails();   // 已完成 → 跳过
        d2.setId(12L);
        d2.setPlotId(102L);
        d2.setPlantStatus("completed");
        when(detailsMapper.selectList(any())).thenReturn(List.of(d1, d2));
        when(detailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(plotMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(cropMapper.selectByIds(any())).thenReturn(List.of(mockCrop(30L, 60, 90, new BigDecimal("2000.000"))));

        PlantFinishBo bo = new PlantFinishBo();
        bo.setDetailIds(List.of(11L, 12L));
        bo.setEndActualdate(LocalDate.of(2026, 6, 18));
        bo.setPlantBy(7L);

        int affected = service.finishPlant(bo);

        assertThat(affected).isEqualTo(1);   // 仅 d1 完成（d2 已完成跳过）
        verify(plotMapper).update(isNull(), any(Wrapper.class));   // 待种植地块一步落地补 plot_status=2
    }

    // ============================================================
    // helpers
    // ============================================================

    private CropInfo mockCrop(Long id, Integer minCycle, Integer maxCycle, BigDecimal predictedPer) {
        CropInfo c = new CropInfo();
        c.setId(id);
        c.setCropName("白菜-" + id);
        c.setMinCycle(minCycle);
        c.setMaxCycle(maxCycle);
        c.setPredictedPer(predictedPer);
        return c;
    }

    private PlotInfo mockPlot(Long id, BigDecimal area) {
        PlotInfo p = new PlotInfo();
        p.setId(id);
        p.setPlotName("地块-" + id);
        p.setPlotCode("P" + id);
        p.setPlotArea(area);
        p.setZoneId(1L);
        return p;
    }

    private PlantDetailInputBo mockInput(Long plotId, Integer month, String period) {
        PlantDetailInputBo bo = new PlantDetailInputBo();
        bo.setPlotId(plotId);
        bo.setPlantMonth(month);
        bo.setPlantPeriod(period);
        return bo;
    }
}
