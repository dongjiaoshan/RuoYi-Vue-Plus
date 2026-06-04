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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
 *   <li>createPig：公猪 → BOAR_ACTIVE / 母猪 → HB</li>
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

    private PigCoreServiceImpl service;

    @BeforeEach
    void setup() {
        PigStateMachine sm = new PigStateMachine();
        service = new PigCoreServiceImpl(pigMapper, statusRecordMapper, sm, eventPublisher, barnMapper, penMapper,
            org.mockito.Mockito.mock(org.dromara.common.core.service.DictService.class),
            org.mockito.Mockito.mock(org.dromara.djs.breed.production.service.IProductionCycleConfigService.class));
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
    @DisplayName("createPig 公猪: initial=BOAR_ACTIVE + 写初始 status_record (old=null)")
    void createPig_boar_initial_BOAR_ACTIVE() {
        PigCreateBo bo = new PigCreateBo();
        bo.setEarNo("260520-099");
        bo.setPigSex("M");
        bo.setPigType("boar");
        bo.setLifecycleId(1);

        service.createPig(bo);

        ArgumentCaptor<Pig> pigCaptor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).insert(pigCaptor.capture());
        assertThat(pigCaptor.getValue().getCurrentStatus()).isEqualTo("BOAR_ACTIVE");
        assertThat(pigCaptor.getValue().getStatusStartedAt()).isNotNull();

        ArgumentCaptor<PigStatusRecord> recCaptor = ArgumentCaptor.forClass(PigStatusRecord.class);
        verify(statusRecordMapper).insert(recCaptor.capture());
        PigStatusRecord rec = recCaptor.getValue();
        assertThat(rec.getOldStatus()).isNull();
        assertThat(rec.getNewStatus()).isEqualTo("BOAR_ACTIVE");
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
}
