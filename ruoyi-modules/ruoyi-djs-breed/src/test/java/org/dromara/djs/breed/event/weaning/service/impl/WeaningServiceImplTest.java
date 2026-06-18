package org.dromara.djs.breed.event.weaning.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;
import org.dromara.djs.breed.event.eartag.mapper.PigPigletnoMapper;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.dromara.djs.breed.event.transfer.domain.bo.TransferBo;
import org.dromara.djs.breed.event.transfer.service.ITransferService;
import org.dromara.djs.breed.event.weaning.domain.PigWeaning;
import org.dromara.djs.breed.event.weaning.domain.PigWeaningDetail;
import org.dromara.djs.breed.event.weaning.domain.bo.WeaningBo;
import org.dromara.djs.breed.event.weaning.domain.bo.WeaningDetailBo;
import org.dromara.djs.breed.event.weaning.mapper.PigWeaningDetailMapper;
import org.dromara.djs.breed.event.weaning.mapper.PigWeaningMapper;
import org.junit.jupiter.api.BeforeAll;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
    private PigWeaningDetailMapper weaningDetailMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private PigFarrowMapper farrowMapper;
    @Mock
    private PigPigletnoMapper pigletnoMapper;
    @Mock
    private IPigCoreService pigCoreService;
    @Mock
    private ITransferService transferService;
    @Mock
    private BarnMapper barnMapper;
    @Mock
    private PenMapper penMapper;
    @Mock
    private DictService dictService;

    private WeaningServiceImpl service;

    /**
     * MyBatis-Plus 单测 entity cache 预热（coder-mp-entity-cache-test）：flipWeanedPigletsToFattening
     * 用 {@code Wrappers.<Pig>lambdaUpdate().set(...)}（eager 解析列名）+ {@code <PigPigletno>lambdaQuery()}，
     * mock 路径下也会触发 TableInfoHelper.getTableInfo，必须先注册 entity。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, Pig.class);
        TableInfoHelper.initTableInfo(assistant, PigPigletno.class);
    }

    @BeforeEach
    void setup() {
        service = new WeaningServiceImpl(weaningMapper, weaningDetailMapper, pigMapper, farrowMapper,
            pigletnoMapper, pigCoreService, transferService, barnMapper, penMapper, dictService);
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

    private PigPigletno mkPiglet(Long pigId, String earNo) {
        PigPigletno p = new PigPigletno();
        p.setPigId(pigId);
        p.setPigletEarNo(earNo);
        return p;
    }

    private WeaningDetailBo mkDetail(Integer seq, String earNo, String weight) {
        WeaningDetailBo d = new WeaningDetailBo();
        d.setPigletSeq(seq);
        d.setEarNo(earNo);
        d.setWeight(new BigDecimal(weight));
        return d;
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
    @DisplayName("#32a inline transfer: 给了转移目标栋舍 → 断奶事务内联调 transferService.recordTransfer")
    void inlineTransfer_whenTargetGiven() {
        Pig pig = mkSow(320L, PigLifecycle.FM);
        when(pigMapper.selectById(320L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(520L, 320L, 10, 7777L);
        when(farrowMapper.selectById(520L)).thenReturn(farrow);
        // 该分娩无已建行仔猪 → 仅转母猪
        when(pigletnoMapper.selectList(any())).thenReturn(List.of());

        WeaningBo bo = mkBo(320L, 520L, 8, new BigDecimal("60.000"));
        bo.setTransferBarnCode("B02");
        bo.setTransferPenCode("P03");
        service.recordWeaning(bo);

        ArgumentCaptor<TransferBo> cap = ArgumentCaptor.forClass(TransferBo.class);
        verify(transferService, times(1)).recordTransfer(cap.capture());
        assertThat(cap.getValue().getPigId()).isEqualTo(320L);
        assertThat(cap.getValue().getNewBarnCode()).isEqualTo("B02");
        assertThat(cap.getValue().getNewPenCode()).isEqualTo("P03");
        // 转移日期 = 断奶日期
        assertThat(cap.getValue().getTransferDate()).isEqualTo(bo.getWeaningDate());
    }

    @Test
    @DisplayName("Y2(b): farrowId 空 → 自动取该母猪最近一次分娩兜底（按 pigId 查 selectOne）")
    void autoMatchLatestFarrow_whenFarrowIdAbsent() {
        Pig pig = mkSow(330L, PigLifecycle.FM);
        when(pigMapper.selectById(330L)).thenReturn(pig);
        PigFarrow latest = mkFarrow(530L, 330L, 10, 7777L);
        // farrowId 空时走 selectOne（最近分娩）
        when(farrowMapper.selectOne(any())).thenReturn(latest);

        WeaningBo bo = mkBo(330L, null /* 无 farrowId */, 8, new BigDecimal("60.000"));
        var vo = service.recordWeaning(bo);

        // 兜底回填 farrowId
        assertThat(bo.getFarrowId()).isEqualTo(530L);
        ArgumentCaptor<PigWeaning> cap = ArgumentCaptor.forClass(PigWeaning.class);
        verify(weaningMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getFarrowId()).isEqualTo(530L);
        assertThat(vo.getFarrowId()).isEqualTo(530L);
        // 未传 farrowId 时不应再按 id 查（只走 selectOne 兜底）
        verify(farrowMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("Y2(b): farrowId 空且该母猪无任何分娩 → 抛明确异常 weaning.no_farrow_for_pig")
    void autoMatchLatestFarrow_noFarrowAtAll() {
        Pig pig = mkSow(331L, PigLifecycle.FM);
        when(pigMapper.selectById(331L)).thenReturn(pig);
        when(farrowMapper.selectOne(any())).thenReturn(null);

        WeaningBo bo = mkBo(331L, null, 8, new BigDecimal("60.000"));
        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("weaning.no_farrow_for_pig");
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
        verify(pigCoreService, never()).fireEvent(any());
    }

    @Test
    @DisplayName("K071: 给了转移目标 → 母猪 + 该分娩已贴标仔猪（pig_id 非空）逐头转到同目标（N+1 次）")
    void pigletTransferAfterWean_sameTarget() {
        Pig pig = mkSow(340L, PigLifecycle.FM);
        when(pigMapper.selectById(340L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(540L, 340L, 10, 7777L);
        when(farrowMapper.selectById(540L)).thenReturn(farrow);
        // 该分娩下 2 头已建 pig_info 行的仔猪 + 1 头未落 pig_id（应跳过）
        when(pigletnoMapper.selectList(any())).thenReturn(List.of(
            mkPiglet(9001L, "P-001"),
            mkPiglet(9002L, "P-002")
        ));

        WeaningBo bo = mkBo(340L, 540L, 2, new BigDecimal("16.000"));
        bo.setTransferBarnCode("B05");
        bo.setTransferPenCode("P07");
        service.recordWeaning(bo);

        // 母猪 1 + 仔猪 2 = 3 次转移
        ArgumentCaptor<TransferBo> cap = ArgumentCaptor.forClass(TransferBo.class);
        verify(transferService, times(3)).recordTransfer(cap.capture());
        assertThat(cap.getAllValues()).extracting(TransferBo::getPigId)
            .containsExactly(340L, 9001L, 9002L);
        // 全部转到同目标
        assertThat(cap.getAllValues()).allMatch(t -> "B05".equals(t.getNewBarnCode()));
        assertThat(cap.getAllValues()).allMatch(t -> "P07".equals(t.getNewPenCode()));
    }

    @Test
    @DisplayName("K071: 给了转移目标但该分娩无已建行仔猪 → 仅转母猪（1 次）")
    void onlySowTransfer_whenNoPiglets() {
        Pig pig = mkSow(341L, PigLifecycle.FM);
        when(pigMapper.selectById(341L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(541L, 341L, 10, 7777L);
        when(farrowMapper.selectById(541L)).thenReturn(farrow);
        when(pigletnoMapper.selectList(any())).thenReturn(List.of());

        WeaningBo bo = mkBo(341L, 541L, 5, new BigDecimal("40.000"));
        bo.setTransferBarnCode("B05");
        service.recordWeaning(bo);

        verify(transferService, times(1)).recordTransfer(any(TransferBo.class));
    }

    @Test
    @DisplayName("#32a inline transfer: 无转移目标 → 不调 transferService（仅断奶）")
    void noInlineTransfer_whenTargetAbsent() {
        Pig pig = mkSow(321L, PigLifecycle.FM);
        when(pigMapper.selectById(321L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(521L, 321L, 10, 7777L);
        when(farrowMapper.selectById(521L)).thenReturn(farrow);

        WeaningBo bo = mkBo(321L, 521L, 8, new BigDecimal("60.000")); // 无转移目标
        service.recordWeaning(bo);

        verify(transferService, never()).recordTransfer(any(TransferBo.class));
    }

    @Test
    @DisplayName("per-piglet: 逐头录重明细同事务批量 INSERT，piglet_seq 缺省按顺序补 1..N")
    void perPiglet_details_batchInserted() {
        Pig pig = mkSow(310L, PigLifecycle.FM);
        when(pigMapper.selectById(310L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(510L, 310L, 10, 7777L);
        when(farrowMapper.selectById(510L)).thenReturn(farrow);

        WeaningBo bo = mkBo(310L, 510L, 3, new BigDecimal("24.000"));
        bo.setDetails(List.of(
            mkDetail(null, "P-001", "8.000"),
            mkDetail(null, "P-002", "8.000"),
            mkDetail(null, null, "8.000")
        ));
        var vo = service.recordWeaning(bo);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PigWeaningDetail>> cap = ArgumentCaptor.forClass(List.class);
        verify(weaningDetailMapper, times(1)).insertBatch(cap.capture());
        List<PigWeaningDetail> rows = cap.getValue();
        assertThat(rows).hasSize(3);
        // piglet_seq 缺省 → 按下发顺序补 1/2/3
        assertThat(rows).extracting(PigWeaningDetail::getPigletSeq).containsExactly(1, 2, 3);
        assertThat(rows).extracting(PigWeaningDetail::getWeight)
            .allMatch(w -> w.compareTo(new BigDecimal("8.000")) == 0);
        // 第 3 头无耳号允许
        assertThat(rows.get(2).getEarNo()).isNull();
        // VO 回带明细
        assertThat(vo.getDetails()).hasSize(3);
    }

    @Test
    @DisplayName("per-piglet: details 缺省（汇总录入）→ 不调 detailMapper（向后兼容）")
    void perPiglet_emptyDetails_skipsDetailMapper() {
        Pig pig = mkSow(311L, PigLifecycle.FM);
        when(pigMapper.selectById(311L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(511L, 311L, 10, null);
        when(farrowMapper.selectById(511L)).thenReturn(farrow);

        WeaningBo bo = mkBo(311L, 511L, 5, new BigDecimal("40.000")); // 无 details
        var vo = service.recordWeaning(bo);

        verify(weaningMapper, times(1)).insert(any(PigWeaning.class));
        verify(weaningDetailMapper, never()).insertBatch(anyList());
        assertThat(vo.getDetails()).isEmpty();
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

    @Test
    @DisplayName("FIX-BRD-PIGTYPE-001: 断奶把该窝已贴标仔猪批量翻育肥猪（pigMapper.update 调一次）")
    void flipWeanedPigletsToFattening_onWean() {
        Pig pig = mkSow(350L, PigLifecycle.FM);
        when(pigMapper.selectById(350L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(550L, 350L, 10, 7777L);
        when(farrowMapper.selectById(550L)).thenReturn(farrow);
        // 该分娩下 2 头已建 pig_info 行的仔猪
        when(pigletnoMapper.selectList(any())).thenReturn(List.of(
            mkPiglet(9101L, "P-001"),
            mkPiglet(9102L, "P-002")
        ));

        WeaningBo bo = mkBo(350L, 550L, 2, new BigDecimal("16.000"));
        service.recordWeaning(bo);

        // 一次性 IN 批量条件 update（pig_type='piglet' 且非 END → set 'fattening'）
        verify(pigMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("FIX-BRD-PIGTYPE-001: 该分娩无已建行仔猪 → 不调 pigMapper.update（无可翻仔猪）")
    void flipWeanedPiglets_noPiglets_noUpdate() {
        Pig pig = mkSow(351L, PigLifecycle.FM);
        when(pigMapper.selectById(351L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(551L, 351L, 10, 7777L);
        when(farrowMapper.selectById(551L)).thenReturn(farrow);
        when(pigletnoMapper.selectList(any())).thenReturn(List.of());

        WeaningBo bo = mkBo(351L, 551L, 5, new BigDecimal("40.000"));
        service.recordWeaning(bo);

        verify(pigMapper, never()).update(any(), any());
    }
}
