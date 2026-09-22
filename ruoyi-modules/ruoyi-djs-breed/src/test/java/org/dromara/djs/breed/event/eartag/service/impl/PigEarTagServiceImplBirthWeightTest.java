package org.dromara.djs.breed.event.eartag.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.djs.breed.breeding.mapper.BreedConfigMapper;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.EarNoAllocator;
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBatchEarTagBo;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletEarTagItem;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBirthWeightBo;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBirthWeightItem;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo;
import org.dromara.djs.breed.event.eartag.mapper.PigPigletnoMapper;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
import org.dromara.djs.breed.farm.service.PenCountUpdater;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PigEarTagServiceImpl} 的 V6 行242/243 行为单测 —— 自动建档 + 出生重订正。
 *
 * <p>钉三件事（破了就是甲方当场能看见的错）：</p>
 * <ul>
 *   <li><b>建档头数</b>：公 = {@code farrow.male_count}、母 = {@code female_count}；两者皆 0 时整窝按公组铺
 *       {@code live_born}；{@code live_born}=0 一头都不建（分娩仍成立）。</li>
 *   <li><b>默认出生重</b>：取字典 {@code djs_piglet_default_weight} 的「仔猪出生重」；缺 / 非数字回落 2kg（D-0107）。</li>
 *   <li><b>订正幂等</b>：同一窝反复提交结果一致，且 pig_info 与 pigletno <b>两张表一起改</b>、窝级总重均重每次重算。</li>
 * </ul>
 *
 * <p>mapper 全 mock，但 {@code selectList / insert / updateById} 用一个内存 list 模拟真实库——
 * 否则「改完再读」这条链在 mock 下永远读到旧值，幂等断言会变成空断言。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PigEarTagServiceImpl 自动建档 + 出生重订正 (V6 行242/243)")
class PigEarTagServiceImplBirthWeightTest {

    private static final long FARROW_ID = 900L;
    private static final long MOTHER_ID = 101L;

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
    @Mock
    private PenCountUpdater penCountUpdater;
    @Mock
    private DictService dictService;
    @Mock
    private org.dromara.djs.breed.event.weaning.mapper.PigWeaningMapper weaningMapper;

    private PigEarTagServiceImpl service;

    /** 模拟 t_farm_pig_pigletno 的内存表：insert 进来、selectList 读出去、updateById 原地改。 */
    private final List<PigPigletno> pigletnoTable = new ArrayList<>();

    private long nextPigletnoId = 1L;

    @BeforeEach
    void setup() {
        service = new PigEarTagServiceImpl(pigMapper, pigletnoMapper, farrowMapper, earNoAllocator,
            breedConfigMapper, penCountUpdater, dictService, weaningMapper);

        when(earNoAllocator.buildPrefix(eq("4"), eq("04"), eq("M"), any(LocalDate.class)))
            .thenReturn("4-04-1-260508");
        when(earNoAllocator.buildPrefix(eq("4"), eq("04"), eq("F"), any(LocalDate.class)))
            .thenReturn("4-04-2-260508");
        when(earNoAllocator.allocateBatchByPrefixes(any(), any(LocalDate.class))).thenAnswer(inv -> {
            List<String> prefixes = inv.getArgument(0);
            List<String> out = new ArrayList<>(prefixes.size());
            for (int i = 0; i < prefixes.size(); i++) {
                out.add(prefixes.get(i) + "-" + String.format("%03d", i + 1));
            }
            return out;
        });
        when(pigletnoMapper.selectCount(any())).thenReturn(0L);
        when(pigMapper.selectById(MOTHER_ID)).thenReturn(mkSow());

        // 内存库：insert 落表、selectList 读快照、updateById 按 id 原地改出生重
        when(pigletnoMapper.insert(any(PigPigletno.class))).thenAnswer(inv -> {
            PigPigletno row = inv.getArgument(0);
            row.setId(nextPigletnoId++);
            pigletnoTable.add(row);
            return 1;
        });
        when(pigletnoMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(pigletnoTable));
        // D-0116：自动补建的判据改成「连软删的行一起数」——死光的窝不能被当成零档案老窝再补建一遍。
        // 假库里的行全是未删的，所以直接返回行数。
        when(pigletnoMapper.countByFarrowIgnoreDeleted(anyLong())).thenAnswer(inv -> pigletnoTable.size());
        when(pigletnoMapper.updateById(any(PigPigletno.class))).thenAnswer(inv -> {
            PigPigletno patch = inv.getArgument(0);
            for (PigPigletno row : pigletnoTable) {
                if (row.getId().equals(patch.getId())) {
                    row.setBirthWeight(patch.getBirthWeight());
                    return 1;
                }
            }
            return 0;
        });
    }

