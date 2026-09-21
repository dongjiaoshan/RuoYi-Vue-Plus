package org.dromara.djs.breed.event.farrow.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.breeding.mapper.PigBreedingMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;
import org.dromara.djs.breed.event.eartag.mapper.PigPigletnoMapper;
import org.dromara.djs.breed.event.eartag.service.IPigEarTagService;
import org.dromara.djs.breed.event.farrow.domain.vo.FarrowLitterVo;
import org.dromara.djs.breed.event.farrow.domain.vo.PigFarrowVo;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.domain.bo.FarrowBo;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
 *   <li>状态机 guard 前置：公猪（female_only）/ 非 PZ（invalid_transition）在写台账<b>之前</b>被拒，
 *       台账 INSERT 不发生（否则 DB 列约束报错会把业务错误顶成「发生未知异常」）；</li>
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
    private PigBreedingMapper breedingMapper;
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
    @Mock
    private IPigEarTagService eartagService;

    private FarrowServiceImpl service;

    /**
     * MP 的 lambda 列缓存在纯单测里不会自动建（没有 SqlSessionFactory），
     * 不预热就渲染不出 wrapper 的 SQL 串，「未断奶过滤有没有下到 SQL」这条断言也就无从断起。
     */
    @BeforeAll
    static void warmUpLambdaCache() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), PigFarrow.class);
    }

    @BeforeEach
    void setup() {
        service = new FarrowServiceImpl(farrowMapper, breedingMapper, pigMapper, barnMapper, penMapper,
            pigletnoMapper, pigCoreService, dictService, eartagService);
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
    @DisplayName("guard 前置: HB 母猪（非 PZ）→ invalid_transition 且台账不落库、不 fireEvent")
    void invalidTransition_rejectedBeforeLedgerInsert() {
        Pig pig = mkSow(203L, PigLifecycle.HB, null);
        when(pigMapper.selectById(203L)).thenReturn(pig);
        // 真状态机对 (HB, FARROW) 的行为见 PigCoreServiceImplTest#precheckEvent_hb_sow_farrow_invalid_transition
        when(pigCoreService.precheckEvent(any(PigEventBo.class)))
            .thenThrow(new ServiceException("pig.event.invalid_transition: 后备, 分娩", 400));

        FarrowBo bo = mkBo(203L, null, 6, 6);
        assertThatThrownBy(() -> service.recordFarrow(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.invalid_transition")
            .extracting(e -> ((ServiceException) e).getCode()).isEqualTo(400);

        // 关键：台账 INSERT 必须没发生 —— 否则非法输入先撞 t_farm_pig_farrow 的列约束，
        // 真业务错误会被 DataIntegrityViolationException 顶成 500「发生未知异常」
        verify(farrowMapper, never()).insert(any(PigFarrow.class));
        verify(pigCoreService, never()).fireEvent(any());
    }

    @Test
    @DisplayName("guard 前置: 公猪走分娩 → female_only 且台账不落库、不 fireEvent")
    void boarFarrow_rejectedBeforeLedgerInsert() {
        Pig boar = mkSow(204L, PigLifecycle.HB, null);
        boar.setPigSex("M");
        boar.setPigType("boar");
        boar.setCurrentStatus("");          // 种公猪空状态（ADR-0016）
        when(pigMapper.selectById(204L)).thenReturn(boar);
        // 真状态机对 (公猪, FARROW) 的行为见 PigCoreServiceImplTest#precheckEvent_boar_farrow_female_only
        when(pigCoreService.precheckEvent(any(PigEventBo.class)))
            .thenThrow(new ServiceException("pig.event.female_only: 分娩", 400));

        FarrowBo bo = mkBo(204L, null, 5, 5);
        assertThatThrownBy(() -> service.recordFarrow(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.female_only")
            .extracting(e -> ((ServiceException) e).getCode()).isEqualTo(400);

        verify(farrowMapper, never()).insert(any(PigFarrow.class));
        verify(pigCoreService, never()).fireEvent(any());
    }

    @Test
    @DisplayName("guard 前置顺序: precheckEvent → INSERT 台账 → fireEvent")
    void guardRunsBeforeLedgerInsert() {
        Pig pig = mkSow(205L, PigLifecycle.PZ, 7777L);
        when(pigMapper.selectById(205L)).thenReturn(pig);

        service.recordFarrow(mkBo(205L, 7777L, 10, 10));

        InOrder order = inOrder(pigCoreService, farrowMapper);
        order.verify(pigCoreService).precheckEvent(any(PigEventBo.class));
        order.verify(farrowMapper).insert(any(PigFarrow.class));
        order.verify(pigCoreService).fireEvent(any(PigEventBo.class));
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

    // ==================== V6 行242/243 ====================

    @Test
    @DisplayName("分娩提交后整窝自动建档（同事务），VO 的已建档头数回填真实值")
    void recordFarrow_autoCreatesPiglets() {
        Pig pig = mkSow(300L, PigLifecycle.PZ, 7777L);
        when(pigMapper.selectById(300L)).thenReturn(pig);
        when(eartagService.autoCreatePigletsForFarrow(any(PigFarrow.class), any()))
            .thenReturn(List.of(new org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo(),
                new org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo()));

        FarrowBo bo = mkBo(300L, 7777L, 3, 2);
        bo.setOperatorId(888L);
        PigFarrowVo vo = service.recordFarrow(bo);

        InOrder order = inOrder(farrowMapper, pigCoreService, eartagService);
        order.verify(farrowMapper).insert(any(PigFarrow.class));
        order.verify(pigCoreService).fireEvent(any(PigEventBo.class));
        order.verify(eartagService).autoCreatePigletsForFarrow(any(PigFarrow.class), eq(888L));
        assertThat(vo.getTagged()).isEqualTo(2);
        assertThat(vo.getRemaining()).isZero();
    }

    @Test
    @DisplayName("公母数按实际建档回写 —— 否则同一行会写着公5母5、却只挂 2 头仔猪档案")
    void recordFarrow_syncsSexCountsFromActuallyCreatedPiglets() {
        Pig pig = mkSow(301L, PigLifecycle.PZ, 7778L);
        when(pigMapper.selectById(301L)).thenReturn(pig);
        // 建档侧按活产数收敛，只建了 1 公 1 母
        org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo m =
            new org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo();
        m.setPigletSex("M");
        org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo f =
            new org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo();
        f.setPigletSex("F");
        when(eartagService.autoCreatePigletsForFarrow(any(PigFarrow.class), any()))
            .thenReturn(List.of(m, f));

        // mkBo(pigId, breedingId, totalBorn, liveBorn) —— 第 3/4 个参数是总产/活产，不是公母。
        // BO 没带性别细分，落库派生出的公母是 0+0，与实际建出的 1+1 不一致，正是回写要覆盖的场景。
        FarrowBo bo = mkBo(301L, 7778L, 5, 5);
        service.recordFarrow(bo);

        ArgumentCaptor<PigFarrow> patch = ArgumentCaptor.forClass(PigFarrow.class);
        verify(farrowMapper).updateById(patch.capture());
        assertThat(patch.getValue().getMaleCount())
            .as("分娩记录的公母数必须等于实际建出来的档案数，否则窝详情与耳标页互相打架")
            .isEqualTo(1);
        assertThat(patch.getValue().getFemaleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("全窝死胎（活产0）时公母数一并归零 —— 否则留下「活产0、公5母5、档案0行」")
    void recordFarrow_zeroLiveBorn_alsoZerosSexCounts() {
        Pig pig = mkSow(302L, PigLifecycle.PZ, 7779L);
        when(pigMapper.selectById(302L)).thenReturn(pig);
        when(eartagService.autoCreatePigletsForFarrow(any(PigFarrow.class), any()))
            .thenReturn(List.of());

        FarrowBo bo = mkBo(302L, 7779L, 10, 0);
        bo.setMaleCount(5);
        bo.setFemaleCount(5);
        service.recordFarrow(bo);

        ArgumentCaptor<PigFarrow> patch = ArgumentCaptor.forClass(PigFarrow.class);
        verify(farrowMapper).updateById(patch.capture());
        assertThat(patch.getValue().getMaleCount()).isZero();
        assertThat(patch.getValue().getFemaleCount()).isZero();
    }

    @Test
    @DisplayName("公母数本来就与实际建档一致时不发多余 UPDATE")
    void recordFarrow_alreadyConsistent_skipsUpdate() {
        Pig pig = mkSow(303L, PigLifecycle.PZ, 7780L);
        when(pigMapper.selectById(303L)).thenReturn(pig);
        org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo m =
            new org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo();
        m.setPigletSex("M");
        when(eartagService.autoCreatePigletsForFarrow(any(PigFarrow.class), any()))
            .thenReturn(List.of(m));

        FarrowBo bo = mkBo(303L, 7780L, 1, 1);
        bo.setHealthyMale(1);
        service.recordFarrow(bo);

        verify(farrowMapper, never()).updateById(any(PigFarrow.class));
    }

    @Test
    @DisplayName("公母细分之和与活产数对不上 → 门口就拒，且用分娩域的话而不是耳标域术语")
    void recordFarrow_sexSplitMismatch_rejectedAtValidation() {
        Pig pig = mkSow(304L, PigLifecycle.PZ, 7781L);
        when(pigMapper.selectById(304L)).thenReturn(pig);

        FarrowBo bo = mkBo(304L, 7781L, 10, 2);
        bo.setHealthyMale(5);
        bo.setHealthyFemale(5);

        assertThatThrownBy(() -> service.recordFarrow(bo))
            .isInstanceOf(ServiceException.class)
            // 只断「抛了异常」护不住文案：退回耳标域那句 pigletno.exceeds_live_born 照样绿。
            // 分娩页的人没有「打标」这个概念，这里必须钉住用的是分娩域的 key。
            // 单测无 i18n 上下文，拿到的是 key 本身，正好可以直接断归属。
            .hasMessageContaining("farrow.sex_split_mismatch")
            .hasMessageNotContaining("pigletno.");
        verify(farrowMapper, never()).insert(any(PigFarrow.class));
        verify(eartagService, never()).autoCreatePigletsForFarrow(any(PigFarrow.class), any());
    }

    @Test
    @DisplayName("建档失败必须抛出去让整条分娩回滚，不得吞")
    void recordFarrow_autoCreateFailurePropagates() {
        Pig pig = mkSow(301L, PigLifecycle.PZ, 7777L);
        when(pigMapper.selectById(301L)).thenReturn(pig);
        when(eartagService.autoCreatePigletsForFarrow(any(PigFarrow.class), any()))
            .thenThrow(new ServiceException("耳号分配失败"));

        FarrowBo bo = mkBo(301L, 7777L, 3, 2);
        assertThatThrownBy(() -> service.recordFarrow(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("耳号分配失败");
    }

    @Test
    @DisplayName("选窝列表的「未断奶」过滤走 SQL 侧 NOT EXISTS，不靠内存筛")
    void pendingLitters_filtersUnweanedInSql() {
        when(farrowMapper.selectVoList(any())).thenReturn(new ArrayList<>(List.of(mkLitterRow(77L, 8))));

        service.queryPendingLitters(null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<PigFarrow>> captor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(farrowMapper).selectVoList(captor.capture());
        String sql = captor.getValue().getSqlSegment().replaceAll("\\s+", " ");
        assertThat(sql)
            .as("过滤条件掉回内存 = LIMIT 200 的候选被滤掉大半，列表口径会漂")
            .contains("NOT EXISTS")
            .contains("t_farm_pig_weaning")
            .contains("w.farrow_id = t_farm_pig_farrow.id")
            .contains("w.del_flag = '0'");
    }

    @Test
    @DisplayName("整窝仔猪已全部建档（remain=0）照样在列表里 —— 口径是「未断奶」不是「未打标」")
    void pendingLitters_keepsFullyTaggedLitter() {
        when(farrowMapper.selectVoList(any())).thenReturn(new ArrayList<>(List.of(mkLitterRow(77L, 8))));
        when(pigletnoMapper.selectList(any())).thenReturn(mkPigletnoRows(77L, 8));

        List<FarrowLitterVo> litters = service.queryPendingLitters(null, null);

        assertThat(litters).extracting(FarrowLitterVo::getId).containsExactly(77L);
        assertThat(litters.get(0).getRemainEartag())
            .as("行242 起整窝在分娩时就建好档，remain 恒 0；再按 remain 过滤这页永远是空的")
            .isZero();
        assertThat(litters.get(0).getTaggedEartag()).isEqualTo(8);
    }

    private PigFarrowVo mkLitterRow(long farrowId, int liveBorn) {
        PigFarrowVo vo = new PigFarrowVo();
        vo.setId(farrowId);
        vo.setPigId(101L);
        vo.setEarNo("01A12605001");
        vo.setFarrowDate(LocalDateTime.of(2026, 5, 8, 9, 0));
        vo.setLiveBorn(liveBorn);
        vo.setMaleCount(liveBorn);
        vo.setFemaleCount(0);
        vo.setParity(2);
        vo.setBarnName("分娩1栋");
        vo.setPenName("11栏");
        return vo;
    }

    private List<PigPigletno> mkPigletnoRows(long farrowId, int count) {
        List<PigPigletno> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            PigPigletno row = new PigPigletno();
            row.setId((long) (i + 1));
            row.setFarrowId(farrowId);
            row.setPigletEarNo("4-04-1-260508-00" + (i + 1));
            rows.add(row);
        }
        return rows;
    }
}
