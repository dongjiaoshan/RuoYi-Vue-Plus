package org.dromara.djs.breed.event.weaning.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
import org.dromara.djs.breed.event.weaning.domain.PigWeaning;
import org.dromara.djs.breed.event.weaning.domain.bo.WeaningBo;
import org.dromara.djs.breed.event.weaning.mapper.PigWeaningMapper;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WeaningServiceImpl} 单元测试（BRD-EVENT-002 WEAN）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>happy path：FM 母猪 WEAN → INSERT weaning + fireEvent(WEAN)；</li>
 *   <li>avg 自动计算（weanedWeight + count）；</li>
 *   <li>farrow.pig_id 不匹配 → 拒绝；</li>
 *   <li>weanedCount > farrow.liveBorn → 拒绝；</li>
 *   <li>farrow 不存在 → 拒绝；</li>
 *   <li>非法 transition（非 FM）→ fireEvent 抛传播。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WeaningServiceImpl 单元测试 (BRD-EVENT-002)")
class WeaningServiceImplTest {

    @Mock
    private PigWeaningMapper weaningMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private PigFarrowMapper farrowMapper;
    @Mock
    private IPigCoreService pigCoreService;

    private WeaningServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WeaningServiceImpl(weaningMapper, pigMapper, farrowMapper, pigCoreService);
    }

    private Pig mkSow(Long id, PigLifecycle status) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo("260520-001");
        p.setPigSex("F");
        p.setCurrentStatus(status.name());
        return p;
    }

    private PigFarrow mkFarrow(Long id, Long pigId, int liveBorn, Long breedingId) {
        PigFarrow f = new PigFarrow();
        f.setId(id);
        f.setPigId(pigId);
        f.setLiveBorn(liveBorn);
        f.setBreedingId(breedingId);
        return f;
    }

    private WeaningBo mkBo(Long pigId, Long farrowId, int count, BigDecimal weight) {
        WeaningBo bo = new WeaningBo();
        bo.setPigId(pigId);
        bo.setFarrowId(farrowId);
        bo.setWeaningDate(LocalDateTime.of(2026, 6, 24, 9, 0));
        bo.setWeanedCount(count);
        bo.setWeanedWeight(weight);
        return bo;
    }

    @Test
    @DisplayName("happy: FM WEAN → INSERT + fireEvent(WEAN) + 自动算 avg")
    void happyPath_autoAvg() {
        Pig pig = mkSow(300L, PigLifecycle.FM);
        when(pigMapper.selectById(300L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(500L, 300L, 10, 7777L);
        when(farrowMapper.selectById(500L)).thenReturn(farrow);

        WeaningBo bo = mkBo(300L, 500L, 8, new BigDecimal("60.000"));
        service.recordWeaning(bo);

        ArgumentCaptor<PigWeaning> cap = ArgumentCaptor.forClass(PigWeaning.class);
        verify(weaningMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getPigId()).isEqualTo(300L);
        assertThat(cap.getValue().getFarrowId()).isEqualTo(500L);
        assertThat(cap.getValue().getBreedingId()).isEqualTo(7777L);
        assertThat(cap.getValue().getWeanedCount()).isEqualTo(8);
        // 60 / 8 = 7.500
        assertThat(cap.getValue().getAvgWeanedWeight()).isEqualByComparingTo(new BigDecimal("7.500"));

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo(PigStatusEvent.WEAN);
    }

    @Test
    @DisplayName("校验: farrow.pig_id 与 bo.pig_id 不匹配 → ServiceException")
    void validate_farrowPigMismatch() {
        Pig pig = mkSow(301L, PigLifecycle.FM);
        when(pigMapper.selectById(301L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(501L, 999L /* 不同 pigId */, 10, 7777L);
        when(farrowMapper.selectById(501L)).thenReturn(farrow);

        WeaningBo bo = mkBo(301L, 501L, 5, null);
        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("weaning.farrow_pig_mismatch");
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
        verify(pigCoreService, never()).fireEvent(any());
    }

    @Test
    @DisplayName("校验: weanedCount > farrow.liveBorn → ServiceException")
    void validate_countExceedsLiveBorn() {
        Pig pig = mkSow(302L, PigLifecycle.FM);
        when(pigMapper.selectById(302L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(502L, 302L, 6, null);
        when(farrowMapper.selectById(502L)).thenReturn(farrow);

        WeaningBo bo = mkBo(302L, 502L, 10, null);
        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("weaning.count_exceeds_live_born");
    }

    @Test
    @DisplayName("校验: farrow 不存在 → ServiceException")
    void farrowNotFound() {
        Pig pig = mkSow(303L, PigLifecycle.FM);
        when(pigMapper.selectById(303L)).thenReturn(pig);
        when(farrowMapper.selectById(999L)).thenReturn(null);

        WeaningBo bo = mkBo(303L, 999L, 1, null);
        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("weaning.farrow_not_found");
    }

    @Test
    @DisplayName("非法 transition: pig 非 FM → fireEvent 抛 ServiceException 透传")
    void invalidTransition() {
        Pig pig = mkSow(304L, PigLifecycle.HB);
        when(pigMapper.selectById(304L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(504L, 304L, 10, null);
        when(farrowMapper.selectById(504L)).thenReturn(farrow);
        when(pigCoreService.fireEvent(any()))
            .thenThrow(new ServiceException("pig.event.invalid_transition"));

        WeaningBo bo = mkBo(304L, 504L, 5, null);
        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.invalid_transition");
    }
}
