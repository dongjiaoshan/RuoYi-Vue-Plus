package org.dromara.djs.plant.pick.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.farm.domain.bo.GrowRecordBo;
import org.dromara.djs.plant.farm.service.IFarmRecordsService;
import org.dromara.djs.plant.pick.domain.bo.PickSubmitBo;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plan.mapper.PlantPlanMapper;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AppletPickServiceImpl} 采收录入单测（PLT-PICK-001）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>happy path（finish=false）：actual_yield 累加 + begin_harvestdate 回填 + harvest_status pending→picking + 不动 is_pick + INSERT 农事记录</li>
 *   <li>finish=true：harvest_status→completed + end_actualdate + average_yield 计算</li>
 *   <li>明细不存在 → ServiceException</li>
 *   <li>未指派班组（harvest_by=null）→ ServiceException（farm_records 追溯前置）</li>
 * </ul>
 *
 * @author djs
 * @since PLT-PICK-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppletPickServiceImpl 采收录入单元测试")
class AppletPickServiceImplTest {

    @Mock
    private PlantDetailsMapper detailsMapper;
    @Mock
    private PlantPlanMapper planMapper;
    @Mock
    private PlotInfoMapper plotMapper;
    @Mock
    private CropInfoMapper cropMapper;
    @Mock
    private PlantWorkTeamMapper teamMapper;
    @Mock
    private IFarmRecordsService farmRecordsService;

    private AppletPickServiceImpl service;

    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, PlantDetails.class);
    }

    @BeforeEach
    void setUp() {
        service = new AppletPickServiceImpl(detailsMapper, planMapper, plotMapper, cropMapper, teamMapper, farmRecordsService);
    }

    private PlantDetails detailFixture() {
        PlantDetails d = new PlantDetails();
        d.setId(100L);
        d.setPlantId(10L);
        d.setPlotId(20L);
        d.setCropId(30L);
        d.setHarvestBy(40L);
        d.setHarvestStatus("pending");
        d.setIsPick(2);
        d.setPlotArea(new BigDecimal("10.00"));
        return d;
    }

    @Test
    @DisplayName("采收提交 happy(finish=false)：actual_yield 累加 + begin 回填 + pending→picking + 不动 is_pick + INSERT 农事")
    void submitPick_continue_happy() {
        PlantDetails d = detailFixture();
        when(detailsMapper.selectById(100L)).thenReturn(d);

        PickSubmitBo bo = new PickSubmitBo();
        bo.setDetailId(100L);
        bo.setWeight(new BigDecimal("50"));
        bo.setHarvestDate(LocalDate.of(2026, 6, 1));
        bo.setFinish(false);

        service.submitPick(bo);

        ArgumentCaptor<PlantDetails> cap = ArgumentCaptor.forClass(PlantDetails.class);
        verify(detailsMapper).updateById(cap.capture());
        PlantDetails saved = cap.getValue();
        assertThat(saved.getActualYield()).isEqualByComparingTo("50");
        assertThat(saved.getBeginHarvestdate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(saved.getHarvestStatus()).isEqualTo("picking");
        assertThat(saved.getEndActualdate()).isNull();
        assertThat(saved.getIsPick()).isEqualTo(2);   // 决策①：不动 is_pick

        ArgumentCaptor<GrowRecordBo> grow = ArgumentCaptor.forClass(GrowRecordBo.class);
        verify(farmRecordsService).submitGrow(grow.capture());
        assertThat(grow.getValue().getFarmType()).isEqualTo("harvest_activity");
        assertThat(grow.getValue().getFarmBy()).isEqualTo(40L);
    }

    @Test
    @DisplayName("二次采收累加：原 30 + 本次 20 = 50")
    void submitPick_accumulate() {
        PlantDetails d = detailFixture();
        d.setActualYield(new BigDecimal("30"));
        d.setHarvestStatus("picking");
        d.setBeginHarvestdate(LocalDate.of(2026, 5, 30));
        when(detailsMapper.selectById(100L)).thenReturn(d);

        PickSubmitBo bo = new PickSubmitBo();
        bo.setDetailId(100L);
        bo.setWeight(new BigDecimal("20"));
        bo.setHarvestDate(LocalDate.of(2026, 6, 1));

        service.submitPick(bo);

        ArgumentCaptor<PlantDetails> cap = ArgumentCaptor.forClass(PlantDetails.class);
        verify(detailsMapper).updateById(cap.capture());
        assertThat(cap.getValue().getActualYield()).isEqualByComparingTo("50");
        // begin 已存在不覆盖
        assertThat(cap.getValue().getBeginHarvestdate()).isEqualTo(LocalDate.of(2026, 5, 30));
    }

    @Test
    @DisplayName("完成采收(finish=true)：completed + end_actualdate=今日 + average_yield=actual/plot_area")
    void submitPick_finish() {
        PlantDetails d = detailFixture();
        when(detailsMapper.selectById(100L)).thenReturn(d);

        PickSubmitBo bo = new PickSubmitBo();
        bo.setDetailId(100L);
        bo.setWeight(new BigDecimal("100"));
        bo.setHarvestDate(LocalDate.of(2026, 6, 1));
        bo.setFinish(true);

        service.submitPick(bo);

        ArgumentCaptor<PlantDetails> cap = ArgumentCaptor.forClass(PlantDetails.class);
        verify(detailsMapper).updateById(cap.capture());
        PlantDetails saved = cap.getValue();
        assertThat(saved.getHarvestStatus()).isEqualTo("completed");
        assertThat(saved.getEndActualdate()).isEqualTo(LocalDate.now());
        assertThat(saved.getEndHarvestdate()).isEqualTo(LocalDate.of(2026, 6, 1));
        // 100 / 10.00 = 10.000
        assertThat(saved.getAverageYield()).isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("明细不存在 → ServiceException，不 INSERT 农事")
    void submitPick_detail_not_found() {
        when(detailsMapper.selectById(999L)).thenReturn(null);
        PickSubmitBo bo = new PickSubmitBo();
        bo.setDetailId(999L);
        bo.setWeight(new BigDecimal("10"));
        bo.setHarvestDate(LocalDate.now());

        assertThatThrownBy(() -> service.submitPick(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("采摘明细不存在");
        verify(farmRecordsService, never()).submitGrow(any());
    }

    @Test
    @DisplayName("未指派采摘班组(harvest_by=null) → ServiceException")
    void submitPick_no_team() {
        PlantDetails d = detailFixture();
        d.setHarvestBy(null);
        when(detailsMapper.selectById(100L)).thenReturn(d);

        PickSubmitBo bo = new PickSubmitBo();
        bo.setDetailId(100L);
        bo.setWeight(new BigDecimal("50"));
        bo.setHarvestDate(LocalDate.now());

        assertThatThrownBy(() -> service.submitPick(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("未指派采摘班组");
        verify(farmRecordsService, never()).submitGrow(any());
    }
}
