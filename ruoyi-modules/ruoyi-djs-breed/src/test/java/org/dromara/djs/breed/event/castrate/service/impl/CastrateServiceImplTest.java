package org.dromara.djs.breed.event.castrate.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.service.UserService;
import org.dromara.djs.breed.event.castrate.domain.CastrateRecord;
import org.dromara.djs.breed.event.castrate.domain.bo.CastrateBo;
import org.dromara.djs.breed.event.castrate.domain.vo.CastrateRecordVo;
import org.dromara.djs.breed.event.castrate.mapper.CastrateRecordMapper;
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
 * {@link CastrateServiceImpl} 单元测试（BRD-EVENT-004 CASTRATE）。
 *
 * <p>状态机 NO_CHANGE：公猪空状态维持不变（ADR-0016 非种母猪无繁殖态）；service 层提前校验 pig_sex='M'。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CastrateServiceImpl 单元测试 (BRD-EVENT-004)")
class CastrateServiceImplTest {

    @Mock
    private CastrateRecordMapper castrateMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private IPigCoreService pigCoreService;
    @Mock
    private DictService dictService;
    @Mock
    private UserService userService;

    private CastrateServiceImpl service;

    @BeforeEach
    void setup() {
        service = new CastrateServiceImpl(castrateMapper, pigMapper, pigCoreService, dictService, userService);
    }

    /** 可阉割的公猪 = 育肥/仔猪公（R40：种公猪 boar 不可阉割，见 {@link #validate_boar_rejected}）。 */
    private Pig mkCastratableMale(Long id) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo("260520-M-001");
        p.setPigSex("M");
        p.setPigType("fattening");
        p.setCurrentStatus("");
        return p;
    }

    private Pig mkBoar(Long id) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo("260520-B-001");
        p.setPigSex("M");
        p.setPigType("boar");
        p.setCurrentStatus("");
        return p;
    }

    private Pig mkSow(Long id) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo("260520-F-001");
        p.setPigSex("F");
        p.setPigType("sow");
        p.setCurrentStatus(PigLifecycle.HB.name());
        return p;
    }

    private CastrateBo mkBo(Long pigId) {
        CastrateBo bo = new CastrateBo();
        bo.setPigId(pigId);
        bo.setCastrateDate(LocalDateTime.of(2026, 5, 27, 11, 0));
        return bo;
    }

    @Test
    @DisplayName("happy: 育肥公猪 CASTRATE → INSERT castrate + fireEvent(CASTRATE) 一次")
    void happyPath_castratableMale() {
        Pig pig = mkCastratableMale(300L);
        when(pigMapper.selectById(300L)).thenReturn(pig);

        service.recordCastrate(mkBo(300L));

        ArgumentCaptor<CastrateRecord> c = ArgumentCaptor.forClass(CastrateRecord.class);
        verify(castrateMapper, times(1)).insert(c.capture());
        assertThat(c.getValue().getEarNo()).isEqualTo("260520-M-001");

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo(PigStatusEvent.CASTRATE);
    }

    @Test
    @DisplayName("castrater: 阉割人员落库（BRD-FIX-MP-EVENT-LEAVE-IA-001 原型 87 字段）")
    void castrater_persisted() {
        Pig pig = mkCastratableMale(302L);
        when(pigMapper.selectById(302L)).thenReturn(pig);

        CastrateBo bo = mkBo(302L);
        bo.setCastrater("  张师傅  ");

        CastrateRecordVo vo = service.recordCastrate(bo);

        ArgumentCaptor<CastrateRecord> c = ArgumentCaptor.forClass(CastrateRecord.class);
        verify(castrateMapper, times(1)).insert(c.capture());
        // trim 后落库
        assertThat(c.getValue().getCastrater()).isEqualTo("张师傅");
        // VO 回显
        assertThat(vo.getCastrater()).isEqualTo("张师傅");
    }

    @Test
    @DisplayName("castrater: 空白阉割人员 → 落库 null（不存空串）")
    void castrater_blank_to_null() {
        Pig pig = mkCastratableMale(303L);
        when(pigMapper.selectById(303L)).thenReturn(pig);

        CastrateBo bo = mkBo(303L);
        bo.setCastrater("   ");

        service.recordCastrate(bo);

        ArgumentCaptor<CastrateRecord> c = ArgumentCaptor.forClass(CastrateRecord.class);
        verify(castrateMapper, times(1)).insert(c.capture());
        assertThat(c.getValue().getCastrater()).isNull();
    }

    @Test
    @DisplayName("性别校验: 母猪 CASTRATE → ServiceException(castrate.male_only)，不 INSERT 不 fireEvent")
    void validate_male_only() {
        Pig pig = mkSow(301L);
        when(pigMapper.selectById(301L)).thenReturn(pig);

        assertThatThrownBy(() -> service.recordCastrate(mkBo(301L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("castrate.male_only");
        verify(castrateMapper, never()).insert(any(CastrateRecord.class));
        verify(pigCoreService, never()).fireEvent(any());
    }

    @Test
    @DisplayName("类型校验: 种公猪 boar CASTRATE → ServiceException(种公猪不可阉割)，不 INSERT 不 fireEvent（R40：阉割对象仅公的仔猪/育肥猪）")
    void validate_boar_rejected() {
        Pig pig = mkBoar(304L);
        when(pigMapper.selectById(304L)).thenReturn(pig);

        assertThatThrownBy(() -> service.recordCastrate(mkBo(304L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("种公猪不可阉割");
        verify(castrateMapper, never()).insert(any(CastrateRecord.class));
        verify(pigCoreService, never()).fireEvent(any());
    }

    @Test
    @DisplayName("pig 不存在 → not_found")
    void pigNotFound() {
        when(pigMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.recordCastrate(mkBo(999L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.not_found");
    }
}
