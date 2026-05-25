package org.dromara.djs.breed.event.slaughter.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.event.PigMarketingEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.slaughter.domain.PigMarketing;
import org.dromara.djs.breed.event.slaughter.domain.bo.SlaughterBo;
import org.dromara.djs.breed.event.slaughter.mapper.PigMarketingMapper;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SlaughterServiceImpl} 单元测试（BRD-EVENT-004 SLAUGHTER）。
 *
 * <p>覆盖：INSERT marketing → fireEvent(SLAUGHTER) → publishEvent(PigMarketingEvent) 三步同事务。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SlaughterServiceImpl 单元测试 (BRD-EVENT-004)")
class SlaughterServiceImplTest {

    @Mock
    private PigMarketingMapper marketingMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private IPigCoreService pigCoreService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SlaughterServiceImpl service;

    @BeforeEach
    void setup() {
        service = new SlaughterServiceImpl(marketingMapper, pigMapper, pigCoreService, eventPublisher);
    }

    private Pig mkFatteningPig() {
        Pig p = new Pig();
        p.setId(500L);
        p.setEarNo("260520-S-001");
        p.setPigSex("M");
        p.setPigType("fattening");
        p.setCurrentStatus(PigLifecycle.BOAR_ACTIVE.name());
        return p;
    }

    private SlaughterBo mkBo(Long pigId, String ossIds, BigDecimal weight) {
        SlaughterBo bo = new SlaughterBo();
        bo.setPigId(pigId);
        bo.setMarketingDate(LocalDateTime.of(2026, 5, 27, 16, 0));
        bo.setOutWeight(weight);
        bo.setOutDest("外销");
        bo.setOssIds(ossIds);
        return bo;
    }

    @Test
    @DisplayName("happy: SLAUGHTER → INSERT marketing + fireEvent(SLAUGHTER) + publishEvent(PigMarketingEvent)")
    void happyPath() {
        Pig pig = mkFatteningPig();
        when(pigMapper.selectById(500L)).thenReturn(pig);

        SlaughterBo bo = mkBo(500L, "5001,5002", new BigDecimal("125.50"));
        service.recordSlaughter(bo);

        ArgumentCaptor<PigMarketing> m = ArgumentCaptor.forClass(PigMarketing.class);
        verify(marketingMapper, times(1)).insert(m.capture());
        assertThat(m.getValue().getOutWeight()).isEqualByComparingTo("125.50");
        assertThat(m.getValue().getOutDest()).isEqualTo("外销");
        assertThat(m.getValue().getOssIds()).isEqualTo("5001,5002");

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo(PigStatusEvent.SLAUGHTER);

        ArgumentCaptor<PigMarketingEvent> pe = ArgumentCaptor.forClass(PigMarketingEvent.class);
        verify(eventPublisher, times(1)).publishEvent(pe.capture());
        assertThat(pe.getValue().getMarketing()).isNotNull();
        assertThat(pe.getValue().getMarketing().getOutWeight()).isEqualByComparingTo("125.50");
    }

    @Test
    @DisplayName("校验: ossIds 空 → ServiceException，不 INSERT / 不 fireEvent / 不 publish")
    void validate_photo_required() {
        Pig pig = mkFatteningPig();
        when(pigMapper.selectById(500L)).thenReturn(pig);

        SlaughterBo bo = mkBo(500L, "", new BigDecimal("125.50"));
        assertThatThrownBy(() -> service.recordSlaughter(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("slaughter.photo");
        verify(marketingMapper, never()).insert(any(PigMarketing.class));
        verify(pigCoreService, never()).fireEvent(any());
        verify(eventPublisher, never()).publishEvent(any(PigMarketingEvent.class));
    }

    @Test
    @DisplayName("非法 transition: END → fireEvent 抛 terminal 透传，publish 不调")
    void invalidTransition_terminal() {
        Pig pig = mkFatteningPig();
        pig.setCurrentStatus(PigLifecycle.END.name());
        when(pigMapper.selectById(500L)).thenReturn(pig);
        when(pigCoreService.fireEvent(any()))
            .thenThrow(new ServiceException("pig.state.terminal"));

        SlaughterBo bo = mkBo(500L, "5001", new BigDecimal("125.50"));
        assertThatThrownBy(() -> service.recordSlaughter(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.state.terminal");
        // fireEvent 抛了，publish 不应该被调（事务回滚后）
        verify(eventPublisher, never()).publishEvent(any(PigMarketingEvent.class));
    }
}