    private Pig mkSow() {
        Pig p = new Pig();
        p.setId(MOTHER_ID);
        p.setEarNo("01A12605001");
        p.setPigSex("F");
        p.setPigType("sow");
        p.setPigBreedCode("04");
        p.setPigStrainCode("4");
        p.setBarnId(5L);
        p.setPenId(50L);
        return p;
    }

    private PigFarrow mkFarrow(int liveBorn, Integer maleCount, Integer femaleCount) {
        PigFarrow f = new PigFarrow();
        f.setId(FARROW_ID);
        f.setPigId(MOTHER_ID);
        f.setEarNo("01A12605001");
        f.setFarrowDate(LocalDateTime.of(2026, 5, 8, 9, 0));
        f.setLiveBorn(liveBorn);
        f.setMaleCount(maleCount);
        f.setFemaleCount(femaleCount);
        f.setParity(2);
        when(farrowMapper.selectById(FARROW_ID)).thenReturn(f);
        return f;
    }

    // ==================== 行242：自动建档 ====================

    @Test
    @DisplayName("自动建档头数 = 公 male_count + 母 female_count，逐头写字典默认出生重")
    void autoCreate_countsBySexAndDictWeight() {
        PigFarrow farrow = mkFarrow(5, 2, 3);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("1.8");

        List<PigletEarTagVo> created = service.autoCreatePigletsForFarrow(farrow, 777L);

        assertThat(created).hasSize(5);
        ArgumentCaptor<Pig> pigCaptor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper, times(5)).insert(pigCaptor.capture());
        assertThat(pigCaptor.getAllValues()).extracting(Pig::getPigSex)
            .as("公母头数必须严格照窝级 male_count / female_count 铺")
            .containsExactly("M", "M", "F", "F", "F");
        assertThat(pigCaptor.getAllValues()).extracting(Pig::getBirthWeight)
            .as("每头出生重 = 字典「仔猪出生重」当前值（D-0107），并已按 2 位归一")
            .containsOnly(new BigDecimal("1.80"));
        assertThat(pigletnoTable).extracting(PigPigletno::getBirthWeight)
            .as("pigletno 与 pig_info 同写一个出生重，否则窝级汇总与详情页对不上")
            .containsOnly(new BigDecimal("1.80"));
        assertThat(pigletnoTable).extracting(PigPigletno::getOperatorId)
            .as("记录人员取分娩录入人员，不回落登录态")
            .containsOnly(777L);
        verify(penCountUpdater).increase(50L, 5);
    }

    @Test
    @DisplayName("字典没配 / 值非数字 → 出生重回落 2kg，不阻断分娩录入（D-0107 fallback）")
    void autoCreate_dictMissing_fallsBackTo2kg() {
        PigFarrow farrow = mkFarrow(2, 1, 1);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("  ");

        service.autoCreatePigletsForFarrow(farrow, null);

        ArgumentCaptor<Pig> pigCaptor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper, times(2)).insert(pigCaptor.capture());
        assertThat(pigCaptor.getAllValues()).extracting(Pig::getBirthWeight)
            .usingElementComparator(java.util.Comparator.comparing(o -> (BigDecimal) o))
            .containsOnly(new BigDecimal("2"));
    }

    @Test
    @DisplayName("窝级公母数都缺 → 整窝按公组铺 live_born 头（退化规则），保证有耳号可订正")
    void autoCreate_noSexSplit_allMale() {
        PigFarrow farrow = mkFarrow(4, 0, 0);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");

        List<PigletEarTagVo> created = service.autoCreatePigletsForFarrow(farrow, 1L);

        assertThat(created).hasSize(4);
        ArgumentCaptor<Pig> pigCaptor = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper, times(4)).insert(pigCaptor.capture());
        assertThat(pigCaptor.getAllValues()).extracting(Pig::getPigSex).containsOnly("M");
    }

    @Test
    @DisplayName("live_born=0（全窝死胎）→ 一头都不建，也不报错")
    void autoCreate_zeroLiveBorn_noop() {
        PigFarrow farrow = mkFarrow(0, 0, 0);

        assertThat(service.autoCreatePigletsForFarrow(farrow, 1L)).isEmpty();

        verify(pigMapper, never()).insert(any(Pig.class));
        verify(pigletnoMapper, never()).insert(any(PigPigletno.class));
    }

    // ==================== 行243：出生重订正 ====================

    @Test
    @DisplayName("订正出生重：两张表一起改 + 窝级总重均重重算 + 反复提交结果一致（幂等）")
    void adjust_updatesBothTables_andIsIdempotent() {
        PigFarrow farrow = mkFarrow(2, 1, 1);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");
        when(pigMapper.insert(any(Pig.class))).thenAnswer(inv -> {
            inv.<Pig>getArgument(0).setId(500L + pigletnoTable.size());
            return 1;
        });
        service.autoCreatePigletsForFarrow(farrow, 1L);
        String maleEar = pigletnoTable.get(0).getPigletEarNo();
        String femaleEar = pigletnoTable.get(1).getPigletEarNo();

        PigletBirthWeightBo bo = mkBo(maleEar, "1.55", femaleEar, "1.45");
        List<PigletEarTagVo> first = service.adjustBirthWeights(bo);

        assertThat(first).extracting(PigletEarTagVo::getBirthWeight)
            .containsExactly(new BigDecimal("1.55"), new BigDecimal("1.45"));
        // pig_info 也必须跟着改——只改 pigletno 会让详情页出生重与窝级汇总对不上
        ArgumentCaptor<Pig> pigPatch = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper, times(2)).updateById(pigPatch.capture());
        assertThat(pigPatch.getAllValues()).extracting(Pig::getBirthWeight)
            .containsExactly(new BigDecimal("1.55"), new BigDecimal("1.45"));
        assertThat(pigPatch.getAllValues()).extracting(Pig::getId).doesNotContainNull();
        assertFarrowWeights("3.00", "1.50");

        // 再提交一次同样的入参：窝级总重/均重不得累加或漂移
        service.adjustBirthWeights(bo);
        assertFarrowWeights("3.00", "1.50");
        assertThat(pigletnoTable).extracting(PigPigletno::getBirthWeight)
            .containsExactly(new BigDecimal("1.55"), new BigDecimal("1.45"));
    }

    @Test
    @DisplayName("老窝一头都没建档 → 先按同一套规则补建整窝再订正（D-0110），不走第二套分支")
    void adjust_emptyLitter_backfillsThenAdjusts() {
        PigFarrow farrow = mkFarrow(3, 2, 1);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");

        PigletBirthWeightBo probe = new PigletBirthWeightBo();
        probe.setFarrowId(FARROW_ID);
        // 补建用的耳号规则与 batchTag 完全一致，故这里能提前写出第一头公仔的耳号
        PigletBirthWeightItem item = new PigletBirthWeightItem();
        item.setPigletEarNo("4-04-1-260508-001");
        item.setBirthWeight(new BigDecimal("1.70"));
        probe.setItems(List.of(item));

        List<PigletEarTagVo> result = service.adjustBirthWeights(probe);

        assertThat(pigletnoTable).as("按窝级公母数补建 3 头").hasSize(3);
        verify(pigMapper, times(3)).insert(any(Pig.class));
        assertThat(result).extracting(PigletEarTagVo::getBirthWeight)
            .as("被点名那头改成 1.70，其余保持默认 2kg")
            .containsExactly(new BigDecimal("1.70"), new BigDecimal("2.00"), new BigDecimal("2.00"));
        assertFarrowWeights("5.70", "1.90");
    }

    @Test
    @DisplayName("进页面读本窝：老窝没档案先补建，已有档案纯读不重复建（幂等）")
    void ensureLitter_backfillsOnceThenReadsOnly() {
        mkFarrow(3, 2, 1);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");

        var first = service.ensureLitterCreated(FARROW_ID);
        assertThat(first.getTaggedList()).hasSize(3);
        verify(pigMapper, times(3)).insert(any(Pig.class));

        var second = service.ensureLitterCreated(FARROW_ID);
        assertThat(second.getTaggedList()).hasSize(3);
        verify(pigMapper, times(3))
            .insert(any(Pig.class));
        assertThat(pigletnoTable).as("第二次进页面不得再建一窝").hasSize(3);
    }

    @Test
    @DisplayName("旧小程序兼容：窝已自动建档时 batchTag 降级为订正出生重，不再撞 exceeds_live_born")
    void batchTag_onAlreadyArchivedLitter_degradesToAdjust() {
        PigFarrow farrow = mkFarrow(2, 1, 1);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");
        when(pigMapper.insert(any(Pig.class))).thenAnswer(inv -> {
            inv.<Pig>getArgument(0).setId(900L + pigletnoTable.size());
            return 1;
        });
        service.autoCreatePigletsForFarrow(farrow, 1L);
        assertThat(pigletnoTable).hasSize(2);

        // 旧 mp 的提交体：只有 pigletSex + birthWeight，不带耳号
        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(FARROW_ID);
        PigletEarTagItem m = new PigletEarTagItem();
        m.setPigletSex("M");
        m.setBirthWeight(new BigDecimal("1.40"));
        PigletEarTagItem f = new PigletEarTagItem();
        f.setPigletSex("F");
        f.setBirthWeight(new BigDecimal("1.60"));
        bo.setPiglets(List.of(m, f));

        // 关键：不得抛 exceeds_live_born
        service.batchTag(bo);

        assertThat(pigletnoTable)
            .as("兼容路径只订正、绝不新增 —— 再建一窝就是重复建档")
            .hasSize(2);
        assertThat(pigletnoTable).extracting(PigPigletno::getPigletSex, PigPigletno::getBirthWeight)
            .as("按性别顺序把重量贴到已建档的那几头上")
            .containsExactly(
                org.assertj.core.api.Assertions.tuple("M", new BigDecimal("1.40")),
                org.assertj.core.api.Assertions.tuple("F", new BigDecimal("1.60")));
    }

    @Test
    @DisplayName("旧小程序兼容：已建满的窝 statByFarrow 回报 remaining=liveBorn，否则旧 mp 铺 0 行变死路")
    void statByFarrow_archivedLitter_reportsRemainingForLegacyClient() {
        PigFarrow farrow = mkFarrow(2, 1, 1);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");
        service.autoCreatePigletsForFarrow(farrow, 1L);

        var stat = service.statByFarrow(FARROW_ID);
        assertThat(stat.getTagged()).isEqualTo(2);
        assertThat(stat.getRemaining())
            .as("回 0 的话旧 mp eartag 页铺 0 行、提交键置灰，整页成死路")
            .isEqualTo(2);
    }

    @Test
    @DisplayName("出生重按 2 位归一 —— 两张表精度不同(6,2)vs(8,3)，不归一就会「显示 1.56、窝级按 1.555 算」")
    void adjust_normalizesToTwoDecimals_soBothTablesAgree() {
        PigFarrow farrow = mkFarrow(1, 1, 0);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");
        when(pigMapper.insert(any(Pig.class))).thenAnswer(inv -> {
            inv.<Pig>getArgument(0).setId(700L + pigletnoTable.size());
            return 1;
        });
        service.autoCreatePigletsForFarrow(farrow, 1L);
        String earNo = pigletnoTable.get(0).getPigletEarNo();

        PigletBirthWeightBo bo = new PigletBirthWeightBo();
        bo.setFarrowId(FARROW_ID);
        PigletBirthWeightItem it = new PigletBirthWeightItem();
        it.setPigletEarNo(earNo);
        it.setBirthWeight(new BigDecimal("1.555"));
        bo.setItems(List.of(it));
        service.adjustBirthWeights(bo);

        ArgumentCaptor<Pig> pigCap = ArgumentCaptor.forClass(Pig.class);
        verify(pigMapper, atLeastOnce()).updateById(pigCap.capture());
        ArgumentCaptor<PigPigletno> logCap = ArgumentCaptor.forClass(PigPigletno.class);
        verify(pigletnoMapper, atLeastOnce()).updateById(logCap.capture());

        BigDecimal toPigInfo = pigCap.getValue().getBirthWeight();
        BigDecimal toPigletno = logCap.getValue().getBirthWeight();
        assertThat(toPigInfo)
            .as("写进 pig_info 的必须已经是 2 位 —— 那张表就是 DECIMAL(6,2)，交给 MySQL 截会两边不一致")
            .isEqualByComparingTo(new BigDecimal("1.56"));
        assertThat(toPigletno)
            .as("写进 pigletno 的必须和 pig_info 同值，否则窝级总重按它汇总就对不上工人看到的数")
            .isEqualByComparingTo(toPigInfo);
    }

    @Test
    @DisplayName("公母拆分之和超过活产数 → 按活产数收敛建档，绝不让分娩记录跟着一起失败")
    void autoCreate_sexSplitExceedsLiveBorn_clampsInsteadOfThrowing() {
        // 活产 2，却填了公 5 母 5 —— 旧实现会撞 exceeds_live_born 把整条分娩事务掀掉
        PigFarrow farrow = mkFarrow(2, 5, 5);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");

        List<PigletEarTagVo> created = service.autoCreatePigletsForFarrow(farrow, 1L);

        assertThat(created)
            .as("必须按活产数建档，而不是按公母之和 —— 否则分娩录入整单失败，甲方要的一步走完就废了")
            .hasSize(2);
        assertThat(pigletnoTable).hasSize(2);
        assertThat(pigletnoTable).extracting(PigPigletno::getPigletSex)
            .as("收敛后仍按先公后母的次序铺")
            .containsExactly("M", "F");
    }

    @Test
    @DisplayName("同一耳号在一次提交里出现两次 → 整单拒绝，不静默取最后一个把先填的值扔掉")
    void adjust_duplicateEarNoInOneSubmit_rejected() {
        PigFarrow farrow = mkFarrow(2, 1, 1);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");
        service.autoCreatePigletsForFarrow(farrow, 1L);
        String ear = pigletnoTable.get(0).getPigletEarNo();

        PigletBirthWeightBo bo = new PigletBirthWeightBo();
        bo.setFarrowId(FARROW_ID);
        PigletBirthWeightItem a = new PigletBirthWeightItem();
        a.setPigletEarNo(ear);
        a.setBirthWeight(new BigDecimal("1.11"));
        PigletBirthWeightItem b = new PigletBirthWeightItem();
        b.setPigletEarNo(ear);
        b.setBirthWeight(new BigDecimal("2.22"));
        bo.setItems(List.of(a, b));

        assertThatThrownBy(() -> service.adjustBirthWeights(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining(ear);
        verify(pigletnoMapper, never()).updateById(any(PigPigletno.class));
    }

    @Test
    @DisplayName("传进来的耳号不属于本窝 → 抛异常，不静默跳过、不改任何一行")
    void adjust_alienEarNo_rejected() {
        PigFarrow farrow = mkFarrow(2, 1, 1);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");
        service.autoCreatePigletsForFarrow(farrow, 1L);

        PigletBirthWeightBo bo = new PigletBirthWeightBo();
        bo.setFarrowId(FARROW_ID);
        PigletBirthWeightItem alien = new PigletBirthWeightItem();
        alien.setPigletEarNo("4-04-1-991231-999");
        alien.setBirthWeight(new BigDecimal("1.5"));
        bo.setItems(List.of(alien));

        assertThatThrownBy(() -> service.adjustBirthWeights(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("4-04-1-991231-999");
        verify(pigMapper, never()).updateById(any(Pig.class));
        verify(pigletnoMapper, never()).updateById(any(PigPigletno.class));
    }

    @Test
    @DisplayName("D-0112：已断奶那头的出生重改不动 —— 整单拒绝，同一单里没断奶的那头也不许先落库")
    void adjust_weanedEarNo_rejectedWholeSubmit() {
        PigFarrow farrow = mkFarrow(2, 1, 1);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");
        service.autoCreatePigletsForFarrow(farrow, 1L);
        String weanedEar = pigletnoTable.get(0).getPigletEarNo();
        String stillNursing = pigletnoTable.get(1).getPigletEarNo();
        when(weaningMapper.selectAlreadyWeanedEarNos(any(), any()))
            .thenReturn(List.of(weanedEar));

        // 未断奶那头排在前面：若查重是循环内判而不是 pre-pass，它会先被改掉再抛异常
        PigletBirthWeightBo bo = mkBo(stillNursing, "1.11", weanedEar, "2.22");

        assertThatThrownBy(() -> service.adjustBirthWeights(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining(weanedEar);
        verify(pigletnoMapper, never()).updateById(any(PigPigletno.class));
        verify(pigMapper, never()).updateById(any(Pig.class));
    }

    @Test
    @DisplayName("D-0112 旁路：旧 mp 整窝按位提交时，已断奶那几头跳过不写，但位次不塌（后面的头不许串位）")
    void batchTag_compatPath_skipsWeanedButKeepsPositions() {
        PigFarrow farrow = mkFarrow(4, 2, 2);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");
        service.autoCreatePigletsForFarrow(farrow, 1L);
        // 建档顺序 = 公 2 头在前、母 2 头在后；把第一头公的标成已断奶
        List<PigPigletno> males = pigletnoTable.stream()
            .filter(r -> "M".equals(r.getPigletSex()))
            .sorted(Comparator.comparing(PigPigletno::getPigletEarNo)).toList();
        List<PigPigletno> females = pigletnoTable.stream()
            .filter(r -> "F".equals(r.getPigletSex()))
            .sorted(Comparator.comparing(PigPigletno::getPigletEarNo)).toList();
        String weanedMale = males.get(0).getPigletEarNo();
        when(weaningMapper.selectAlreadyWeanedEarNos(any(), any()))
            .thenReturn(List.of(weanedMale));

        // 旧 mp 提交体：无耳号，按 live_born 铺满整窝（先公后母），逐头给不同重量
        PigletBatchEarTagBo bo = new PigletBatchEarTagBo();
        bo.setFarrowId(FARROW_ID);
        bo.setPiglets(List.of(
            mkItem("M", "7.71"), mkItem("M", "7.72"),
            mkItem("F", "7.73"), mkItem("F", "7.74")));

        BigDecimal weanedBefore = males.get(0).getBirthWeight();   // 建档时写的字典默认 2kg
        service.batchTag(bo);

        assertThat(males.get(0).getBirthWeight())
            .as("已断奶那头必须一个字节都没改（仍是建档时的值，不是提交上来的 7.71）")
            .isEqualByComparingTo(weanedBefore);
        assertThat(males.get(1).getBirthWeight())
            .as("第二头公的必须还是拿到它自己那一位的 7.72，而不是被跳过后串成 7.71")
            .isEqualByComparingTo(new BigDecimal("7.72"));
        assertThat(females.get(0).getBirthWeight()).isEqualByComparingTo(new BigDecimal("7.73"));
        assertThat(females.get(1).getBirthWeight()).isEqualByComparingTo(new BigDecimal("7.74"));
    }

    @Test
    @DisplayName("D-0112：本窝清单逐头标 weaned，断掉的那几头前端据此置灰")
    void statByFarrow_marksWeanedPiglets() {
        PigFarrow farrow = mkFarrow(2, 1, 1);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");
        service.autoCreatePigletsForFarrow(farrow, 1L);
        String weanedEar = pigletnoTable.get(0).getPigletEarNo();
        when(weaningMapper.selectAlreadyWeanedEarNos(any(), any()))
            .thenReturn(List.of(weanedEar));

        var list = service.statByFarrow(FARROW_ID).getTaggedList();

        assertThat(list).hasSize(2);
        assertThat(list).filteredOn(v -> weanedEar.equals(v.getPigletEarNo()))
            .allMatch(v -> Boolean.TRUE.equals(v.getWeaned()));
        assertThat(list).filteredOn(v -> !weanedEar.equals(v.getPigletEarNo()))
            .allMatch(v -> Boolean.FALSE.equals(v.getWeaned()));
    }

    @Test
    @DisplayName("D-0112：一窝都没断奶 → 逐头 weaned=false，整页照常可改")
    void statByFarrow_noneWeaned_allEditable() {
        PigFarrow farrow = mkFarrow(2, 1, 1);
        when(dictService.getDictValue("djs_piglet_default_weight", "仔猪出生重")).thenReturn("2");
        service.autoCreatePigletsForFarrow(farrow, 1L);
        when(weaningMapper.selectAlreadyWeanedEarNos(any(), any()))
            .thenReturn(List.of());

        assertThat(service.statByFarrow(FARROW_ID).getTaggedList())
            .isNotEmpty()
            .allMatch(v -> Boolean.FALSE.equals(v.getWeaned()));
    }

    @Test
    @DisplayName("farrow 不存在 → 抛异常（不静默建空窝）")
    void adjust_farrowNotFound_rejected() {
        when(farrowMapper.selectById(FARROW_ID)).thenReturn(null);
        PigletBirthWeightBo bo = new PigletBirthWeightBo();
        bo.setFarrowId(FARROW_ID);
        PigletBirthWeightItem item = new PigletBirthWeightItem();
        item.setPigletEarNo("x");
        item.setBirthWeight(new BigDecimal("1.5"));
        bo.setItems(List.of(item));

        assertThatThrownBy(() -> service.adjustBirthWeights(bo)).isInstanceOf(ServiceException.class);
    }

    private static PigletEarTagItem mkItem(String sex, String weight) {
        PigletEarTagItem it = new PigletEarTagItem();
        it.setPigletSex(sex);
        it.setBirthWeight(new BigDecimal(weight));
        return it;
    }

    private PigletBirthWeightBo mkBo(String ear1, String w1, String ear2, String w2) {
        PigletBirthWeightBo bo = new PigletBirthWeightBo();
        bo.setFarrowId(FARROW_ID);
        PigletBirthWeightItem i1 = new PigletBirthWeightItem();
        i1.setPigletEarNo(ear1);
        i1.setBirthWeight(new BigDecimal(w1));
        PigletBirthWeightItem i2 = new PigletBirthWeightItem();
        i2.setPigletEarNo(ear2);
        i2.setBirthWeight(new BigDecimal(w2));
        bo.setItems(List.of(i1, i2));
        return bo;
    }

    /** 断言最近一次回写分娩表的窝级总重 / 均重。 */
    private void assertFarrowWeights(String total, String avg) {
        ArgumentCaptor<PigFarrow> captor = ArgumentCaptor.forClass(PigFarrow.class);
        verify(farrowMapper, atLeastOnce()).updateById(captor.capture());
        PigFarrow last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(last.getTotalWeight()).isEqualByComparingTo(new BigDecimal(total));
        assertThat(last.getAvgWeight()).isEqualByComparingTo(new BigDecimal(avg));
    }
    @Test
    @DisplayName("D-0116：一窝贴过标的仔猪全部死亡（pigletno 被软删）后，不许被当成零档案老窝再补建一窝幽灵档案")
    void ensureLitter_doesNotRebuildAfterWholeLitterDied() {
        mkFarrow(3, 2, 1);
        // 未删行为 0（全死了），但历史上建过 3 行 —— 判据看的必须是后者
        pigletnoTable.clear();
        when(pigletnoMapper.countByFarrowIgnoreDeleted(anyLong())).thenReturn(3);

        service.ensureLitterCreated(FARROW_ID);

        verify(pigMapper, never()).insert(any(Pig.class));
        verify(pigletnoMapper, never()).insert(any(PigPigletno.class));
    }

}
