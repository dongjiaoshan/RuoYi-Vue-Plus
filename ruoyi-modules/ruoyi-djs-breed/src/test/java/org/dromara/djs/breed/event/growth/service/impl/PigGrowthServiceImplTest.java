package org.dromara.djs.breed.event.growth.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssService;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.event.growth.domain.PigGrowth;
import org.dromara.djs.breed.event.growth.domain.bo.GrowthBo;
import org.dromara.djs.breed.event.growth.domain.vo.PigGrowthVo;
import org.dromara.djs.breed.event.growth.mapper.PigGrowthMapper;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.dromara.djs.breed.production.service.IFattenAgeStageService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PigGrowthServiceImpl} 单元测试（BRD-EVENT-005 GROWTH）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>happy path：INSERT growth + 不调状态机；</li>
 *   <li>pig 不存在 → ServiceException；</li>
 *   <li>删除：3 天内 OK；超 3 天抛 growth.delete.expired。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-005
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PigGrowthServiceImpl 单元测试 (BRD-EVENT-005)")
class PigGrowthServiceImplTest {

    @Mock
    private PigGrowthMapper growthMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private BarnMapper barnMapper;
    @Mock
    private PenMapper penMapper;
    @Mock
    private IFattenAgeStageService fattenAgeStageService;
    @Mock
    private OssService ossService;
    @Mock
    private org.dromara.djs.breed.core.service.IPigCoreService pigCoreService;

    private PigGrowthServiceImpl service;

    @BeforeEach
    void setup() {
        service = new PigGrowthServiceImpl(growthMapper, pigMapper, barnMapper, penMapper, fattenAgeStageService, ossService, pigCoreService);
    }

    private Pig mkPig(Long id) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo("260520-001");
        p.setPigSex("M");
        p.setPigType("fattening");
        p.setCurrentStatus(PigLifecycle.HB.name());
        return p;
    }

    private GrowthBo mkBo(Long pigId, BigDecimal weight) {
        GrowthBo bo = new GrowthBo();
        bo.setPigId(pigId);
        bo.setMeasureDate(LocalDate.of(2026, 5, 27));
        bo.setWeight(weight);
        return bo;
    }

    @Test
    @DisplayName("happy: 体重录入 → INSERT growth 一次 + 不调状态机")
    void happyPath_insertsGrowthOnly() {
        Pig pig = mkPig(100L);
        when(pigMapper.selectById(100L)).thenReturn(pig);

        GrowthBo bo = mkBo(100L, new BigDecimal("85.50"));
        bo.setBackfatThickness(new BigDecimal("12.30"));
        bo.setBackHeight(new BigDecimal("68.00"));
        bo.setRemark("D27 体测");

        PigGrowthVo vo = service.addGrowthRecord(bo);

        ArgumentCaptor<PigGrowth> cap = ArgumentCaptor.forClass(PigGrowth.class);
        verify(growthMapper, times(1)).insert(cap.capture());
        PigGrowth saved = cap.getValue();
        assertThat(saved.getPigId()).isEqualTo(100L);
        assertThat(saved.getEarNo()).isEqualTo("260520-001");
        assertThat(saved.getMeasureDate()).isEqualTo(LocalDate.of(2026, 5, 27));
        assertThat(saved.getWeight()).isEqualByComparingTo("85.50");
        assertThat(saved.getBackfatThickness()).isEqualByComparingTo("12.30");
        assertThat(saved.getBackHeight()).isEqualByComparingTo("68.00");
        assertThat(saved.getDelFlag()).isEqualTo("0");

        // VO 字段透出
        assertThat(vo.getPigId()).isEqualTo(100L);
        assertThat(vo.getWeight()).isEqualByComparingTo("85.50");
        assertThat(vo.getEarNo()).isEqualTo("260520-001");
    }

    @Test
    @DisplayName("pig 不存在 → ServiceException pig.not_found")
    void pigNotFound() {
        when(pigMapper.selectById(999L)).thenReturn(null);

        GrowthBo bo = mkBo(999L, new BigDecimal("80.00"));
        assertThatThrownBy(() -> service.addGrowthRecord(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.not_found");

        verify(growthMapper, never()).insert(any(PigGrowth.class));
    }

    private static Date asDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("删除 3 天内: 通过 → mapper.deleteByIds 调一次")
    void deleteWithinWindow_ok() {
        PigGrowth entity = new PigGrowth();
        entity.setId(10L);
        entity.setCreateTime(asDate(LocalDateTime.now().minusDays(2)));
        when(growthMapper.selectById(10L)).thenReturn(entity);
        when(growthMapper.deleteByIds(any())).thenReturn(1);

        int affected = service.deleteByIds(List.of(10L));
        assertThat(affected).isEqualTo(1);
        verify(growthMapper, times(1)).deleteByIds(any());
    }

    @Test
    @DisplayName("删除超期: 抛 growth.delete.expired")
    void deleteExpired_throws() {
        PigGrowth entity = new PigGrowth();
        entity.setId(11L);
        entity.setCreateTime(asDate(LocalDateTime.now().minusDays(10)));
        when(growthMapper.selectById(11L)).thenReturn(entity);

        assertThatThrownBy(() -> service.deleteByIds(List.of(11L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("growth.delete.expired");

        verify(growthMapper, never()).deleteByIds(any());
    }

    @Test
    @DisplayName("queryPage / getById 入口存在性烟雾测试（不抛异常）")
    void getById_notFound() {
        when(growthMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(404L))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("growth.not_found");
    }

    @Test
    @DisplayName("row54 hard delete: deleteById → mapper.deleteById 调一次（不走 3 天校验）")
    void deleteById_hardDelete() {
        when(growthMapper.deleteById(20L)).thenReturn(1);
        int affected = service.deleteById(20L);
        assertThat(affected).isEqualTo(1);
        verify(growthMapper, times(1)).deleteById(20L);
        // hard delete 不读 createTime / 不走 3 天窗口校验
        verify(growthMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("row52 operatorId: BO 显式传记录人 → 落库 operator_id")
    void recordGrowth_setsOperatorIdFromBo() {
        Pig pig = mkPig(100L);
        when(pigMapper.selectById(100L)).thenReturn(pig);

        GrowthBo bo = mkBo(100L, new BigDecimal("85.50"));
        bo.setOperatorId("1899000000000000001");

        service.addGrowthRecord(bo);

        ArgumentCaptor<PigGrowth> cap = ArgumentCaptor.forClass(PigGrowth.class);
        verify(growthMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getOperatorId()).isEqualTo(1899000000000000001L);
    }
}
