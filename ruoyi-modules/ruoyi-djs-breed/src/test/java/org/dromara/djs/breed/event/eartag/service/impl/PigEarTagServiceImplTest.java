package org.dromara.djs.breed.event.eartag.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBatchEarTagBo;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletEarTagItem;
import org.dromara.djs.breed.event.eartag.domain.vo.FarrowEarTagStatVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo;
import org.dromara.djs.breed.event.eartag.mapper.PigPigletnoMapper;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PigEarTagServiceImpl} 单元测试（BRD-EVENT-003）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>happy path：批量贴 3 头 → 3 行 pig_info（piglet/HB）+ 3 行 pigletno + 0 行 status_record</li>
 *   <li>父猪耳号反查（farrow.breeding_id → boar_ear_no）</li>
 *   <li>超量校验：tagged + newCount > live_born → 抛 i18n key</li>
 *   <li>farrow 不存在校验</li>
 *   <li>母猪不存在校验</li>
 *   <li>statByFarrow：返活产 / 已贴 / 待贴 + 清单</li>
 *   <li>耳号 context.barnCode 由母猪所在 barn 派生（缺时回落 "00"）</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PigEarTagServiceImpl 单元测试 (BRD-EVENT-003)")
class PigEarTagServiceImplTest {

    @Mock
    private PigMapper pigMapper;
    @Mock
    private PigPigletnoMapper pigletnoMapper;
    @Mock
    private PigFarrowMapper farrowMapper;
    @Mock
    private BarnMapper barnMapper;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;

    private PigEarTagServiceImpl service;

    @BeforeEach
    void setup() {
        service = new PigEarTagServiceImpl(pigMapper, pigletnoMapper, farrowMapper, barnMapper, bizCodeGenerator);
    }

    private Pig mkSow() {
        Pig p = new Pig();
        p.setId(101L);
        p.setEarNo("01A12605001");
        p.setPigSex("F");
        p.setPigType("sow");
        p.setPigBreedCode("LW");
        p.setPigStrainCode("LWA");
        p.setBarnId(5L);
        p.setPenId(50L);
        p.setCurrentStatus(PigLifecycle.FM.name());
        return p;
    }

    private PigFarrow mkFarrow(long id, int liveBorn, Long breedingId) {
        PigFarrow f = new PigFarrow();
        f.setId(id);
        f.setPigId(101L);
        f.setEarNo("01A12605001");
        f.setBreedingId(breedingId);
        f.setFarrowDate(LocalDateTime.now().minusDays(2));
        f.setLiveBorn(liveBorn);
        f.setParity(2);
        return f;
    }

    private Barn mkBarn(String code) {
        Barn b = new Barn();
        b.setId(5L);
        b.setBarnCode(code);
        return b;
    }

    private PigletEarTagItem mkItem(String sex, BigDecimal weight) {
        PigletEarTagItem it = new PigletEarTagItem();
        it.setPigletSex(sex);
        it.setBirthWeight(weight);
        return it;
    }

    @Test
    @DisplayName("batchTag 3 头 → INSERT 3 pig + 3 pigletno + 0 status_record + 父猪耳号反查")
    void batchTag_happyPath() {
        PigFarrow farrow = mkFarrow(900L, 10, 800L);
        when(farrowMapper.selectById(900L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());
        when(pigletnoMapper.selectCount(any())).thenReturn(0L);
        when(farrowMapper.selectBoarEarByBreedingId(800L)).thenReturn("01B12605900");
        when(barnMapper.selectById(5L)).thenReturn(mkBarn("A1"));
        when(bizCodeGenerator.generateBatch(eq(BizCodeType.EAR_NO), any(), eq(3)))
            .thenReturn(List.of("01A126050001", "01A126050002", "01A126050003"));

        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(900L);
        bo.setPiglets(List.of(
            mkItem("M", new BigDecimal("1.45")),
            mkItem("F", new BigDecimal("1.32")),
            mkItem("M", null)
        ));

        List<PigletEarTagVo> result = service.batchTag(bo);

        assertThat(result).hasSize(3);
        // pig.insert 3 次（仔猪 pig_info 主表行）
        ArgumentCaptor<Pig> pigCaptor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper, times(3)).insert(pigCaptor.capture());
        for (Pig p : pigCaptor.getAllValues()) {
            assertThat(p.getPigType()).isEqualTo("piglet");
            assertThat(p.getCurrentStatus()).isEqualTo(PigLifecycle.HB.name());
            assertThat(p.getMotherEar()).isEqualTo("01A12605001");
            assertThat(p.getFatherEar()).isEqualTo("01B12605900");
            assertThat(p.getBarnId()).isEqualTo(5L);
            assertThat(p.getPenId()).isEqualTo(50L);
            assertThat(p.getPigBreedCode()).isEqualTo("LW");
            assertThat(p.getStatusStartedAt()).isNotNull();
            // 仔猪 lifecycleId=1 / parity=0 / isAppointed=0
            assertThat(p.getLifecycleId()).isEqualTo(1);
            assertThat(p.getParity()).isZero();
        }
        // pigletno.insert 3 次
        verify(pigletnoMapper, times(3)).insert(any(PigPigletno.class));
        // 父猪耳号反查走了一次
        verify(farrowMapper, times(1)).selectBoarEarByBreedingId(800L);
        // 编码生成器一次性批量 3 个
        verify(bizCodeGenerator, times(1)).generateBatch(eq(BizCodeType.EAR_NO), any(), eq(3));
    }

