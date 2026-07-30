package org.dromara.djs.breed.core.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.PigStatusRecord;
import org.dromara.djs.breed.core.domain.bo.PigCreateBo;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.domain.vo.PigStatusRecordVo;
import org.dromara.djs.breed.core.enums.PigEndReason;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.event.PigStateChangedEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.mapper.PigStatusRecordMapper;
import org.dromara.djs.breed.core.service.PigStateMachine;
import org.dromara.djs.breed.farm.service.PenCountUpdater;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PigCoreServiceImpl} 单元测试（BRD-CORE-001）。
 *
 * <p>覆盖 fireEvent 关键路径：</p>
 * <ul>
 *   <li>happy path：写 status_record + 更新 pig.current_status + publish event</li>
 *   <li>BREED 事件：mating_id 被回写</li>
 *   <li>DIE 事件：end_reason=DEAD + current_status=END</li>
 *   <li>FARROW 事件：parity +1</li>
 *   <li>TRANSFER 事件：barn/pen 更新</li>
 *   <li>乐观锁冲突：updateById 返 0 → 抛 ServiceException</li>
 *   <li>非 END pig 不存在：抛 not_found</li>
 *   <li>createPig：种母猪(sow) → HB / 非种母猪(公猪/育肥/仔猪) → 空状态('')</li>
 * </ul>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PigCoreServiceImpl 单元测试 (BRD-CORE-001)")
class PigCoreServiceImplTest {

    @Mock
    private PigMapper pigMapper;
    @Mock
    private PigStatusRecordMapper statusRecordMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private org.dromara.djs.breed.farm.mapper.BarnMapper barnMapper;
    @Mock
    private org.dromara.djs.breed.farm.mapper.PenMapper penMapper;
    @Mock
    private PenCountUpdater penCountUpdater;

    private PigCoreServiceImpl service;

    @BeforeEach
    void setup() {
        PigStateMachine sm = new PigStateMachine();
        service = new PigCoreServiceImpl(pigMapper, statusRecordMapper, sm, eventPublisher, barnMapper, penMapper,
            org.mockito.Mockito.mock(org.dromara.common.core.service.DictService.class),
            org.mockito.Mockito.mock(org.dromara.djs.breed.production.service.IProductionCycleConfigService.class),
            org.mockito.Mockito.mock(org.dromara.djs.breed.breeding.mapper.BreedInfoMapper.class),
            penCountUpdater);
    }

