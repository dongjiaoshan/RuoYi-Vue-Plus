package org.dromara.djs.breed.event.die.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.die.domain.PigDeath;
import org.dromara.djs.breed.event.die.domain.bo.DieBo;
import org.dromara.djs.breed.event.die.mapper.PigDeathMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DieServiceImpl} 单元测试（BRD-EVENT-004 DIE）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>happy path：HB 母猪 DIE → INSERT death + fireEvent(DIE) 一次；</li>
 *   <li>校验：ossIds 空 → ServiceException，不 INSERT 不 fireEvent；</li>
 *   <li>非法 transition（END 已终结）→ fireEvent 抛 terminal 透传；</li>
 *   <li>pig 不存在 → not_found；</li>
 *   <li>deathPigType 自动填 pig.pig_type。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DieServiceImpl 单元测试 (BRD-EVENT-004)")
class DieServiceImplTest {

    @Mock
    private PigDeathMapper deathMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private BarnMapper barnMapper;
    @Mock
    private PenMapper penMapper;
    @Mock
    private IPigCoreService pigCoreService;

    private DieServiceImpl service;

    @BeforeEach
    void setup() {
        service = new DieServiceImpl(deathMapper, pigMapper, barnMapper, penMapper, pigCoreService);
    }

    private Pig mkPig(Long id, PigLifecycle status, String sex, String pigType) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo("260520-001");
        p.setPigSex(sex);
        p.setPigType(pigType);
        p.setCurrentStatus(status.name());
        return p;
    }

    private DieBo mkBo(Long pigId, String ossIds) {
        DieBo bo = new DieBo();
        bo.setPigId(pigId);
        bo.setDeathDate(LocalDateTime.of(2026, 5, 27, 10, 0));
        bo.setDeathKind("normal");
        bo.setDeathReason("disease");
        bo.setOssIds(ossIds);
        return bo;
    }

    @Test
    @DisplayName("happy: HB 母猪 DIE → INSERT death + fireEvent(DIE) 一次 + deathPigType=pig.pig_type")
    void happyPath() {
        Pig pig = mkPig(100L, PigLifecycle.HB, "F", "sow");
        when(pigMapper.selectById(100L)).thenReturn(pig);

        DieBo bo = mkBo(100L, "1001,1002");
        service.recordDie(bo);

        ArgumentCaptor<PigDeath> d = ArgumentCaptor.forClass(PigDeath.class);
        verify(deathMapper, times(1)).insert(d.capture());
        assertThat(d.getValue().getDeathPigType()).isEqualTo("sow");
        assertThat(d.getValue().getOssIds()).isEqualTo("1001,1002");
        assertThat(d.getValue().getEarNo()).isEqualTo("260520-001");

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo(PigStatusEvent.DIE);
        assertThat(ev.getValue().getPigId()).isEqualTo(100L);
        assertThat(ev.getValue().getEventAt()).isEqualTo(bo.getDeathDate());
    }

    @Test
    @DisplayName("校验: ossIds 空 → ServiceException，不 INSERT 不 fireEvent")
    void validate_photo_required() {
        Pig pig = mkPig(101L, PigLifecycle.HB, "F", "sow");
        when(pigMapper.selectById(101L)).thenReturn(pig);

        DieBo bo = mkBo(101L, "");
        assertThatThrownBy(() -> service.recordDie(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("die.photo");
        verify(deathMapper, never()).insert(any(PigDeath.class));
        verify(pigCoreService, never()).fireEvent(any());
    }

    @Test
    @DisplayName("非法 transition: END → fireEvent 抛 terminal 透传")
    void invalidTransition_terminal() {
        Pig pig = mkPig(102L, PigLifecycle.END, "F", "sow");
        when(pigMapper.selectById(102L)).thenReturn(pig);
        when(pigCoreService.fireEvent(any()))
            .thenThrow(new ServiceException("pig.state.terminal"));

        DieBo bo = mkBo(102L, "1001");
        assertThatThrownBy(() -> service.recordDie(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.state.terminal");
    }

    @Test
    @DisplayName("pig 不存在 → not_found")
    void pigNotFound() {
        when(pigMapper.selectById(999L)).thenReturn(null);

        DieBo bo = mkBo(999L, "1001");
        assertThatThrownBy(() -> service.recordDie(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.not_found");
        verify(deathMapper, never()).insert(any(PigDeath.class));
        verify(pigCoreService, never()).fireEvent(any());
    }
}