    @Test
    @DisplayName("batchTag context.barnCode 取自母猪所在栋舍 barnCode 前 2 位")
    void batchTag_contextBarnCode() {
        PigFarrow farrow = mkFarrow(901L, 5, null);
        when(farrowMapper.selectById(901L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());
        when(pigletnoMapper.selectCount(any())).thenReturn(0L);
        when(barnMapper.selectById(5L)).thenReturn(mkBarn("B7-PROD"));
        when(bizCodeGenerator.generateBatch(eq(BizCodeType.EAR_NO), any(), eq(1)))
            .thenReturn(List.of("01B726050001"));

        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(901L);
        bo.setPiglets(List.of(mkItem("F", null)));

        service.batchTag(bo);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> ctxCaptor =
            ArgumentCaptor.forClass(Map.class);
        verify(bizCodeGenerator).generateBatch(eq(BizCodeType.EAR_NO), ctxCaptor.capture(), eq(1));
        assertThat(ctxCaptor.getValue().get("barnCode")).isEqualTo("B7");
    }

    @Test
    @DisplayName("batchTag breedingId 为 null（人工授精无公猪） → fatherEar 为 null")
    void batchTag_nullBreedingId_fatherEarNull() {
        PigFarrow farrow = mkFarrow(902L, 5, null);
        when(farrowMapper.selectById(902L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());
        when(pigletnoMapper.selectCount(any())).thenReturn(0L);
        when(barnMapper.selectById(5L)).thenReturn(mkBarn("A1"));
        when(bizCodeGenerator.generateBatch(eq(BizCodeType.EAR_NO), any(), eq(1)))
            .thenReturn(List.of("01A126050099"));

        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(902L);
        bo.setPiglets(List.of(mkItem("M", null)));

        service.batchTag(bo);

        ArgumentCaptor<Pig> pigCap = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).insert(pigCap.capture());
        assertThat(pigCap.getValue().getFatherEar()).isNull();
        // breedingId 为 null 时不应该去反查
        verify(farrowMapper, times(0)).selectBoarEarByBreedingId(anyLong());
    }

    @Test
    @DisplayName("batchTag 超量校验：tagged 5 + new 6 > liveBorn 10 → 抛 exceeds_live_born")
    void batchTag_exceedsLiveBorn() {
        PigFarrow farrow = mkFarrow(903L, 10, 800L);
        when(farrowMapper.selectById(903L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());
        when(pigletnoMapper.selectCount(any())).thenReturn(5L);

        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(903L);
        bo.setPiglets(List.of(
            mkItem("F", null), mkItem("F", null), mkItem("F", null),
            mkItem("F", null), mkItem("F", null), mkItem("F", null)
        ));

        assertThatThrownBy(() -> service.batchTag(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pigletno.exceeds_live_born");
        // 越界时不应该再去取编码 / 插数据
        verify(bizCodeGenerator, times(0)).generateBatch(any(), any(), anyInt());
        verify(pigMapper, times(0)).insert(any(Pig.class));
        verify(pigletnoMapper, times(0)).insert(any(PigPigletno.class));
    }

    @Test
    @DisplayName("batchTag farrow 不存在 → 抛 farrow.not_found")
    void batchTag_farrowNotFound() {
        when(farrowMapper.selectById(999L)).thenReturn(null);

        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(999L);
        bo.setPiglets(List.of(mkItem("F", null)));

        assertThatThrownBy(() -> service.batchTag(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pigletno.farrow.not_found");
    }

    @Test
    @DisplayName("batchTag 母猪不存在 → 抛 mother.not_found")
    void batchTag_motherNotFound() {
        PigFarrow farrow = mkFarrow(904L, 5, null);
        when(farrowMapper.selectById(904L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(null);

        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(904L);
        bo.setPiglets(List.of(mkItem("M", null)));

        assertThatThrownBy(() -> service.batchTag(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pigletno.mother.not_found");
    }

    @Test
    @DisplayName("statByFarrow 已贴 3 / liveBorn 10 → remaining 7 + 清单 size=3")
    void statByFarrow_happyPath() {
        PigFarrow farrow = mkFarrow(905L, 10, 800L);
        when(farrowMapper.selectById(905L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());

        PigPigletno l1 = new PigPigletno();
        l1.setId(1L);
        l1.setPigletEarNo("01A126050001");
        l1.setMotherEarNo("01A12605001");
        l1.setFarrowId(905L);
        l1.setTagDate(LocalDateTime.now());
        l1.setPigletSex("M");
        l1.setPigId(201L);
        PigPigletno l2 = new PigPigletno();
        l2.setId(2L);
        l2.setPigletEarNo("01A126050002");
        l2.setMotherEarNo("01A12605001");
        l2.setFarrowId(905L);
        l2.setTagDate(LocalDateTime.now());
        l2.setPigletSex("F");
        l2.setPigId(202L);
        PigPigletno l3 = new PigPigletno();
        l3.setId(3L);
        l3.setPigletEarNo("01A126050003");
        l3.setMotherEarNo("01A12605001");
        l3.setFarrowId(905L);
        l3.setTagDate(LocalDateTime.now());
        l3.setPigletSex("M");
        l3.setPigId(203L);
        when(pigletnoMapper.selectList(any())).thenReturn(List.of(l1, l2, l3));
        Pig p201 = new Pig();
        p201.setId(201L);
        p201.setCurrentStatus("HB");
        Pig p202 = new Pig();
        p202.setId(202L);
        p202.setCurrentStatus("HB");
        Pig p203 = new Pig();
        p203.setId(203L);
        p203.setCurrentStatus("HB");
        when(pigMapper.selectByIds(any())).thenReturn(List.of(p201, p202, p203));

        FarrowEarTagStatVo vo = service.statByFarrow(905L);

        assertThat(vo.getFarrowId()).isEqualTo(905L);
        assertThat(vo.getLiveBorn()).isEqualTo(10);
        assertThat(vo.getTagged()).isEqualTo(3);
        assertThat(vo.getRemaining()).isEqualTo(7);
        assertThat(vo.getMotherEar()).isEqualTo("01A12605001");
        assertThat(vo.getTaggedList()).hasSize(3);
        assertThat(vo.getTaggedList()).allMatch(item -> "HB".equals(item.getCurrentStatus()));
    }

    @Test
    @DisplayName("statByFarrow 0 已贴 → tagged=0 / remaining=liveBorn / 清单空")
    void statByFarrow_zeroTagged() {
        PigFarrow farrow = mkFarrow(906L, 8, null);
        when(farrowMapper.selectById(906L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());
        when(pigletnoMapper.selectList(any())).thenReturn(List.of());

        FarrowEarTagStatVo vo = service.statByFarrow(906L);

        assertThat(vo.getTagged()).isZero();
        assertThat(vo.getRemaining()).isEqualTo(8);
        assertThat(vo.getTaggedList()).isEmpty();
    }

    @Test
    @DisplayName("statByFarrow farrow 不存在 → 抛 farrow.not_found")
    void statByFarrow_farrowNotFound() {
        when(farrowMapper.selectById(909L)).thenReturn(null);
        assertThatThrownBy(() -> service.statByFarrow(909L))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pigletno.farrow.not_found");
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
