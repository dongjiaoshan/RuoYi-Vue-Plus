package org.dromara.djs.breed.event.breeding.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.breeding.domain.PigBreeding;
import org.dromara.djs.breed.event.breeding.domain.bo.BreedingBo;
import org.dromara.djs.breed.event.breeding.mapper.PigBreedingMapper;
import org.dromara.djs.breed.breeding.mapper.BreedConfigMapper;
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
 * {@link BreedingServiceImpl} 单元测试（BRD-EVENT-002 BREED）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>happy path：HB 母猪 BREED → INSERT breeding + fireEvent(BREED, relatedEventId) 调一次；</li>
 *   <li>校验：breedingType=1 必填 boarEarNo；breedingType=2 必填 supplier+batch；</li>
 *   <li>非法 transition（pig.current_status=END）→ 状态机 fireEvent 抛 ServiceException 传播；</li>
 *   <li>pig 不存在 → 抛 not_found；</li>
 *   <li>payload.breedingDate 被透传给 fireEvent（供 applyBreedingCounters 用）。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BreedingServiceImpl 单元测试 (BRD-EVENT-002)")
class BreedingServiceImplTest {

    @Mock
    private PigBreedingMapper breedingMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private BarnMapper barnMapper;
    @Mock
    private PenMapper penMapper;
    @Mock
    private IPigCoreService pigCoreService;
    @Mock
    private BreedConfigMapper breedConfigMapper;

    private BreedingServiceImpl service;

    @BeforeEach
    void setup() {
        service = new BreedingServiceImpl(breedingMapper, pigMapper, barnMapper, penMapper, pigCoreService, breedConfigMapper);
    }