    private Pig mkSow(Long id, PigLifecycle status) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo("260520-001");
        p.setPigSex("F");
        p.setPigType("sow");
        p.setCurrentStatus(status.name());
        p.setStatusStartedAt(LocalDateTime.now().minusDays(10));
        p.setParity(0);
        p.setVersion(0);
        return p;
    }

    @Test
    @DisplayName("fireEvent happy: BREED HB→PZ 写 status_record + 更新 pig.currentStatus + publish event")
    void fireEvent_happy_path() {
        Pig pig = mkSow(100L, PigLifecycle.HB);
        when(pigMapper.selectById(100L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);
        when(statusRecordMapper.insert(any(PigStatusRecord.class))).thenReturn(1);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(100L);
        bo.setEventType(PigStatusEvent.BREED);
        bo.setRelatedEventId(9001L);

        PigStatusRecordVo vo = service.fireEvent(bo);

        // 写 record
        ArgumentCaptor<PigStatusRecord> recCaptor = ArgumentCaptor.forClass(PigStatusRecord.class);
        verify(statusRecordMapper).insert(recCaptor.capture());
        PigStatusRecord rec = recCaptor.getValue();
        assertThat(rec.getPigId()).isEqualTo(100L);
        assertThat(rec.getOldStatus()).isEqualTo("HB");
        assertThat(rec.getNewStatus()).isEqualTo("PZ");
        assertThat(rec.getEventType()).isEqualTo("BREED");
        assertThat(rec.getRelatedEventId()).isEqualTo(9001L);
        assertThat(rec.getDurationDays()).isGreaterThanOrEqualTo(0);

        // 更新 pig
        ArgumentCaptor<Pig> pigCaptor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).updateById(pigCaptor.capture());
        assertThat(pigCaptor.getValue().getCurrentStatus()).isEqualTo("PZ");
        assertThat(pigCaptor.getValue().getStatusStartedAt()).isNotNull();

        // publish event
        verify(eventPublisher).publishEvent(any(PigStateChangedEvent.class));

        // 返 VO
        assertThat(vo).isNotNull();
        assertThat(vo.getEventType()).isEqualTo("BREED");
    }

    @Test
    @DisplayName("fireEvent 补录配种: 停留天数按业务日期计算，不按提交时间计算")
    void fireEvent_backdated_breed_uses_business_date_for_duration() {
        Pig pig = mkSow(1001L, PigLifecycle.HB);
        pig.setStatusStartedAt(LocalDate.of(2026, 7, 22).atStartOfDay());
        when(pigMapper.selectById(1001L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(1001L);
        bo.setEventType(PigStatusEvent.BREED);
        bo.setEventAt(LocalDateTime.of(2026, 7, 24, 22, 41, 8));

        service.fireEvent(bo);

        ArgumentCaptor<PigStatusRecord> captor = ArgumentCaptor.forClass(PigStatusRecord.class);
        verify(statusRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getDurationDays()).isEqualTo(2);
        assertThat(captor.getValue().getChangeTime()).isEqualTo(bo.getEventAt());
    }

    @Test
    @DisplayName("fireEvent BREED: mating_id 被回写")
    void fireEvent_breed_updates_mating_id() {
        Pig pig = mkSow(101L, PigLifecycle.DN);
        when(pigMapper.selectById(101L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(101L);
        bo.setEventType(PigStatusEvent.BREED);
        bo.setRelatedEventId(7777L);

        service.fireEvent(bo);

        ArgumentCaptor<Pig> captor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).updateById(captor.capture());
        assertThat(captor.getValue().getMatingId()).isEqualTo(7777L);
    }

    @Test
    @DisplayName("fireEvent DIE: end_reason=DEAD, current_status=END")
    void fireEvent_die_sets_end_reason() {
        Pig pig = mkSow(102L, PigLifecycle.FM);
        when(pigMapper.selectById(102L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(102L);
        bo.setEventType(PigStatusEvent.DIE);
        bo.setRelatedEventId(5555L);

        service.fireEvent(bo);

        ArgumentCaptor<Pig> captor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo("END");
        assertThat(captor.getValue().getEndReason()).isEqualTo(PigEndReason.DEAD.name());
    }

    @Test
    @DisplayName("fireEvent FARROW: parity +1")
    void fireEvent_farrow_increments_parity() {
        Pig pig = mkSow(103L, PigLifecycle.PZ);
        pig.setParity(3);
        when(pigMapper.selectById(103L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(103L);
        bo.setEventType(PigStatusEvent.FARROW);

        service.fireEvent(bo);

        ArgumentCaptor<Pig> captor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).updateById(captor.capture());
        assertThat(captor.getValue().getParity()).isEqualTo(4);
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo("FM");
    }

    @Test
    @DisplayName("fireEvent TRANSFER: barn/pen 更新 + 状态不变")
    void fireEvent_transfer_updates_barn_pen() {
        Pig pig = mkSow(104L, PigLifecycle.PZ);
        pig.setBarnId(1L);
        pig.setPenId(10L);
        when(pigMapper.selectById(104L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(104L);
        bo.setEventType(PigStatusEvent.TRANSFER);
        bo.setPayload(java.util.Map.of("newBarnId", 2L, "newPenId", 20L));

        service.fireEvent(bo);

        ArgumentCaptor<Pig> captor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).updateById(captor.capture());
        assertThat(captor.getValue().getBarnId()).isEqualTo(2L);
        assertThat(captor.getValue().getPenId()).isEqualTo(20L);
        // 状态不变：仍 PZ
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo("PZ");
    }

    @Test
    @DisplayName("FIX-BRD-PENCOUNT-001 fireEvent TRANSFER: 旧栏 -1 / 新栏 +1（迁栏计数配对）")
    void fireEvent_transfer_moves_pen_count() {
        Pig pig = mkSow(120L, PigLifecycle.PZ);
        pig.setBarnId(1L);
        pig.setPenId(10L);
        when(pigMapper.selectById(120L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(120L);
        bo.setEventType(PigStatusEvent.TRANSFER);
        bo.setPayload(java.util.Map.of("newBarnId", 2L, "newPenId", 20L));

        service.fireEvent(bo);

        // 旧栏 id 取事件前的值（applyEventSideEffects 已就地改写 pig.penId）
        verify(penCountUpdater).move(10L, 20L, 1);
        verify(penCountUpdater, never()).decrease(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("FIX-BRD-PENCOUNT-001 fireEvent TRANSFER 只换栋舍不换栏: from=to 交给 move 短路（见 PenCountUpdaterTest）")
    void fireEvent_transfer_same_pen_passes_identical_ids() {
        Pig pig = mkSow(121L, PigLifecycle.PZ);
        pig.setBarnId(1L);
        pig.setPenId(10L);
        when(pigMapper.selectById(121L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(121L);
        bo.setEventType(PigStatusEvent.TRANSFER);
        bo.setPayload(java.util.Map.of("newBarnId", 2L, "newPenId", 10L));

        service.fireEvent(bo);

        verify(penCountUpdater).move(10L, 10L, 1);
    }

    @Test
    @DisplayName("FIX-BRD-PENCOUNT-001 fireEvent SLAUGHTER: 出栏后所在栏 -1")
    void fireEvent_slaughter_decreases_pen_count() {
        Pig pig = mkSow(122L, PigLifecycle.DN);
        pig.setPenId(30L);
        when(pigMapper.selectById(122L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(122L);
        bo.setEventType(PigStatusEvent.SLAUGHTER);

        service.fireEvent(bo);

        verify(penCountUpdater).decrease(30L, 1);
        verify(penCountUpdater, never()).move(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("FIX-BRD-PENCOUNT-001 fireEvent DIE / ELIMINATE: 终止事件同样把所在栏 -1")
    void fireEvent_die_and_eliminate_decrease_pen_count() {
        Pig died = mkSow(123L, PigLifecycle.FM);
        died.setPenId(31L);
        when(pigMapper.selectById(123L)).thenReturn(died);
        Pig culled = mkSow(124L, PigLifecycle.KH);
        culled.setPenId(32L);
        when(pigMapper.selectById(124L)).thenReturn(culled);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        PigEventBo dieBo = new PigEventBo();
        dieBo.setPigId(123L);
        dieBo.setEventType(PigStatusEvent.DIE);
        service.fireEvent(dieBo);

        PigEventBo elimBo = new PigEventBo();
        elimBo.setPigId(124L);
        elimBo.setEventType(PigStatusEvent.ELIMINATE);
        service.fireEvent(elimBo);

        verify(penCountUpdater).decrease(31L, 1);
        verify(penCountUpdater).decrease(32L, 1);
    }

    @Test
    @DisplayName("FIX-BRD-PENCOUNT-001 fireEvent 非迁栏非终止事件（BREED）: 不动栏位计数")
    void fireEvent_breed_keeps_pen_count() {
        Pig pig = mkSow(125L, PigLifecycle.HB);
        pig.setPenId(33L);
        when(pigMapper.selectById(125L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(125L);
        bo.setEventType(PigStatusEvent.BREED);

        service.fireEvent(bo);

        org.mockito.Mockito.verifyNoInteractions(penCountUpdater);
    }

    @Test
    @DisplayName("FIX-BRD-PENCOUNT-001 fireEvent 乐观锁冲突: 猪没动 → 栏位计数也不动")
    void fireEvent_optimistic_lock_keeps_pen_count() {
        Pig pig = mkSow(126L, PigLifecycle.PZ);
        pig.setPenId(34L);
        when(pigMapper.selectById(126L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(0);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(126L);
        bo.setEventType(PigStatusEvent.TRANSFER);
        bo.setPayload(java.util.Map.of("newBarnId", 2L, "newPenId", 44L));

        assertThatThrownBy(() -> service.fireEvent(bo)).isInstanceOf(ServiceException.class);

        org.mockito.Mockito.verifyNoInteractions(penCountUpdater);
    }

    @Test
    @DisplayName("fireEvent 乐观锁冲突: updateById 返 0 → 抛 optimistic_lock_conflict")
    void fireEvent_optimistic_lock_throws() {
        Pig pig = mkSow(105L, PigLifecycle.HB);
        when(pigMapper.selectById(105L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(0); // 模拟版本号冲突

        PigEventBo bo = new PigEventBo();
        bo.setPigId(105L);
        bo.setEventType(PigStatusEvent.BREED);

        assertThatThrownBy(() -> service.fireEvent(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.update.optimistic_lock_conflict");
    }

    @Test
    @DisplayName("fireEvent pig 不存在: 抛 not_found")
    void fireEvent_pig_not_found_throws() {
        when(pigMapper.selectById(999L)).thenReturn(null);
        PigEventBo bo = new PigEventBo();
        bo.setPigId(999L);
        bo.setEventType(PigStatusEvent.BREED);

        assertThatThrownBy(() -> service.fireEvent(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.not_found");
    }

    @Test
    @DisplayName("createPig 公猪: 空状态 '' + 写初始 status_record (old=null, new='')")
    void createPig_boar_initial_empty() {
        PigCreateBo bo = new PigCreateBo();
        bo.setEarNo("260520-099");
        bo.setPigSex("M");
        bo.setPigType("boar");
        bo.setLifecycleId(1);

        service.createPig(bo);

        ArgumentCaptor<Pig> pigCaptor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).insert(pigCaptor.capture());
        assertThat(pigCaptor.getValue().getCurrentStatus()).isEqualTo("");
        assertThat(pigCaptor.getValue().getStatusStartedAt()).isNotNull();

        ArgumentCaptor<PigStatusRecord> recCaptor = ArgumentCaptor.forClass(PigStatusRecord.class);
        verify(statusRecordMapper).insert(recCaptor.capture());
        PigStatusRecord rec = recCaptor.getValue();
        assertThat(rec.getOldStatus()).isNull();
        assertThat(rec.getNewStatus()).isEqualTo("");
        assertThat(rec.getEventType()).isEqualTo("INTRO");
    }

    @Test
    @DisplayName("createPig 母猪: initial=HB")
    void createPig_sow_initial_HB() {
        PigCreateBo bo = new PigCreateBo();
        bo.setEarNo("260520-001");
        bo.setPigSex("F");
        bo.setPigType("sow");

        service.createPig(bo);

        ArgumentCaptor<Pig> pigCaptor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).insert(pigCaptor.capture());
        assertThat(pigCaptor.getValue().getCurrentStatus()).isEqualTo("HB");
    }

    @Test
    @DisplayName("createPig 引种母猪: 进入后备状态时间 + INTRO record 按引种日期(非提交时间)")
    void createPig_statusStartedAt_uses_introduceDate() {
        LocalDate introDate = LocalDate.now().minusDays(3);
        PigCreateBo bo = new PigCreateBo();
        bo.setEarNo("260520-002");
        bo.setPigSex("F");
        bo.setPigType("sow");
        bo.setIntroduceDate(introDate);

        service.createPig(bo);

        ArgumentCaptor<Pig> pigCaptor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).insert(pigCaptor.capture());
        assertThat(pigCaptor.getValue().getStatusStartedAt()).isEqualTo(introDate.atStartOfDay());

        ArgumentCaptor<PigStatusRecord> recCaptor = ArgumentCaptor.forClass(PigStatusRecord.class);
        verify(statusRecordMapper).insert(recCaptor.capture());
        assertThat(recCaptor.getValue().getChangeTime()).isEqualTo(introDate.atStartOfDay());
    }

    // ===== internalIntroToReserve（内部留种：育肥猪→种猪类型变更，FIX-BRD-PIGTYPE-001）=====

    private Pig mkFattening(Long id, String sex, PigLifecycle status) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo("260601-001");
        p.setPigSex(sex);
        p.setPigType("fattening");
        p.setCurrentStatus(status == null ? null : status.name());
        p.setStatusStartedAt(LocalDateTime.now().minusDays(30));
        p.setVersion(0);
        return p;
    }

    @Test
    @DisplayName("internalIntroToReserve 母育肥猪: pig_type fattening→sow + 状态 HB(后备)")
    void internalIntroToReserve_female_fattening_to_sow() {
        Pig pig = mkFattening(200L, "F", PigLifecycle.HB);
        when(pigMapper.selectById(200L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        LocalDate introduceDate = LocalDate.of(2026, 7, 21);
        service.internalIntroToReserve(200L, introduceDate);

        ArgumentCaptor<Pig> captor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPigType()).isEqualTo("sow");
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo("HB");
        assertThat(captor.getValue().getIntroduceDate()).isEqualTo(introduceDate);
        assertThat(captor.getValue().getStatusStartedAt()).isEqualTo(introduceDate.atStartOfDay());

        ArgumentCaptor<PigStatusRecord> recCaptor = ArgumentCaptor.forClass(PigStatusRecord.class);
        verify(statusRecordMapper).insert(recCaptor.capture());
        assertThat(recCaptor.getValue().getNewStatus()).isEqualTo("HB");
        assertThat(recCaptor.getValue().getEventType()).isEqualTo("INTRO");
        assertThat(recCaptor.getValue().getChangeTime()).isEqualTo(introduceDate.atStartOfDay());
    }

    @Test
    @DisplayName("internalIntroToReserve 引种日期为空: 主表和状态起点统一回落当天")
    void internalIntroToReserve_null_date_falls_back_to_today() {
        Pig pig = mkFattening(203L, "F", PigLifecycle.HB);
        when(pigMapper.selectById(203L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        service.internalIntroToReserve(203L, null);

        ArgumentCaptor<Pig> captor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).updateById(captor.capture());
        Pig updated = captor.getValue();
        assertThat(updated.getIntroduceDate()).isNotNull();
        assertThat(updated.getStatusStartedAt()).isEqualTo(updated.getIntroduceDate().atStartOfDay());
    }

    @Test
    @DisplayName("internalIntroToReserve 公育肥猪: pig_type fattening→boar + 空状态('')")
    void internalIntroToReserve_male_fattening_to_boar() {
        Pig pig = mkFattening(201L, "M", PigLifecycle.HB);
        when(pigMapper.selectById(201L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        service.internalIntroToReserve(201L, LocalDate.now());

        ArgumentCaptor<Pig> captor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPigType()).isEqualTo("boar");
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo("");
    }

    @Test
    @DisplayName("internalIntroToReserve 非育肥猪(已是 sow): 幂等跳过，不 update/不写 record")
    void internalIntroToReserve_non_fattening_skips() {
        Pig pig = mkSow(202L, PigLifecycle.HB);
        when(pigMapper.selectById(202L)).thenReturn(pig);

        service.internalIntroToReserve(202L, LocalDate.now());

        verify(pigMapper, never()).updateById(any(Pig.class));
        verify(statusRecordMapper, never()).insert(any(PigStatusRecord.class));
    }

    @Test
    @DisplayName("internalIntroToReserve 终止(END)育肥猪: 跳过不留种")
    void internalIntroToReserve_terminal_skips() {
        Pig pig = mkFattening(203L, "F", PigLifecycle.END);
        when(pigMapper.selectById(203L)).thenReturn(pig);

        service.internalIntroToReserve(203L, LocalDate.now());

        verify(pigMapper, never()).updateById(any(Pig.class));
    }

    // ===== 空状态育肥猪事件（FIX-BRD-PIG-EMPTY-STATUS：育肥猪空状态可出栏/死亡/淘汰/转栏）=====

    @Test
    @DisplayName("fireEvent 空状态育肥猪 SLAUGHTER: 空 current_status → END + end_reason=MARKET（出栏报错根因）")
    void fireEvent_emptyStatus_fattening_slaughter() {
        Pig pig = mkFattening(210L, "F", null);
        pig.setCurrentStatus("");  // 空状态（育肥猪正常态，与 staging 真实数据一致）
        when(pigMapper.selectById(210L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(210L);
        bo.setEventType(PigStatusEvent.SLAUGHTER);

        service.fireEvent(bo);

        ArgumentCaptor<Pig> captor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo("END");
        assertThat(captor.getValue().getEndReason()).isEqualTo(PigEndReason.MARKET.name());

        ArgumentCaptor<PigStatusRecord> rec = ArgumentCaptor.forClass(PigStatusRecord.class);
        verify(statusRecordMapper).insert(rec.capture());
        assertThat(rec.getValue().getOldStatus()).isNull();   // 空状态 → old_status=null
        assertThat(rec.getValue().getNewStatus()).isEqualTo("END");
    }

    @Test
    @DisplayName("fireEvent 空状态育肥猪 TRANSFER: 状态保持空（不写 current_status）")
    void fireEvent_emptyStatus_fattening_transfer_keepsEmpty() {
        Pig pig = mkFattening(211L, "F", null);
        pig.setCurrentStatus("");
        pig.setBarnId(1L);
        when(pigMapper.selectById(211L)).thenReturn(pig);
        when(pigMapper.updateById(any(Pig.class))).thenReturn(1);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(211L);
        bo.setEventType(PigStatusEvent.TRANSFER);
        bo.setPayload(java.util.Map.of("newBarnId", 2L));

        service.fireEvent(bo);

        ArgumentCaptor<Pig> captor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).updateById(captor.capture());
        // 状态不变事件 + 空状态：current_status 保持空（""），barn 更新
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo("");
        assertThat(captor.getValue().getBarnId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("precheckEvent 公猪走 FARROW: 抛 female_only(400) 且不写任何库")
    void precheckEvent_boar_farrow_female_only() {
        Pig boar = mkFattening(300L, "M", null);
        boar.setPigType("boar");
        boar.setCurrentStatus("");          // 种公猪空状态（ADR-0016）
        when(pigMapper.selectById(300L)).thenReturn(boar);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(300L);
        bo.setEventType(PigStatusEvent.FARROW);

        assertThatThrownBy(() -> service.precheckEvent(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.female_only")
            .extracting(e -> ((ServiceException) e).getCode()).isEqualTo(400);

        verify(statusRecordMapper, never()).insert(any(PigStatusRecord.class));
        verify(pigMapper, never()).updateById(any(Pig.class));
    }

    @Test
    @DisplayName("precheckEvent HB 母猪走 FARROW: 抛 invalid_transition(400)（SIMPLE 表只有 PZ→FM）")
    void precheckEvent_hb_sow_farrow_invalid_transition() {
        Pig sow = mkSow(301L, PigLifecycle.HB);
        when(pigMapper.selectById(301L)).thenReturn(sow);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(301L);
        bo.setEventType(PigStatusEvent.FARROW);

        assertThatThrownBy(() -> service.precheckEvent(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.invalid_transition")
            .extracting(e -> ((ServiceException) e).getCode()).isEqualTo(400);

        verify(statusRecordMapper, never()).insert(any(PigStatusRecord.class));
        verify(pigMapper, never()).updateById(any(Pig.class));
    }

    @Test
    @DisplayName("precheckEvent PZ 母猪走 FARROW: 返目标态 FM 且不写任何库")
    void precheckEvent_pz_sow_farrow_returns_FM() {
        Pig sow = mkSow(302L, PigLifecycle.PZ);
        when(pigMapper.selectById(302L)).thenReturn(sow);

        PigEventBo bo = new PigEventBo();
        bo.setPigId(302L);
        bo.setEventType(PigStatusEvent.FARROW);

        assertThat(service.precheckEvent(bo)).isEqualTo(PigLifecycle.FM);

        verify(statusRecordMapper, never()).insert(any(PigStatusRecord.class));
        verify(pigMapper, never()).updateById(any(Pig.class));
    }
}
