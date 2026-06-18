package org.dromara.djs.breed.event.farrow.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.eartag.mapper.PigPigletnoMapper;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.domain.bo.FarrowBo;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
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
 * {@link FarrowServiceImpl} 单元测试（BRD-EVENT-002 FARROW）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>happy path：PZ 母猪 FARROW → INSERT farrow + fireEvent(FARROW)；</li>
 *   <li>breedingId 缺时回落 pig.matingId；</li>
 *   <li>校验 liveBorn > totalBorn → 拒绝；</li>
 *   <li>非法 transition（非 PZ）→ fireEvent 抛传播；</li>
 *   <li>pig 不存在 → 拒绝。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FarrowServiceImpl 单元测试 (BRD-EVENT-002)")
class FarrowServiceImplTest {

    @Mock
    private PigFarrowMapper farrowMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private BarnMapper barnMapper;
    @Mock
    private PenMapper penMapper;
    @Mock
    private PigPigletnoMapper pigletnoMapper;
    @Mock
    private IPigCoreService pigCoreService;
    @Mock
    private DictService dictService;

    private FarrowServiceImpl service;

    @BeforeEach
    void setup() {
        service = new FarrowServiceImpl(farrowMapper, pigMapper, barnMapper, penMapper,
            pigletnoMapper, pigCoreService, dictService);
    }

    private Pig mkSow(Long id, PigLifecycle status, Long matingId) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo("260520-001");
        p.setPigSex("F");
        p.setPigType("sow");
        p.setCurrentStatus(status.name());
        p.setMatingId(matingId);
        p.setParity(3);
        return p;
    }

    private FarrowBo mkBo(Long pigId, Long breedingId, int total, int live) {
        FarrowBo bo = new FarrowBo();
        bo.setPigId(pigId);
        bo.setBreedingId(breedingId);
        bo.setFarrowDate(LocalDateTime.of(2026, 5, 27, 10, 0));
        bo.setTotalBorn(total);
        bo.setLiveBorn(live);
        bo.setDeadBorn(0);
        bo.setMummyBorn(0);
        bo.setWeakBorn(0);
        return bo;
    }

    @Test
    @DisplayName("happy: PZ FARROW → INSERT farrow + fireEvent(FARROW) + parity=pig.parity+1")
    void happyPath() {
        Pig pig = mkSow(200L, PigLifecycle.PZ, 7777L);
        when(pigMapper.selectById(200L)).thenReturn(pig);

        FarrowBo bo = mkBo(200L, 7777L, 12, 10);
        service.recordFarrow(bo);

        ArgumentCaptor<PigFarrow> cap = ArgumentCaptor.forClass(PigFarrow.class);
        verify(farrowMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getPigId()).isEqualTo(200L);
        assertThat(cap.getValue().getLiveBorn()).isEqualTo(10);
        assertThat(cap.getValue().getTotalBorn()).isEqualTo(12);
        assertThat(cap.getValue().getBreedingId()).isEqualTo(7777L);
        assertThat(cap.getValue().getParity()).isEqualTo(4); // pig.parity=3 +1

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo(PigStatusEvent.FARROW);
    }

    @Test
    @DisplayName("multiclass: 原型 93 多分类字段（健仔公母/弱仔留养公母/弱仔处死/畸形）透传落库")
    void multiclass_fields_persisted() {
        Pig pig = mkSow(210L, PigLifecycle.PZ, 7777L);
        when(pigMapper.selectById(210L)).thenReturn(pig);

        FarrowBo bo = mkBo(210L, 7777L, 21, 20);
        bo.setHealthyMale(10);
        bo.setHealthyFemale(9);
        bo.setWeakRaisedMale(0);
        bo.setWeakRaisedFemale(1);
        bo.setWeakCulled(0);
        bo.setDeformedBorn(0);
        service.recordFarrow(bo);

        ArgumentCaptor<PigFarrow> cap = ArgumentCaptor.forClass(PigFarrow.class);
        verify(farrowMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getHealthyMale()).isEqualTo(10);
        assertThat(cap.getValue().getHealthyFemale()).isEqualTo(9);
        assertThat(cap.getValue().getWeakRaisedMale()).isEqualTo(0);
        assertThat(cap.getValue().getWeakRaisedFemale()).isEqualTo(1);
        assertThat(cap.getValue().getWeakCulled()).isEqualTo(0);
        assertThat(cap.getValue().getDeformedBorn()).isEqualTo(0);
    }

    @Test
    @DisplayName("multiclass: 多分类字段缺省 → 落库回落 0（不 NPE）")
    void multiclass_fields_default_zero() {
        Pig pig = mkSow(211L, PigLifecycle.PZ, null);
        when(pigMapper.selectById(211L)).thenReturn(pig);

        FarrowBo bo = mkBo(211L, null, 8, 8); // 不设任何多分类字段
        service.recordFarrow(bo);

        ArgumentCaptor<PigFarrow> cap = ArgumentCaptor.forClass(PigFarrow.class);
        verify(farrowMapper).insert(cap.capture());
        assertThat(cap.getValue().getHealthyMale()).isEqualTo(0);
        assertThat(cap.getValue().getHealthyFemale()).isEqualTo(0);
        assertThat(cap.getValue().getWeakRaisedMale()).isEqualTo(0);
        assertThat(cap.getValue().getWeakRaisedFemale()).isEqualTo(0);
        assertThat(cap.getValue().getWeakCulled()).isEqualTo(0);
        assertThat(cap.getValue().getDeformedBorn()).isEqualTo(0);
    }

    @Test
    @DisplayName("breedingId 缺 → 回落 pig.matingId")
    void breedingId_fallback_to_matingId() {
        Pig pig = mkSow(201L, PigLifecycle.PZ, 8888L);
        when(pigMapper.selectById(201L)).thenReturn(pig);

        FarrowBo bo = mkBo(201L, null, 8, 8);
        service.recordFarrow(bo);

        ArgumentCaptor<PigFarrow> cap = ArgumentCaptor.forClass(PigFarrow.class);
        verify(farrowMapper).insert(cap.capture());
        assertThat(cap.getValue().getBreedingId()).isEqualTo(8888L);
    }

    @Test
    @DisplayName("校验: liveBorn > totalBorn → ServiceException")
    void validate_liveExceedsTotal() {
        Pig pig = mkSow(202L, PigLifecycle.PZ, null);
        when(pigMapper.selectById(202L)).thenReturn(pig);

        FarrowBo bo = mkBo(202L, null, 5, 10);
        assertThatThrownBy(() -> service.recordFarrow(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("farrow.live_exceeds_total");
        verify(farrowMapper, never()).insert(any(PigFarrow.class));
        verify(pigCoreService, never()).fireEvent(any());
    }

    @Test
    @DisplayName("非法 transition: pig 状态非 PZ → fireEvent 抛 ServiceException 透传")
    void invalidTransition() {
        Pig pig = mkSow(203L, PigLifecycle.HB, null);
        when(pigMapper.selectById(203L)).thenReturn(pig);
        when(pigCoreService.fireEvent(any()))
            .thenThrow(new ServiceException("pig.event.invalid_transition"));

        FarrowBo bo = mkBo(203L, null, 6, 6);
        assertThatThrownBy(() -> service.recordFarrow(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.invalid_transition");
    }

    @Test
    @DisplayName("pig 不存在 → ServiceException")
    void pigNotFound() {
        when(pigMapper.selectById(999L)).thenReturn(null);

        FarrowBo bo = mkBo(999L, null, 1, 1);
        assertThatThrownBy(() -> service.recordFarrow(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.not_found");
    }
}