    private Pig mkSow(Long id, PigLifecycle status) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo("260520-001");
        p.setPigSex("F");
        p.setPigType("sow");
        p.setCurrentStatus(status.name());
        p.setParity(2);
        return p;
    }

    private BreedingBo mkBo(Long pigId, String type, String boar) {
        BreedingBo bo = new BreedingBo();
        bo.setPigId(pigId);
        bo.setBreedingDate(LocalDateTime.of(2026, 5, 27, 9, 30));
        bo.setBreedingType(type);
        bo.setBoarEarNo(boar);
        return bo;
    }

    @Test
    @DisplayName("happy: HB 母猪 BREED → INSERT breeding + fireEvent(BREED) 一次 + payload 含 breedingDate")
    void happyPath_firesBreedEventOnce() {
        Pig pig = mkSow(100L, PigLifecycle.HB);
        when(pigMapper.selectById(100L)).thenReturn(pig);

        BreedingBo bo = mkBo(100L, "1", "B-001");
        service.recordBreeding(bo);

        // 1 行 breeding INSERT
        ArgumentCaptor<PigBreeding> br = ArgumentCaptor.forClass(PigBreeding.class);
        verify(breedingMapper, times(1)).insert(br.capture());
        assertThat(br.getValue().getPigId()).isEqualTo(100L);
        assertThat(br.getValue().getEarNo()).isEqualTo("260520-001");
        assertThat(br.getValue().getBreedingType()).isEqualTo("1");
        assertThat(br.getValue().getBoarEarNo()).isEqualTo("B-001");
        assertThat(br.getValue().getParity()).isEqualTo(2);

        // fireEvent 一次，eventType=BREED，payload.breedingDate=date
        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo(PigStatusEvent.BREED);
        assertThat(ev.getValue().getPigId()).isEqualTo(100L);
        assertThat(ev.getValue().getEventAt()).isEqualTo(bo.getBreedingDate());
        assertThat(ev.getValue().getPayload()).isNotNull();
        assertThat(ev.getValue().getPayload().get("breedingDate")).isEqualTo(bo.getBreedingDate().toLocalDate());
    }

    @Test
    @DisplayName("#20: bo.operatorId 非空 → INSERT 用所选配种人员（不回落登录态）")
    void recordBreeding_honorsExplicitOperatorId() {
        Pig pig = mkSow(110L, PigLifecycle.HB);
        when(pigMapper.selectById(110L)).thenReturn(pig);

        BreedingBo bo = mkBo(110L, "1", "B-001");
        bo.setOperatorId(8888L);
        service.recordBreeding(bo);

        ArgumentCaptor<PigBreeding> br = ArgumentCaptor.forClass(PigBreeding.class);
        verify(breedingMapper, times(1)).insert(br.capture());
        assertThat(br.getValue().getOperatorId()).isEqualTo(8888L);
    }

    @Test
    @DisplayName("校验: breedingType=1 缺 boarEarNo → ServiceException")
    void validate_type1_requires_boar() {
        Pig pig = mkSow(101L, PigLifecycle.DN);
        when(pigMapper.selectById(101L)).thenReturn(pig);

        BreedingBo bo = mkBo(101L, "1", null);
        assertThatThrownBy(() -> service.recordBreeding(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("breeding.boar_ear");
        verify(breedingMapper, never()).insert(any(PigBreeding.class));
        verify(pigCoreService, never()).fireEvent(any());
    }

    @Test
    @DisplayName("校验: breedingType=2 精液缺 semenCode → ServiceException")
    void validate_type2_requires_semen_code() {
        Pig pig = mkSow(102L, PigLifecycle.DN);
        when(pigMapper.selectById(102L)).thenReturn(pig);

        BreedingBo bo = mkBo(102L, "2", null);
        // FIX-BREEDING-001 #21：精液（!=1）改纯字典下拉，不传 semenCode → 报 breeding.semen_code.required
        assertThatThrownBy(() -> service.recordBreeding(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("breeding.semen_code");
    }

    @Test
    @DisplayName("r181: 本场公猪配种父母系组合未在 breed_config 登记 → 拦截「暂不支持这样配种」")
    void validate_breedConfig_missing_combo_blocks() {
        Pig sow = mkSow(103L, PigLifecycle.DN);
        sow.setPigBreedCode("01");
        sow.setPigStrainCode("01");
        when(pigMapper.selectById(103L)).thenReturn(sow);

        Pig boar = new Pig();
        boar.setId(900L);
        boar.setEarNo("B-001");
        boar.setPigBreedCode("02");
        boar.setPigStrainCode("02");
        when(pigMapper.selectOne(any())).thenReturn(boar);
        // breed_config 无该组合 → selectCount 返 0 → 拦截
        when(breedConfigMapper.selectCount(any())).thenReturn(0L);

        BreedingBo bo = mkBo(103L, "1", "B-001");
        assertThatThrownBy(() -> service.recordBreeding(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("暂不支持这样配种");
        verify(breedingMapper, never()).insert(any(PigBreeding.class));
        verify(pigCoreService, never()).fireEvent(any());
    }

    @Test
    @DisplayName("非法 transition: pig.current_status=END → fireEvent 抛 ServiceException 透传")
    void invalidTransition_terminalState_throws() {
        Pig pig = mkSow(103L, PigLifecycle.END);
        when(pigMapper.selectById(103L)).thenReturn(pig);
        // breeding INSERT 先成功，再到 fireEvent；mock fireEvent 抛
        when(pigCoreService.fireEvent(any()))
            .thenThrow(new ServiceException("pig.state.terminal"));

        BreedingBo bo = mkBo(103L, "1", "B-001");
        assertThatThrownBy(() -> service.recordBreeding(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.state.terminal");
    }

    @Test
    @DisplayName("pig 不存在 → ServiceException")
    void pigNotFound() {
        when(pigMapper.selectById(999L)).thenReturn(null);

        BreedingBo bo = mkBo(999L, "1", "B-001");
        assertThatThrownBy(() -> service.recordBreeding(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.not_found");
        verify(breedingMapper, never()).insert(any(PigBreeding.class));
        verify(pigCoreService, never()).fireEvent(any());
    }
}
