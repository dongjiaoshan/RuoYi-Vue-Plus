package org.dromara.djs.breed.event.eliminate.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.service.OssService;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.eliminate.domain.PigCulling;
import org.dromara.djs.breed.event.eliminate.domain.bo.EliminateBo;
import org.dromara.djs.breed.event.eliminate.mapper.PigCullingMapper;
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
 * {@link EliminateServiceImpl} 单元测试（BRD-EVENT-004 ELIMINATE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EliminateServiceImpl 单元测试 (BRD-EVENT-004)")
class EliminateServiceImplTest {

    @Mock
    private PigCullingMapper cullingMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private BarnMapper barnMapper;
    @Mock
    private PenMapper penMapper;
    @Mock
    private IPigCoreService pigCoreService;
    @Mock
    private OssService ossService;
    @Mock
    private DictService dictService;

    private EliminateServiceImpl service;

    @BeforeEach
    void setup() {
        service = new EliminateServiceImpl(cullingMapper, pigMapper, barnMapper, penMapper, pigCoreService, ossService, dictService);
    }

    private Pig mkPig(Long id, PigLifecycle status) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo("260520-002");
        p.setPigSex("F");
        p.setPigType("sow");
        p.setCurrentStatus(status.name());
        return p;
    }

    private EliminateBo mkBo(Long pigId, String ossIds) {
        EliminateBo bo = new EliminateBo();
        bo.setPigId(pigId);
        bo.setCullingDate(LocalDateTime.of(2026, 5, 27, 10, 30));
        bo.setCullingReason("low_productivity");
        bo.setOssIds(ossIds);
        return bo;
    }

    @Test
    @DisplayName("happy: DN 母猪 ELIMINATE → INSERT culling + fireEvent(ELIMINATE) 一次")
    void happyPath() {
        Pig pig = mkPig(200L, PigLifecycle.DN);
        when(pigMapper.selectById(200L)).thenReturn(pig);

        EliminateBo bo = mkBo(200L, "2001");
        service.recordEliminate(bo);

        ArgumentCaptor<PigCulling> c = ArgumentCaptor.forClass(PigCulling.class);
        verify(cullingMapper, times(1)).insert(c.capture());
        assertThat(c.getValue().getCullingReason()).isEqualTo("low_productivity");
        assertThat(c.getValue().getOssIds()).isEqualTo("2001");

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo(PigStatusEvent.ELIMINATE);
    }

    @Test
    @DisplayName("校验: ossIds 空 → ServiceException")
    void validate_photo_required() {
        Pig pig = mkPig(201L, PigLifecycle.HB);
        when(pigMapper.selectById(201L)).thenReturn(pig);

        EliminateBo bo = mkBo(201L, null);
        assertThatThrownBy(() -> service.recordEliminate(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("eliminate.photo");
        verify(cullingMapper, never()).insert(any(PigCulling.class));
        verify(pigCoreService, never()).fireEvent(any());
    }

    @Test
    @DisplayName("非法 transition: END → fireEvent 抛 terminal 透传")
    void invalidTransition_terminal() {
        Pig pig = mkPig(202L, PigLifecycle.END);
        when(pigMapper.selectById(202L)).thenReturn(pig);
        when(pigCoreService.fireEvent(any()))
            .thenThrow(new ServiceException("pig.state.terminal"));

        EliminateBo bo = mkBo(202L, "2001");
        assertThatThrownBy(() -> service.recordEliminate(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.state.terminal");
    }
}
