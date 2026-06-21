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
import org.dromara.djs.breed.core.service.EarNoAllocator;
import org.dromara.djs.breed.breeding.mapper.BreedConfigMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PigEarTagServiceImpl} 单元测试（BRD-EVENT-003）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>happy path：批量贴 3 头 → 3 行 pig_info（piglet）+ 3 行 pigletno + 0 行 status_record</li>
 *   <li><b>公母混批耳号编入性别段 + 全场连号不撞号 + 按原始索引回填</b>（ADR-0011 §2.5，本次核心）</li>
 *   <li>父猪耳号反查（farrow.breeding_id → boar_ear_no）+ 育种配置仔代码</li>
 *   <li>超量校验 / farrow 不存在 / 母猪不存在</li>
 *   <li>statByFarrow：返活产 / 已贴 / 待贴 + 清单</li>
 *   <li>previewEarNos：公母两组前缀不同、共享全场起点连续排号（与正式落库同口径）</li>
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
    private EarNoAllocator earNoAllocator;
    @Mock
    private BreedConfigMapper breedConfigMapper;

    private PigEarTagServiceImpl service;

    @BeforeEach
    void setup() {
        service = new PigEarTagServiceImpl(pigMapper, pigletnoMapper, farrowMapper, earNoAllocator, breedConfigMapper);
        // 仔代品系=4 / 品种=04（继承母猪，父猪未命中育种配置）：公前缀 -1- / 母前缀 -2-
        when(earNoAllocator.buildPrefix(eq("4"), eq("04"), eq("M"), any(LocalDate.class)))
            .thenReturn("4-04-1-260508");
        when(earNoAllocator.buildPrefix(eq("4"), eq("04"), eq("F"), any(LocalDate.class)))
            .thenReturn("4-04-2-260508");
    }

    private Pig mkSow() {
        Pig p = new Pig();
        p.setId(101L);
        p.setEarNo("01A12605001");
        p.setPigSex("F");
        p.setPigType("sow");
        // 仔猪品系/品种继承母猪（ADR-0011 位码：品系=4 杜洛克 / 品种=04 杜洛克）
        p.setPigBreedCode("04");
        p.setPigStrainCode("4");
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

    private PigletEarTagItem mkItem(String sex, BigDecimal weight) {
        PigletEarTagItem it = new PigletEarTagItem();
        it.setPigletSex(sex);
        it.setBirthWeight(weight);
        return it;
    }

    @Test
    @DisplayName("batchTag 公母混批 → 每头耳号编入正确性别段 + 全场连号不撞号 + 按原始索引回填")
    void batchTag_mixedSex_earNoHasSexSegment_consecutive_byIndex() {
        PigFarrow farrow = mkFarrow(900L, 10, 800L);
        when(farrowMapper.selectById(900L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());
        when(pigletnoMapper.selectCount(any())).thenReturn(0L);
        when(farrowMapper.selectBoarEarByBreedingId(800L)).thenReturn("01B12605900");
        // 父猪查不到（selectOne 未 mock，LENIENT 返 null）→ 仔代品种/品系回落继承母猪（04/4）。

        // 关键：allocateBatchByPrefixes 收到的 prefixes 必须按 piglets 原始索引排列（公-1-/母-2-），
        // 序号在全场范围内整批连号（004..006）—— 用真实拼号逻辑模拟，断言 service 传入前缀顺序正确。
        ArgumentCaptor<List<String>> prefixCaptor = ArgumentCaptor.forClass(List.class);
        when(earNoAllocator.allocateBatchByPrefixes(prefixCaptor.capture(), any(LocalDate.class)))
            .thenAnswer(inv -> {
                List<String> prefixes = inv.getArgument(0);
                // 模拟分配器：当天全场 max=3 → 整批从 004 起连号，每头套各自前缀
                long start = 4L;
                java.util.List<String> out = new java.util.ArrayList<>();
                for (int i = 0; i < prefixes.size(); i++) {
                    out.add(prefixes.get(i) + "-" + String.format("%03d", start + i));
                }
                return out;
            });

        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(900L);
        // 原始顺序：[公, 母, 公] —— 故意非排序，验证按原始索引回填而非性别分组顺序
        bo.setPiglets(List.of(
            mkItem("M", new BigDecimal("1.45")),
            mkItem("F", new BigDecimal("1.32")),
            mkItem("M", null)
        ));

        List<PigletEarTagVo> result = service.batchTag(bo);

        assertThat(result).hasSize(3);

        // 1) service 传给分配器的前缀列表按原始索引：公前缀 / 母前缀 / 公前缀
        List<String> sentPrefixes = prefixCaptor.getValue();
        assertThat(sentPrefixes).containsExactly("4-04-1-260508", "4-04-2-260508", "4-04-1-260508");

        // 2) 落库 pig 行：按原始索引顺序的耳号 = 公(004) / 母(005) / 公(006)，性别段与 pigletSex 一致、序号全场连续不撞
        ArgumentCaptor<Pig> pigCaptor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper, times(3)).insert(pigCaptor.capture());
        List<Pig> pigs = pigCaptor.getAllValues();
        assertThat(pigs.get(0).getEarNo()).isEqualTo("4-04-1-260508-004");
        assertThat(pigs.get(0).getPigSex()).isEqualTo("M");
        assertThat(pigs.get(1).getEarNo()).isEqualTo("4-04-2-260508-005");
        assertThat(pigs.get(1).getPigSex()).isEqualTo("F");
        assertThat(pigs.get(2).getEarNo()).isEqualTo("4-04-1-260508-006");
        assertThat(pigs.get(2).getPigSex()).isEqualTo("M");

        // 3) 性别段正确性硬断言：公耳号第 3 段 = 1，母耳号第 3 段 = 2
        for (Pig p : pigs) {
            String sexSeg = p.getEarNo().split("-")[2];
            assertThat(sexSeg).isEqualTo("M".equals(p.getPigSex()) ? "1" : "2");
        }

        // 4) 全批序号全场连续唯一（004/005/006，无重复、无空洞）→ 不撞号
        List<Long> seqs = pigs.stream()
            .map(p -> Long.parseLong(p.getEarNo().substring(p.getEarNo().lastIndexOf('-') + 1)))
            .sorted()
            .toList();
        assertThat(seqs).containsExactly(4L, 5L, 6L);
        assertThat(pigs.stream().map(Pig::getEarNo).distinct().count()).isEqualTo(3L);

        // 5) 整批一次性分配（单锁单 max，不按性别两次 allocate → 不会两组各读 max 撞号）
        verify(earNoAllocator, times(1)).allocateBatchByPrefixes(anyList(), any(LocalDate.class));
        verify(earNoAllocator, times(0)).allocate(any(), any(), any(), any(), anyInt());

        // pigletno.insert 3 次，性别段透传
        verify(pigletnoMapper, times(3)).insert(any(PigPigletno.class));
        verify(farrowMapper, times(1)).selectBoarEarByBreedingId(800L);
    }

    @Test
    @DisplayName("batchTag 全公批 → 全部 -1- 性别段，连号正确")
    void batchTag_allMale_sexSeg1() {
        PigFarrow farrow = mkFarrow(908L, 5, null);
        when(farrowMapper.selectById(908L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());
        when(pigletnoMapper.selectCount(any())).thenReturn(0L);
        when(earNoAllocator.allocateBatchByPrefixes(anyList(), any(LocalDate.class)))
            .thenReturn(List.of("4-04-1-260508-001", "4-04-1-260508-002"));

        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(908L);
        bo.setPiglets(List.of(mkItem("M", null), mkItem("M", null)));

        service.batchTag(bo);

        ArgumentCaptor<Pig> pigCap = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper, times(2)).insert(pigCap.capture());
        assertThat(pigCap.getAllValues()).allSatisfy(p ->
            assertThat(p.getEarNo().split("-")[2]).isEqualTo("1"));
    }

    @Test
    @DisplayName("batchTag 父猪命中育种配置 → 仔代品种/品系取 cub_code 透传 buildPrefix")
    void batchTag_cubBreedStrainFromConfig() {
        PigFarrow farrow = mkFarrow(905L, 5, 805L);
        when(farrowMapper.selectById(905L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow()); // 母 品种04/品系4
        when(pigletnoMapper.selectCount(any())).thenReturn(0L);
        when(farrowMapper.selectBoarEarByBreedingId(805L)).thenReturn("BOAR-1");
        // 父猪 品种01/品系1
        Pig boar = new Pig();
        boar.setPigBreedCode("01");
        boar.setPigStrainCode("1");
        when(pigMapper.selectOne(any())).thenReturn(boar);
        // 育种配置：品种(breed_strain=1) 母04×父01→cub 17；品系(breed_strain=2) 母04×父01→cub 17
        org.dromara.djs.breed.breeding.domain.BreedConfig cfgBreed = new org.dromara.djs.breed.breeding.domain.BreedConfig();
        cfgBreed.setCubCode("17");
        org.dromara.djs.breed.breeding.domain.BreedConfig cfgStrain = new org.dromara.djs.breed.breeding.domain.BreedConfig();
        cfgStrain.setCubCode("17");
        when(breedConfigMapper.selectOne(any())).thenReturn(cfgBreed, cfgStrain);
        // 仔代码 17/17 → 母仔猪前缀
        when(earNoAllocator.buildPrefix(eq("17"), eq("17"), eq("F"), any(LocalDate.class)))
            .thenReturn("17-17-2-260508");
        when(earNoAllocator.allocateBatchByPrefixes(anyList(), any(LocalDate.class)))
            .thenReturn(List.of("17-17-2-260508-001"));

        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(905L);
        bo.setPiglets(List.of(mkItem("F", null)));

        service.batchTag(bo);

        ArgumentCaptor<Pig> pigCap = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).insert(pigCap.capture());
        assertThat(pigCap.getValue().getPigBreedCode()).isEqualTo("17");
        assertThat(pigCap.getValue().getPigStrainCode()).isEqualTo("17");
        verify(earNoAllocator).buildPrefix(eq("17"), eq("17"), eq("F"), any(LocalDate.class));
    }

    @Test
    @DisplayName("batchTag 无父本（breedingId null）→ 品种/品系继承母猪")
    void batchTag_inheritsStrainBreedFromMother() {
        PigFarrow farrow = mkFarrow(901L, 5, null);
        when(farrowMapper.selectById(901L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());
        when(pigletnoMapper.selectCount(any())).thenReturn(0L);
        when(earNoAllocator.allocateBatchByPrefixes(anyList(), any(LocalDate.class)))
            .thenReturn(List.of("4-04-2-260508-001"));

        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(901L);
        bo.setPiglets(List.of(mkItem("F", null)));

        service.batchTag(bo);

        // 前缀按仔代继承母猪码（4/04）+ 性别 F 算出
        verify(earNoAllocator).buildPrefix(eq("4"), eq("04"), eq("F"), any(LocalDate.class));
        verify(earNoAllocator).allocateBatchByPrefixes(anyList(), any(LocalDate.class));
    }

    @Test
    @DisplayName("batchTag breedingId 为 null（人工授精无公猪） → fatherEar 为 null")
    void batchTag_nullBreedingId_fatherEarNull() {
        PigFarrow farrow = mkFarrow(902L, 5, null);
        when(farrowMapper.selectById(902L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());
        when(pigletnoMapper.selectCount(any())).thenReturn(0L);
        when(earNoAllocator.allocateBatchByPrefixes(anyList(), any(LocalDate.class)))
            .thenReturn(List.of("4-04-1-260508-099"));

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
        // 越界时不应该再去分配耳号 / 插数据
        verify(earNoAllocator, times(0)).allocateBatchByPrefixes(anyList(), any(LocalDate.class));
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
        l1.setPigletEarNo("4-04-1-260508-001");
        l1.setMotherEarNo("01A12605001");
        l1.setFarrowId(905L);
        l1.setTagDate(LocalDateTime.now());
        l1.setPigletSex("M");
        l1.setPigId(201L);
        PigPigletno l2 = new PigPigletno();
        l2.setId(2L);
        l2.setPigletEarNo("4-04-2-260508-002");
        l2.setMotherEarNo("01A12605001");
        l2.setFarrowId(905L);
        l2.setTagDate(LocalDateTime.now());
        l2.setPigletSex("F");
        l2.setPigId(202L);
        PigPigletno l3 = new PigPigletno();
        l3.setId(3L);
        l3.setPigletEarNo("4-04-1-260508-003");
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

    @Test
    @DisplayName("batchTag 写库 ear_tag=全号（含性别段）+ operatorId 取 bo 值")
    void batchTag_writesEarTagFull_andOperatorIdFromBo() {
        PigFarrow farrow = mkFarrow(910L, 5, null);
        when(farrowMapper.selectById(910L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());
        when(pigletnoMapper.selectCount(any())).thenReturn(0L);
        when(earNoAllocator.allocateBatchByPrefixes(anyList(), any(LocalDate.class)))
            .thenReturn(List.of("4-04-1-260508-011"));

        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(910L);
        bo.setOperatorId(777L);
        bo.setPiglets(List.of(mkItem("M", new BigDecimal("1.5"))));

        service.batchTag(bo);

        // ear_no 与 ear_tag 均存全号（全号含性别段，库内不拆短号）
        ArgumentCaptor<Pig> pigCap = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper).insert(pigCap.capture());
        assertThat(pigCap.getValue().getEarNo()).isEqualTo("4-04-1-260508-011");
        assertThat(pigCap.getValue().getEarTag()).isEqualTo("4-04-1-260508-011");

        // operatorId 取 bo 传入值（不回落登录态）
        ArgumentCaptor<PigPigletno> logCap = ArgumentCaptor.forClass(PigPigletno.class);
        verify(pigletnoMapper).insert(logCap.capture());
        assertThat(logCap.getValue().getOperatorId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("previewEarNos 公母前缀不同、共享全场起点连续排号（公占前段、母接后段，与正式落库同口径）")
    void previewEarNos_sharedSeqAcrossSexPrefixes() {
        PigFarrow farrow = mkFarrow(911L, 10, null);
        when(farrowMapper.selectById(911L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());
        // 当天全场 max 已到 003 → 下一号 004；公占 004/005，母接 006
        when(earNoAllocator.nextSeqForDate(any(LocalDate.class))).thenReturn(4L);

        var vo = service.previewEarNos(911L, 2, 1);

        // 公组：公前缀 + 004/005；母组：母前缀 + 006 —— 序号在全场连续不交叉
        assertThat(vo.getMaleEarNos())
            .containsExactly("4-04-1-260508-004", "4-04-1-260508-005");
        assertThat(vo.getFemaleEarNos())
            .containsExactly("4-04-2-260508-006");
        // 预览仅读不分配，不应触碰真实分配
        verify(earNoAllocator, times(0)).allocateBatchByPrefixes(anyList(), any(LocalDate.class));
        verify(earNoAllocator, times(0)).allocate(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("previewEarNos 母数为 0 → 母组空，公组从全场起点排满")
    void previewEarNos_zeroFemaleGroupEmpty() {
        PigFarrow farrow = mkFarrow(912L, 5, null);
        when(farrowMapper.selectById(912L)).thenReturn(farrow);
        when(pigMapper.selectById(101L)).thenReturn(mkSow());
        when(earNoAllocator.nextSeqForDate(any(LocalDate.class))).thenReturn(1L);

        var vo = service.previewEarNos(912L, 1, 0);

        assertThat(vo.getMaleEarNos()).containsExactly("4-04-1-260508-001");
        assertThat(vo.getFemaleEarNos()).isEmpty();
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
