package org.dromara.djs.plant.pick.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.farm.domain.bo.GrowRecordBo;
import org.dromara.djs.plant.farm.service.IFarmRecordsService;
import org.dromara.djs.plant.pick.domain.bo.PickSubmitBo;
import org.dromara.djs.plant.pick.event.PlantPickedEvent;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plan.mapper.PlantPlanMapper;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.mapper.PlantWorkPeopleMapper;
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
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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
    private PlantWorkPeopleMapper peopleMapper;
    @Mock
    private IFarmRecordsService farmRecordsService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ImageUrlResolver imageUrlResolver;
    @Mock
    private org.dromara.djs.plant.team.service.PlantTeamLinkService teamLinkService;
    @Mock
    private org.dromara.djs.plant.activity.service.IPlantActivityService plantActivityService;

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
        service = new AppletPickServiceImpl(detailsMapper, planMapper, plotMapper, cropMapper, teamMapper, peopleMapper, farmRecordsService, eventPublisher, imageUrlResolver, teamLinkService, plantActivityService);
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
    @DisplayName("采收提交 happy(finish=false)：begin 回填 + pending→picking + 不动 is_pick + 无重量 + INSERT 农事")
    void submitPick_continue_happy() {
        PlantDetails d = detailFixture();
        when(detailsMapper.selectById(100L)).thenReturn(d);
        // SM-4：pending→picking 乐观 UPDATE 成功（affected=1 = 本次赢得首次激活）
        when(detailsMapper.update(isNull(), any())).thenReturn(1);

        PickSubmitBo bo = new PickSubmitBo();
        bo.setDetailId(100L);
        bo.setHarvestDate(LocalDate.of(2026, 6, 1));
        bo.setFinish(false);
        bo.setTeamId(55L);

        service.submitPick(bo);

        ArgumentCaptor<PlantDetails> cap = ArgumentCaptor.forClass(PlantDetails.class);
        verify(detailsMapper).updateById(cap.capture());
        PlantDetails saved = cap.getValue();
        // #3=a：采收 tab 不录重量，actual_yield 不被采收流程改动
        assertThat(saved.getActualYield()).isNull();
        assertThat(saved.getBeginHarvestdate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(saved.getHarvestStatus()).isEqualTo("picking");
        assertThat(saved.getEndActualdate()).isNull();
        assertThat(saved.getIsPick()).isEqualTo(2);   // 决策①：不动 is_pick

        ArgumentCaptor<GrowRecordBo> grow = ArgumentCaptor.forClass(GrowRecordBo.class);
        verify(farmRecordsService).submitGrow(grow.capture());
        // is_pick=2 普通采收 → farm_type='harvest'（采收）；is_pick=1 才是 harvest_activity（FIX-PLT-AD-PICK-FARMTYPE-001）
        assertThat(grow.getValue().getFarmType()).isEqualTo("harvest");
        // 采收按班组记录 → farm_by 落采收班组；operator_user_id 留空
        assertThat(grow.getValue().getFarmBy()).isEqualTo(55L);
        assertThat(grow.getValue().getOperatorUserId()).isNull();
    }

    @Test
    @DisplayName("二次采收：begin 已存在不覆盖，采收流程不动 actual_yield")
    void submitPick_continue_keeps_begin() {
        PlantDetails d = detailFixture();
        d.setActualYield(new BigDecimal("30"));
        d.setHarvestStatus("picking");
        d.setBeginHarvestdate(LocalDate.of(2026, 5, 30));
        when(detailsMapper.selectById(100L)).thenReturn(d);
        // SM-4：行已 picking，pending→target 乐观 UPDATE 命不中（affected=0，非首次激活）
        when(detailsMapper.update(isNull(), any())).thenReturn(0);

        PickSubmitBo bo = new PickSubmitBo();
        bo.setDetailId(100L);
        bo.setHarvestDate(LocalDate.of(2026, 6, 1));

        service.submitPick(bo);

        ArgumentCaptor<PlantDetails> cap = ArgumentCaptor.forClass(PlantDetails.class);
        verify(detailsMapper).updateById(cap.capture());
        // 采收流程不改 actual_yield（仍为采摘活动管理累加的 30）
        assertThat(cap.getValue().getActualYield()).isEqualByComparingTo("30");
        // begin 已存在不覆盖
        assertThat(cap.getValue().getBeginHarvestdate()).isEqualTo(LocalDate.of(2026, 5, 30));
    }

    @Test
    @DisplayName("完成采收(finish=true)：completed + end_harvestdate=采收日 + average_yield=actual/plot_area（end_actualdate 归种植完成 finishPlant，采摘完成不写）")
    void submitPick_finish() {
        PlantDetails d = detailFixture();
        d.setActualYield(new BigDecimal("100"));   // 已由采摘活动管理累加
        when(detailsMapper.selectById(100L)).thenReturn(d);
        // SM-4：pending→completed（未开始直接完成）乐观 UPDATE 成功（affected=1 = 本次赢得首次激活）
        when(detailsMapper.update(isNull(), any())).thenReturn(1);

        PickSubmitBo bo = new PickSubmitBo();
        bo.setDetailId(100L);
        bo.setHarvestDate(LocalDate.of(2026, 6, 1));
        bo.setFinish(true);

        service.submitPick(bo);

        ArgumentCaptor<PlantDetails> cap = ArgumentCaptor.forClass(PlantDetails.class);
        verify(detailsMapper).updateById(cap.capture());
        PlantDetails saved = cap.getValue();
        assertThat(saved.getHarvestStatus()).isEqualTo("completed");
        // end_actualdate（种植实际结束）归种植完成 finishPlant 独占；采摘完成只写 end_harvestdate
        assertThat(saved.getEndActualdate()).isNull();
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
        bo.setHarvestDate(LocalDate.now());

        assertThatThrownBy(() -> service.submitPick(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("采摘明细不存在");
        verify(farmRecordsService, never()).submitGrow(any());
    }

    @Test
    @DisplayName("采收提交传 teamId → farm_by 落采收班组，operator_user_id 留空（采收按班组记录）")
    void submitPick_team_to_farmBy() {
        PlantDetails d = detailFixture();   // harvestBy=40
        when(detailsMapper.selectById(100L)).thenReturn(d);
        // SM-4：pending→picking 乐观 UPDATE 成功
        when(detailsMapper.update(isNull(), any())).thenReturn(1);

        PickSubmitBo bo = new PickSubmitBo();
        bo.setDetailId(100L);
        bo.setHarvestDate(LocalDate.now());
        bo.setTeamId(88L);

        service.submitPick(bo);

        ArgumentCaptor<GrowRecordBo> grow = ArgumentCaptor.forClass(GrowRecordBo.class);
        verify(farmRecordsService).submitGrow(grow.capture());
        // 采收按班组记录：所选采收班组落 farm_by；operator_user_id 留空
        assertThat(grow.getValue().getFarmBy()).isEqualTo(88L);
        assertThat(grow.getValue().getOperatorUserId()).isNull();
    }

    // ============================================================
    // CROSS-FLOW-002 跨域事件发布点
    // ============================================================

    @Test
    @DisplayName("CROSS-FLOW-002 finish=true 且旧态非 completed → 发布 PlantPickedEvent 1 次")
    void submitPick_finish_publishesEvent() {
        PlantDetails d = detailFixture();   // harvestStatus=pending
        when(detailsMapper.selectById(100L)).thenReturn(d);
        // SM-4：pending→completed 乐观 UPDATE 成功（affected=1 = 本次赢得首次激活）→ 发事件
        when(detailsMapper.update(isNull(), any())).thenReturn(1);

        PickSubmitBo bo = new PickSubmitBo();
        bo.setDetailId(100L);
        bo.setHarvestDate(LocalDate.of(2026, 6, 1));
        bo.setFinish(true);

        service.submitPick(bo);

        verify(eventPublisher).publishEvent(any(PlantPickedEvent.class));
    }

    @Test
    @DisplayName("CROSS-FLOW-002 已活跃(picking)再采收 finish=false → 不重复发布事件（逐次采收不产生重复待办）")
    void submitPick_continue_doesNotPublishEvent() {
        PlantDetails d = detailFixture();
        // 已处于采摘中：开始采摘时已发过事件，继续采收 pending→target 乐观 UPDATE 命不中（affected=0）不重发
        d.setHarvestStatus("picking");
        when(detailsMapper.selectById(100L)).thenReturn(d);
        when(detailsMapper.update(isNull(), any())).thenReturn(0);

        PickSubmitBo bo = new PickSubmitBo();
        bo.setDetailId(100L);
        bo.setHarvestDate(LocalDate.of(2026, 6, 1));
        bo.setFinish(false);

        service.submitPick(bo);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("CROSS-FLOW-002 finish=true 但旧态已 completed（重复点完成）→ 不发布事件（幂等）")
    void submitPick_alreadyCompleted_doesNotPublishEvent() {
        PlantDetails d = detailFixture();
        d.setHarvestStatus("completed");   // 重复点完成，旧态已 completed
        d.setActualYield(new BigDecimal("80"));
        when(detailsMapper.selectById(100L)).thenReturn(d);
        // 行已 completed，pending→target 乐观 UPDATE 命不中（affected=0）→ 幂等不发事件
        when(detailsMapper.update(isNull(), any())).thenReturn(0);

        PickSubmitBo bo = new PickSubmitBo();
        bo.setDetailId(100L);
        bo.setHarvestDate(LocalDate.of(2026, 6, 1));
        bo.setFinish(true);

        service.submitPick(bo);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
