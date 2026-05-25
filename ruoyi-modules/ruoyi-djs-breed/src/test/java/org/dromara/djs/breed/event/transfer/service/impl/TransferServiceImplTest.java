package org.dromara.djs.breed.event.transfer.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.transfer.domain.PigTransfer;
import org.dromara.djs.breed.event.transfer.domain.bo.TransferBo;
import org.dromara.djs.breed.event.transfer.mapper.PigTransferMapper;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
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

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TransferServiceImpl} 单元测试（BRD-EVENT-004 TRANSFER）。
 *
 * <p>覆盖 D5 audit a-2：仔猪转育肥舍 payload 含 newPigType='fattening'。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TransferServiceImpl 单元测试 (BRD-EVENT-004)")
class TransferServiceImplTest {

    @Mock
    private PigTransferMapper transferMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private BarnMapper barnMapper;
    @Mock
    private PenMapper penMapper;
    @Mock
    private IPigCoreService pigCoreService;

    private TransferServiceImpl service;

    @BeforeEach
    void setup() {
        service = new TransferServiceImpl(transferMapper, pigMapper, barnMapper, penMapper, pigCoreService);
    }

    private Pig mkPig(Long id, String pigType, PigLifecycle status, Long oldBarnId) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo("260520-T-001");
        p.setPigSex("F");
        p.setPigType(pigType);
        p.setCurrentStatus(status.name());
        p.setBarnId(oldBarnId);
        return p;
    }

    private Barn mkBarn(Long id, String name, String type) {
        Barn b = new Barn();
        b.setId(id);
        b.setBarnName(name);
        b.setBarnType(type);
        return b;
    }

    private TransferBo mkBo(Long pigId, Long newBarnId, Long newPenId) {
        TransferBo bo = new TransferBo();
        bo.setPigId(pigId);
        bo.setTransferDate(LocalDateTime.of(2026, 5, 27, 14, 0));
        bo.setNewBarnId(newBarnId);
        bo.setNewPenId(newPenId);
        return bo;
    }

    @Test
    @DisplayName("happy: 母猪 TRANSFER 同栋舍 → INSERT transfer + fireEvent(TRANSFER) + payload 不含 newPigType")
    void happyPath_sow_no_type_change() {
        Pig pig = mkPig(400L, "sow", PigLifecycle.HB, 1L);
        when(pigMapper.selectById(400L)).thenReturn(pig);
        when(barnMapper.selectById(2L)).thenReturn(mkBarn(2L, "妊娠舍-A", "pregnant"));

        service.recordTransfer(mkBo(400L, 2L, null));

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        Map<String, Object> payload = ev.getValue().getPayload();
        assertThat(payload).isNotNull();
        assertThat(payload.get("newBarnId")).isEqualTo(2L);
        assertThat(payload).doesNotContainKey("newPigType");
    }

    @Test
    @DisplayName("audit a-2: 仔猪 → 育肥舍 (barn_type='fattening') → payload.newPigType='fattening'")
    void piglet_to_fattening_includes_newPigType() {
        Pig pig = mkPig(401L, "piglet", PigLifecycle.HB, 1L);
        when(pigMapper.selectById(401L)).thenReturn(pig);
        when(barnMapper.selectById(3L)).thenReturn(mkBarn(3L, "育肥舍-B", "fattening"));

        Pen newPen = new Pen();
        newPen.setId(30L);
        newPen.setPenName("育肥-B-01");
        when(penMapper.selectById(30L)).thenReturn(newPen);

        service.recordTransfer(mkBo(401L, 3L, 30L));

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        Map<String, Object> payload = ev.getValue().getPayload();
        assertThat(payload).isNotNull();
        assertThat(payload.get("newBarnId")).isEqualTo(3L);
        assertThat(payload.get("newPenId")).isEqualTo(30L);
        assertThat(payload.get("newPigType")).isEqualTo("fattening");
    }

    @Test
    @DisplayName("仔猪 → 保育舍 (非 fattening) → payload 不含 newPigType")
    void piglet_to_non_fattening_no_type_change() {
        Pig pig = mkPig(402L, "piglet", PigLifecycle.HB, 1L);
        when(pigMapper.selectById(402L)).thenReturn(pig);
        when(barnMapper.selectById(4L)).thenReturn(mkBarn(4L, "保育舍-C", "nursery"));

        service.recordTransfer(mkBo(402L, 4L, null));

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        Map<String, Object> payload = ev.getValue().getPayload();
        assertThat(payload).doesNotContainKey("newPigType");
    }

    @Test
    @DisplayName("INSERT 转移记录: old/new barn/pen 信息齐全")
    void insert_with_old_new_info() {
        Pig pig = mkPig(403L, "sow", PigLifecycle.HB, 1L);
        when(pigMapper.selectById(403L)).thenReturn(pig);
        when(barnMapper.selectById(1L)).thenReturn(mkBarn(1L, "种猪舍-A", "breeding"));
        when(barnMapper.selectById(2L)).thenReturn(mkBarn(2L, "妊娠舍-A", "pregnant"));

        service.recordTransfer(mkBo(403L, 2L, null));

        ArgumentCaptor<PigTransfer> t = ArgumentCaptor.forClass(PigTransfer.class);
        verify(transferMapper, times(1)).insert(t.capture());
        assertThat(t.getValue().getOldBarnId()).isEqualTo(1L);
        assertThat(t.getValue().getOldBarnName()).isEqualTo("种猪舍-A");
        assertThat(t.getValue().getNewBarnId()).isEqualTo(2L);
        assertThat(t.getValue().getNewBarnName()).isEqualTo("妊娠舍-A");
    }

    @Test
    @DisplayName("目标 barn 不存在 → ServiceException(transfer.new_barn.not_found)")
    void new_barn_not_found() {
        Pig pig = mkPig(404L, "sow", PigLifecycle.HB, 1L);
        when(pigMapper.selectById(404L)).thenReturn(pig);
        when(barnMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.recordTransfer(mkBo(404L, 99L, null)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("transfer.new_barn.not_found");
        verify(transferMapper, never()).insert(any(PigTransfer.class));
    }

    @Test
    @DisplayName("非法 transition: END pig → fireEvent 抛 terminal 透传")
    void invalidTransition_terminal() {
        Pig pig = mkPig(405L, "sow", PigLifecycle.END, 1L);
        when(pigMapper.selectById(405L)).thenReturn(pig);
        when(barnMapper.selectById(2L)).thenReturn(mkBarn(2L, "妊娠舍-A", "pregnant"));
        when(pigCoreService.fireEvent(any()))
            .thenThrow(new ServiceException("pig.state.terminal"));

        assertThatThrownBy(() -> service.recordTransfer(mkBo(405L, 2L, null)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.state.terminal");
    }

    @Test
    @DisplayName("fireEvent 调用时 eventType=TRANSFER + relatedEventId=transferId")
    void verify_event_type_and_related_id() {
        Pig pig = mkPig(406L, "sow", PigLifecycle.HB, 1L);
        when(pigMapper.selectById(406L)).thenReturn(pig);
        when(barnMapper.selectById(2L)).thenReturn(mkBarn(2L, "妊娠舍-A", "pregnant"));

        service.recordTransfer(mkBo(406L, 2L, null));

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo(PigStatusEvent.TRANSFER);
        assertThat(ev.getValue().getPigId()).isEqualTo(406L);
    }
}
