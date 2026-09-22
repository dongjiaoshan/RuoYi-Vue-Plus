package org.dromara.djs.breed.event.weaning.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.event.weaning.domain.vo.PigWeaningVo;
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
import org.dromara.djs.breed.event.weaning.domain.vo.WeaningPigletVo;
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
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
        // BRD-WEAN-SELECT-001：分批断奶要按 farrowId 查本窝既有断奶记录、按 weaningId 查明细耳号，
        // 两处都用 lambdaQuery（eager 解析列名），mock 路径下同样要先注册 entity。
        TableInfoHelper.initTableInfo(assistant, PigWeaning.class);
        TableInfoHelper.initTableInfo(assistant, PigWeaningDetail.class);
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
        return mkPiglet(pigId, earNo, null);
    }

    /** 带窝归属的逐头行 —— 寄养用例要靠 farrowId 区分「本窝的」与「别窝寄养来的」。 */
    private PigPigletno mkPiglet(Long pigId, String earNo, Long farrowId) {
        PigPigletno p = new PigPigletno();
        p.setPigId(pigId);
        p.setPigletEarNo(earNo);
        p.setFarrowId(farrowId);
        return p;
    }

    /**
     * 把 {@code pigletnoMapper.selectList} 装成一个「会看 wrapper 的假库」。
     *
     * <p>耳号过滤现在压在 SQL 里（{@code loadWeanedPiglets} 用 {@code .in(getPigletEarNo, earNos)}），
     * 不再是查回整窝再内存筛。若这里仍然无脑返回全部，「只转选中那几头」就成了一句空话 ——
     * 服务端哪天把 IN 条件删了测试照样绿。所以按 wrapper 里实际带的耳号参数过滤，
     * 没带耳号条件（匿名铺行退化路径）才整窝返回。</p>
     */
    private void stubPigletnoTable(List<PigPigletno> table) {
        when(pigletnoMapper.selectList(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            if (!(arg instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> w)) {
                return table;
            }
            // 🔴 必须先取一次 sqlSegment：MP 的 paramNameValuePairs 是懒填的，
            // 不落实 SQL 段就直接读这张表，拿到的是空 map（于是过滤失效、假库退化成「整窝全返」）。
            w.getSqlSegment();
            // 服务端有两种查法：按耳号 IN（本次断掉那几头）与按 farrow_id eq（本窝整窝，头数守恒用）。
            // 假库两种都要认 —— 只认耳号的话，按窝那一查会把整张表（含别窝寄养来的）当成本窝返回，
            // 头数守恒就会把寄养头算进养母窝的预算，正好掩盖掉这条用例要验的行为。
            Collection<Object> params = w.getParamNameValuePairs().values();
            Set<String> ears = params.stream()
                .filter(String.class::isInstance).map(String.class::cast)
                .collect(Collectors.toSet());
            Set<Long> farrowIds = params.stream()
                .filter(Long.class::isInstance).map(Long.class::cast)
                .collect(Collectors.toSet());
            List<PigPigletno> rows = table;
            if (!farrowIds.isEmpty()) {
                rows = rows.stream().filter(r -> farrowIds.contains(r.getFarrowId())).toList();
            }
            List<String> known = rows.stream().map(PigPigletno::getPigletEarNo).toList();
            Set<String> wanted = ears.stream().filter(known::contains).collect(Collectors.toSet());
            return wanted.isEmpty()
                ? rows
                : rows.stream().filter(r -> wanted.contains(r.getPigletEarNo())).toList();
        });
    }

    private WeaningDetailBo mkDetail(Integer seq, String earNo, String weight) {
        WeaningDetailBo d = new WeaningDetailBo();
        d.setPigletSeq(seq);
        d.setEarNo(earNo);
        d.setWeight(new BigDecimal(weight));
        return d;
    }

    /** 本窝既有断奶记录（分批断奶场景）。 */
    private PigWeaning mkPriorWeaning(Long id, int weanedCount, int lactationDeath) {
        PigWeaning w = new PigWeaning();
        w.setId(id);
        w.setWeanedCount(weanedCount);
        w.setLactationDeathCount(lactationDeath);
        return w;
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
    @DisplayName("校验: 断奶数 + 哺乳期死淘数 > farrow.liveBorn → ServiceException（D-0065 头数守恒）")
    void validate_countPlusLactationDeathExceedsLiveBorn() {
        Pig pig = mkSow(303L, PigLifecycle.FM);
        when(pigMapper.selectById(303L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(503L, 303L, 10, null);
        when(farrowMapper.selectById(503L)).thenReturn(farrow);

        // 断奶 8 + 死淘 3 = 11 > 活仔 10：旧校验只看 weanedCount(8<=10) 会放行
        WeaningBo bo = mkBo(303L, 503L, 8, null);
        bo.setLactationDeathCount(3);
        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("weaning.count_exceeds_live_born");
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("落库: 哺乳期死淘数写入 t_farm_pig_weaning；未传时落 0")
    void recordWeaning_persistsLactationDeathCount() {
        Pig pig = mkSow(304L, PigLifecycle.FM);
        when(pigMapper.selectById(304L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(504L, 304L, 12, null);
        when(farrowMapper.selectById(504L)).thenReturn(farrow);

        WeaningBo bo = mkBo(304L, 504L, 9, null);
        bo.setLactationDeathCount(3);
        service.recordWeaning(bo);

        ArgumentCaptor<PigWeaning> cap = ArgumentCaptor.forClass(PigWeaning.class);
        verify(weaningMapper).insert(cap.capture());
        assertThat(cap.getValue().getLactationDeathCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("落库: 哺乳期死淘数缺省 → 落 0（不落 null，产房损失率 SUM 才不漏行）")
    void recordWeaning_lactationDeathDefaultsToZero() {
        Pig pig = mkSow(305L, PigLifecycle.FM);
        when(pigMapper.selectById(305L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(505L, 305L, 12, null);
        when(farrowMapper.selectById(505L)).thenReturn(farrow);

        WeaningBo bo = mkBo(305L, 505L, 12, null);
        service.recordWeaning(bo);

        ArgumentCaptor<PigWeaning> cap = ArgumentCaptor.forClass(PigWeaning.class);
        verify(weaningMapper).insert(cap.capture());
        assertThat(cap.getValue().getLactationDeathCount()).isEqualTo(0);
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
    @DisplayName("行238 部分断奶: 只选 2/3 头 → 只转这 2 头 + 只翻这 2 头，未选的留在原栏保持哺乳")
    void partialWean_onlySelectedPigletsAffected() {
        Pig pig = mkSow(360L, PigLifecycle.FM);
        when(pigMapper.selectById(360L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(560L, 360L, 3, 7777L);
        when(farrowMapper.selectById(560L)).thenReturn(farrow);
        // 本窝 3 头已建档仔猪，本次只断前 2 头
        stubPigletnoTable(List.of(
            mkPiglet(9201L, "P-001", 560L),
            mkPiglet(9202L, "P-002", 560L),
            mkPiglet(9203L, "P-003", 560L)
        ));

        WeaningBo bo = mkBo(360L, 560L, 2, new BigDecimal("16.000"));
        bo.setDetails(List.of(mkDetail(1, "P-001", "8.000"), mkDetail(2, "P-002", "8.000")));
        bo.setTransferBarnCode("B09");
        service.recordWeaning(bo);

        // 母猪 1 + 选中仔猪 2 = 3 次转移；P-003 不动
        ArgumentCaptor<TransferBo> cap = ArgumentCaptor.forClass(TransferBo.class);
        verify(transferService, times(3)).recordTransfer(cap.capture());
        assertThat(cap.getAllValues()).extracting(TransferBo::getPigId)
            .containsExactly(360L, 9201L, 9202L)
            .doesNotContain(9203L);
        // 翻育肥也只作用在选中的 2 头上（一次条件 update）
        verify(pigMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("行238 部分断奶: 本次明细一头带耳号都没有（匿名铺行）→ 退化整窝转移，向后兼容")
    void partialWean_anonymousDetails_fallsBackToWholeLitter() {
        Pig pig = mkSow(361L, PigLifecycle.FM);
        when(pigMapper.selectById(361L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(561L, 361L, 3, 7777L);
        when(farrowMapper.selectById(561L)).thenReturn(farrow);
        when(pigletnoMapper.selectList(any())).thenReturn(List.of(
            mkPiglet(9301L, "P-001"),
            mkPiglet(9302L, "P-002")
        ));

        WeaningBo bo = mkBo(361L, 561L, 2, new BigDecimal("16.000"));
        bo.setDetails(List.of(mkDetail(1, null, "8.000"), mkDetail(2, null, "8.000")));
        bo.setTransferBarnCode("B09");
        service.recordWeaning(bo);

        // 母猪 1 + 整窝 2 = 3 次
        verify(transferService, times(3)).recordTransfer(any(TransferBo.class));
    }

    @Test
    @DisplayName("行238 分批断奶: 本窝已有断奶记录且母猪已 DN → 不再推状态机（(DN,WEAN) 非法流转）")
    void partialWean_secondBatch_skipsStateMachine() {
        Pig pig = mkSow(362L, PigLifecycle.DN);
        when(pigMapper.selectById(362L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(562L, 362L, 10, 7777L);
        when(farrowMapper.selectById(562L)).thenReturn(farrow);
        when(weaningMapper.selectList(any())).thenReturn(List.of(mkPriorWeaning(8801L, 5, 0)));

        WeaningBo bo = mkBo(362L, 562L, 3, new BigDecimal("24.000"));
        bo.setDetails(List.of(mkDetail(1, "P-006", "8.000")));
        service.recordWeaning(bo);

        verify(weaningMapper, times(1)).insert(any(PigWeaning.class));
        verify(pigCoreService, never()).fireEvent(any());
    }

    @Test
    @DisplayName("行238 分批断奶: 首批（本窝无断奶记录）仍推状态机 FM → DN")
    void partialWean_firstBatch_firesStateMachine() {
        Pig pig = mkSow(363L, PigLifecycle.FM);
        when(pigMapper.selectById(363L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(563L, 363L, 10, 7777L);
        when(farrowMapper.selectById(563L)).thenReturn(farrow);
        when(weaningMapper.selectList(any())).thenReturn(List.of());

        WeaningBo bo = mkBo(363L, 563L, 4, new BigDecimal("32.000"));
        bo.setDetails(List.of(mkDetail(1, "P-001", "8.000")));
        service.recordWeaning(bo);

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo(PigStatusEvent.WEAN);
    }

    @Test
    @DisplayName("行238 分批断奶: 历史已断 6 + 本次 6 > 活产 10 → 拒绝（累计头数守恒）")
    void partialWean_cumulativeCountExceedsLiveBorn() {
        Pig pig = mkSow(364L, PigLifecycle.DN);
        when(pigMapper.selectById(364L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(564L, 364L, 10, 7777L);
        when(farrowMapper.selectById(564L)).thenReturn(farrow);
        when(weaningMapper.selectList(any())).thenReturn(List.of(mkPriorWeaning(8802L, 6, 0)));

        WeaningBo bo = mkBo(364L, 564L, 6, new BigDecimal("48.000"));
        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("weaning.count_exceeds_live_born");
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("行239①: 明细行落母猪耳号快照 sow_ear_no")
    void detailRows_carrySowEarNo() {
        Pig pig = mkSow(365L, PigLifecycle.FM);
        when(pigMapper.selectById(365L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(565L, 365L, 10, 7777L);
        when(farrowMapper.selectById(565L)).thenReturn(farrow);

        WeaningBo bo = mkBo(365L, 565L, 2, new BigDecimal("16.000"));
        bo.setDetails(List.of(mkDetail(1, "P-001", "8.000"), mkDetail(2, "P-002", "8.000")));
        service.recordWeaning(bo);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PigWeaningDetail>> cap = ArgumentCaptor.forClass(List.class);
        verify(weaningDetailMapper).insertBatch(cap.capture());
        assertThat(cap.getValue()).extracting(PigWeaningDetail::getSowEarNo)
            .containsOnly("260520-001");
    }

    @Test
    @DisplayName("行238 第4点: 待断奶列表剔除已断奶耳号与已终止仔猪")
    void listPigletsByFarrow_excludesWeanedAndEnded() {
        when(pigletnoMapper.selectList(any())).thenReturn(List.of(
            mkPiglet(9401L, "P-001", 570L),   // 已断奶 → 剔除
            mkPiglet(9402L, "P-002", 570L),   // 已死亡 → 剔除
            mkPiglet(9403L, "P-003", 570L)    // 仍在哺乳 → 保留
        ));
        // D-0113：判据走不绑窝的 selectAlreadyWeanedEarNos，不再是「本窝那几条断奶记录的明细」
        when(weaningMapper.selectAlreadyWeanedEarNos(any(), any())).thenReturn(List.of("P-001"));
        Pig dead = mkSow(9402L, PigLifecycle.END);
        when(pigMapper.selectByIds(any())).thenReturn(List.of(dead));

        var vos = service.listPigletsByFarrow(570L);

        assertThat(vos).extracting(WeaningPigletVo::getEarNo).containsExactly("P-003");
        // 序号在过滤后重排，从 1 起
        assertThat(vos.get(0).getPigletSeq()).isEqualTo(1);
    }

    @Test
    @DisplayName("D-0113：寄养走、断在别的母猪名下的仔猪，不能再出现在生母的录入页逐头行上")
    void listPigletsByFarrow_excludesFosteredAwayPiglet() {
        when(pigletnoMapper.selectList(any())).thenReturn(List.of(
            mkPiglet(9411L, "P-011", 571L),
            mkPiglet(9412L, "P-012", 571L)   // 被寄养到别的母猪名下断掉了
        ));
        // 关键：判据不看 farrowId —— 若实现退回「只查本窝那几条断奶记录的明细」，P-012 会漏判、继续铺行，
        // 整窝被寄养走时更会把母猪卡成既断不掉又配不了种的僵尸。
        when(weaningMapper.selectAlreadyWeanedEarNos(any(), any())).thenReturn(List.of("P-012"));
        when(pigMapper.selectByIds(any())).thenReturn(List.of());

        assertThat(service.listPigletsByFarrow(571L))
            .extracting(WeaningPigletVo::getEarNo)
            .containsExactly("P-011");
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

    @Test
    @DisplayName("D-0065 头数守恒(贴标窝): 本窝历史已断的头数要算进预算 —— 不算的话贴标窝可以无限超断")
    void taggedLitter_priorWeanedHeadsConsumeBudget() {
        Pig pig = mkSow(374L, PigLifecycle.FM);
        when(pigMapper.selectById(374L)).thenReturn(pig);
        // 本窝活产 3 头，历史已经断掉 2 头（逐头行判出来的），本次再断 2 头 → 2+2=4 > 3 必须拦
        PigFarrow farrow = mkFarrow(574L, 374L, 3, 7794L);
        when(farrowMapper.selectById(574L)).thenReturn(farrow);
        stubPigletnoTable(List.of(
            mkPiglet(9331L, "P-031", 574L),
            mkPiglet(9332L, "P-032", 574L),
            mkPiglet(9333L, "P-033", 574L)
        ));
        // 第一次调用（提交去重守卫，查这两个耳号有没有断过）返空；第二次（算 ownBefore，查整窝）返已断的两头
        when(weaningMapper.selectAlreadyWeanedEarNos(any(), any()))
            .thenReturn(List.of(), List.of("P-031", "P-032"));

        WeaningBo bo = mkBo(374L, 574L, 2, new BigDecimal("16.000"));
        bo.setDetails(List.of(mkDetail(1, "P-033", "8.000"), mkDetail(2, null, "8.000")));

        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class);
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("状态机守卫看的是「她现在是不是 FM」：断过一次又被配种转成 PZ 的母猪不许再推 WEAN")
    void nonFmSow_skipsStateMachine() {
        // 她上一窝还剩仔猪没断，这次来补断。PZ 不是 WEAN 的合法起点，推了必撞非法流转；
        // 只判「是不是 DN」挡不住她 —— 页面上就是列在待断奶里、点进去必 400，且要等下一胎才解锁。
        Pig pig = mkSow(382L, PigLifecycle.PZ);
        when(pigMapper.selectById(382L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(582L, 382L, 4, 7802L);
        when(farrowMapper.selectById(582L)).thenReturn(farrow);
        stubPigletnoTable(List.of(mkPiglet(9701L, "Q-001", 582L)));

        WeaningBo bo = mkBo(382L, 582L, 1, new BigDecimal("8.000"));
        bo.setDetails(List.of(mkDetail(1, "Q-001", "8.000")));
        service.recordWeaning(bo);

        verify(weaningMapper, times(1)).insert(any(PigWeaning.class));
        verify(pigCoreService, never()).fireEvent(any(PigEventBo.class));
    }

    @Test
    @DisplayName("D-0115 寄养把生母那一窝掏空 → 同事务替生母结束哺乳，别留一头永远转不出 FM 的母猪")
    void fostering_closesEmptiedBirthSowLactation() {
        Pig host = mkSow(383L, PigLifecycle.FM);
        when(pigMapper.selectById(383L)).thenReturn(host);
        PigFarrow hostFarrow = mkFarrow(583L, 383L, 2, 7803L);
        when(farrowMapper.selectById(583L)).thenReturn(hostFarrow);
        // 本次只断一头，是从 birthFarrow 590 寄养过来的
        stubPigletnoTable(List.of(mkPiglet(9801L, "R-001", 590L)));
        PigFarrow birthFarrow = mkFarrow(590L, 391L, 1, 7810L);
        when(farrowMapper.selectById(590L)).thenReturn(birthFarrow);
        Pig birthSow = mkSow(391L, PigLifecycle.FM);
        when(pigMapper.selectById(391L)).thenReturn(birthSow);
        when(weaningMapper.countUnweanedPigletsInLitter(590L)).thenReturn(0);

        WeaningBo bo = mkBo(383L, 583L, 1, new BigDecimal("8.000"));
        bo.setDetails(List.of(mkDetail(1, "R-001", "8.000")));
        service.recordWeaning(bo);

        ArgumentCaptor<PigEventBo> cap = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(2)).fireEvent(cap.capture());
        assertThat(cap.getAllValues()).extracting(PigEventBo::getPigId)
            .as("养母自己转 DN，生母也要跟着结束哺乳")
            .containsExactly(383L, 391L);
    }

    @Test
    @DisplayName("D-0115 生母那一窝还有仔猪在哺乳 → 不许提前把她推走")
    void fostering_keepsBirthSowNursingWhenPigletsRemain() {
        Pig host = mkSow(384L, PigLifecycle.FM);
        when(pigMapper.selectById(384L)).thenReturn(host);
        PigFarrow hostFarrow = mkFarrow(584L, 384L, 2, 7804L);
        when(farrowMapper.selectById(584L)).thenReturn(hostFarrow);
        stubPigletnoTable(List.of(mkPiglet(9811L, "R-011", 591L)));
        // 生母那一窝与生母本人都要 stub —— 少了任何一个，代码会在「查不到」那一步就 return，
        // 于是「她还剩仔猪就不动」这道守卫删掉也照样绿（独立验收实测的假绿）。
        PigFarrow birthFarrow = mkFarrow(591L, 392L, 4, 7811L);
        when(farrowMapper.selectById(591L)).thenReturn(birthFarrow);
        when(pigMapper.selectById(392L)).thenReturn(mkSow(392L, PigLifecycle.FM));
        when(weaningMapper.countUnweanedPigletsInLitter(591L)).thenReturn(3);

        WeaningBo bo = mkBo(384L, 584L, 1, new BigDecimal("8.000"));
        bo.setDetails(List.of(mkDetail(1, "R-011", "8.000")));
        service.recordWeaning(bo);

        ArgumentCaptor<PigEventBo> cap = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(cap.capture());
        assertThat(cap.getValue().getPigId()).isEqualTo(384L);
    }

    @Test
    @DisplayName("状态机守卫只看母猪是不是已 DN：多窝母猪先断新窝再回头断老窝，不能撞非法流转")
    void alreadyDnSow_skipsStateMachineEvenForAnotherLitter() {
        // 母猪已 DN（上次断新窝时转的），现在来断老窝 —— 老窝自己没有任何历史断奶记录。
        // 旧判据「本窝已有断奶记录 且 母猪已 DN」在这里为假 → 走 else 推 (DN, WEAN) → 非法流转 400，
        // 而这头母猪会一直列在待断奶里、点进去永远提交不了。
        Pig pig = mkSow(380L, PigLifecycle.DN);
        when(pigMapper.selectById(380L)).thenReturn(pig);
        PigFarrow older = mkFarrow(580L, 380L, 4, 7800L);
        when(farrowMapper.selectById(580L)).thenReturn(older);
        when(weaningMapper.selectList(any())).thenReturn(List.of());   // 老窝无历史断奶记录
        stubPigletnoTable(List.of(
            mkPiglet(9501L, "O-001", 580L),
            mkPiglet(9502L, "O-002", 580L)
        ));

        WeaningBo bo = mkBo(380L, 580L, 2, new BigDecimal("16.000"));
        bo.setDetails(List.of(mkDetail(1, "O-001", "8.000"), mkDetail(2, "O-002", "8.000")));
        service.recordWeaning(bo);

        verify(weaningMapper, times(1)).insert(any(PigWeaning.class));
        verify(pigCoreService, never()).fireEvent(any(PigEventBo.class));
    }

    @Test
    @DisplayName("D-0113 未贴标窝: 上一批寄养的头不许占本窝 live_born —— 占了本窝自己那几头就永远断不了")
    void untaggedLitter_priorFosteredHeadsDoNotConsumeBudget() {
        Pig pig = mkSow(381L, PigLifecycle.FM);
        when(pigMapper.selectById(381L)).thenReturn(pig);
        // 整窝未贴标（pigletno 一行都没有），live_born=3。
        // 上一批已经在这头母猪名下断了 3 头，明细上记死了它们出生在别的窝（900）。
        PigFarrow farrow = mkFarrow(581L, 381L, 3, 7801L);
        when(farrowMapper.selectById(581L)).thenReturn(farrow);
        when(weaningMapper.selectList(any())).thenReturn(List.of(mkPriorWeaning(8810L, 3, 0)));
        when(weaningDetailMapper.selectList(any())).thenReturn(List.of(
            mkSavedDetail(8810L, "F-101", 900L),
            mkSavedDetail(8810L, "F-102", 900L),
            mkSavedDetail(8810L, "F-103", 900L)));
        stubPigletnoTable(List.of());

        // 本次断本窝真正的 3 头（未贴标 → 匿名行，明细无耳号）
        WeaningBo bo = mkBo(381L, 581L, 3, new BigDecimal("24.000"));
        bo.setDetails(List.of(
            mkDetail(1, null, "8.000"), mkDetail(2, null, "8.000"), mkDetail(3, null, "8.000")));
        service.recordWeaning(bo);

        // 若把上一批那 3 头寄养的也算进本窝预算，这里会是 3+3 > 3 被误拦，本窝自己的仔猪永远断不掉
        verify(weaningMapper, times(1)).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("D-0113 未贴标窝: 历史明细记的是本窝出生 → 照常吃预算（含编造耳号：认不出出生窝一律算本窝）")
    void untaggedLitter_ownRecordedHeadsStillConsumeBudget() {
        Pig pig = mkSow(385L, PigLifecycle.FM);
        when(pigMapper.selectById(385L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(585L, 385L, 3, 7805L);
        when(farrowMapper.selectById(585L)).thenReturn(farrow);
        when(weaningMapper.selectList(any())).thenReturn(List.of(mkPriorWeaning(8811L, 3, 0)));
        // 上一批那 3 行虽然带着耳号，但出生窝记的是本窝（认不出出生窝的编造耳号就是这样落库的）
        when(weaningDetailMapper.selectList(any())).thenReturn(List.of(
            mkSavedDetail(8811L, "BOGUS-1", 585L),
            mkSavedDetail(8811L, "BOGUS-2", 585L),
            mkSavedDetail(8811L, "BOGUS-3", 585L)));
        stubPigletnoTable(List.of());

        WeaningBo bo = mkBo(385L, 585L, 3, new BigDecimal("24.000"));
        bo.setDetails(List.of(
            mkDetail(1, null, "8.000"), mkDetail(2, null, "8.000"), mkDetail(3, null, "8.000")));

        // 3（历史本窝）+ 3（本次）> 3 必须拦 —— 不拦的话编几个耳号就能把 3 头窝录成 6 头
        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class);
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("D-0065 两把尺子取 max①：逐头判看不见编造/匿名头 —— 要靠明细记的出生窝把它们数回来")
    void ownBefore_usesRecordedWhenPerPigletUnderCounts() {
        Pig pig = mkSow(388L, PigLifecycle.FM);
        when(pigMapper.selectById(388L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(588L, 388L, 9, 7808L);
        when(farrowMapper.selectById(588L)).thenReturn(farrow);
        stubPigletnoTable(List.of(
            mkPiglet(9911L, "T-001", 588L), mkPiglet(9912L, "T-002", 588L),
            mkPiglet(9913L, "T-003", 588L), mkPiglet(9914L, "T-004", 588L),
            mkPiglet(9915L, "T-005", 588L)));
        // 历史那一笔在本窝名下记了 6 头（编造耳号 + 匿名行），逐头判一头都数不到
        when(weaningMapper.selectList(any())).thenReturn(List.of(mkPriorWeaning(8820L, 6, 0)));
        when(weaningDetailMapper.selectList(any())).thenReturn(List.of(
            mkSavedDetail(8820L, "FAKE-1", 588L), mkSavedDetail(8820L, "FAKE-2", 588L),
            mkSavedDetail(8820L, "FAKE-3", 588L), mkSavedDetail(8820L, null, 588L),
            mkSavedDetail(8820L, null, 588L), mkSavedDetail(8820L, null, 588L)));
        when(weaningMapper.selectAlreadyWeanedEarNos(any(), any())).thenReturn(List.of(), List.of());

        // 再断本窝 5 头真耳号：6 + 5 = 11 > 9 必须拦。只用逐头判的话 ownBefore=0 → 5 ≤ 9 放行
        WeaningBo bo = mkBo(388L, 588L, 5, new BigDecimal("40.000"));
        bo.setDetails(List.of(mkDetail(1, "T-001", "8.000"), mkDetail(2, "T-002", "8.000"),
            mkDetail(3, "T-003", "8.000"), mkDetail(4, "T-004", "8.000"), mkDetail(5, "T-005", "8.000")));

        assertThatThrownBy(() -> service.recordWeaning(bo)).isInstanceOf(ServiceException.class);
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("D-0065 两把尺子取 max②：本窝断奶记录为空时（整窝被寄养走断在别人名下）靠逐头判数回来")
    void ownBefore_usesPerPigletWhenRecordedUnderCounts() {
        Pig pig = mkSow(389L, PigLifecycle.FM);
        when(pigMapper.selectById(389L)).thenReturn(pig);
        // live_born=3，本窝 4 头档案里已有 3 头被寄养走、断在别人名下 → 本窝自己一条断奶记录都没有
        PigFarrow farrow = mkFarrow(589L, 389L, 3, 7809L);
        when(farrowMapper.selectById(589L)).thenReturn(farrow);
        stubPigletnoTable(List.of(
            mkPiglet(9921L, "U-001", 589L), mkPiglet(9922L, "U-002", 589L),
            mkPiglet(9923L, "U-003", 589L), mkPiglet(9924L, "U-004", 589L)));
        when(weaningMapper.selectList(any())).thenReturn(List.of());
        // 去重守卫查本次那一头（未断）→ 空；算 ownBefore 查整窝 → 3 头已断
        when(weaningMapper.selectAlreadyWeanedEarNos(any(), any()))
            .thenReturn(List.of(), List.of("U-001", "U-002", "U-003"));

        WeaningBo bo = mkBo(389L, 589L, 1, new BigDecimal("8.000"));
        bo.setDetails(List.of(mkDetail(1, "U-004", "8.000")));

        // 3 + 1 = 4 > 3 必须拦。只看本窝断奶明细的话 recorded=0 → 1 ≤ 3 放行
        assertThatThrownBy(() -> service.recordWeaning(bo)).isInstanceOf(ServiceException.class);
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("D-0113 迁移前的历史明细 birth_farrow_id 为 NULL → 按本窝算（那时还没有寄养）")
    void legacyDetailsWithNullBirthFarrowCountAsOwn() {
        Pig pig = mkSow(390L, PigLifecycle.FM);
        when(pigMapper.selectById(390L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(592L, 390L, 3, 7812L);
        when(farrowMapper.selectById(592L)).thenReturn(farrow);
        stubPigletnoTable(List.of());
        when(weaningMapper.selectList(any())).thenReturn(List.of(mkPriorWeaning(8830L, 3, 0)));
        // 迁移前落库的行：有耳号但 birth_farrow_id 为空
        when(weaningDetailMapper.selectList(any())).thenReturn(List.of(
            mkSavedDetail(8830L, "V-001", null), mkSavedDetail(8830L, "V-002", null),
            mkSavedDetail(8830L, "V-003", null)));

        WeaningBo bo = mkBo(390L, 592L, 1, new BigDecimal("8.000"));
        bo.setDetails(List.of(mkDetail(1, null, "8.000")));

        // 把 NULL 当成「不是本窝」的话 ownBefore=0 → 1 ≤ 3 放行；按本窝算才是 3+1 > 3
        assertThatThrownBy(() -> service.recordWeaning(bo)).isInstanceOf(ServiceException.class);
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("同一笔里同一个耳号出现两次 → 拒（否则多一行明细、覆盖个体断奶重，而守恒只计一头）")
    void duplicatedEarNoInOneSubmit_rejected() {
        Pig pig = mkSow(393L, PigLifecycle.FM);
        when(pigMapper.selectById(393L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(593L, 393L, 6, 7813L);
        when(farrowMapper.selectById(593L)).thenReturn(farrow);
        stubPigletnoTable(List.of(mkPiglet(9931L, "W-001", 593L)));

        WeaningBo bo = mkBo(393L, 593L, 2, new BigDecimal("16.000"));
        bo.setDetails(List.of(mkDetail(1, "W-001", "8.000"), mkDetail(2, "W-001", "9.000")));

        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("W-001");
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("匿名头按明细行数算，不拿 weanedCount 去减 —— 12 行匿名配 weanedCount=1 不许放行")
    void anonymousHeadsCountedFromDetailRows() {
        Pig pig = mkSow(394L, PigLifecycle.FM);
        when(pigMapper.selectById(394L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(594L, 394L, 9, 7814L);
        when(farrowMapper.selectById(594L)).thenReturn(farrow);
        stubPigletnoTable(List.of());

        WeaningBo bo = mkBo(394L, 594L, 1, new BigDecimal("8.000"));
        bo.setDetails(java.util.stream.IntStream.rangeClosed(1, 12)
            .mapToObj(i -> mkDetail(i, null, "8.000")).toList());

        assertThatThrownBy(() -> service.recordWeaning(bo)).isInstanceOf(ServiceException.class);
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("D-0113 本次头数的归属判定必须与落库的 birth_farrow_id 逐字一致 —— 否则编造耳号能一发录爆本窝")
    void bogusEarNosCountAsOwnHeads() {
        Pig pig = mkSow(387L, PigLifecycle.FM);
        when(pigMapper.selectById(387L)).thenReturn(pig);
        // 未贴标窝，live_born 只有 3
        PigFarrow farrow = mkFarrow(587L, 387L, 3, 7807L);
        when(farrowMapper.selectById(587L)).thenReturn(farrow);
        stubPigletnoTable(List.of());

        // 10 个库里根本不存在的耳号。按「在不在本窝 pigletno 里」判会算成 0 本窝头一路放行；
        // 按落库那一套（认不出就算本窝）判才是 10 > 3。
        WeaningBo bo = mkBo(387L, 587L, 10, new BigDecimal("80.000"));
        bo.setDetails(java.util.stream.IntStream.rangeClosed(1, 10)
            .mapToObj(i -> mkDetail(i, "BOGUS-" + i, "8.000")).toList());

        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class);
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("D-0113 明细落库即记出生窝：寄养头记生母窝、匿名行与认不出的耳号记本窝")
    void detailsRecordBirthFarrowAtInsert() {
        Pig pig = mkSow(386L, PigLifecycle.FM);
        when(pigMapper.selectById(386L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(586L, 386L, 9, 7806L);
        when(farrowMapper.selectById(586L)).thenReturn(farrow);
        stubPigletnoTable(List.of(
            mkPiglet(9901L, "S-001", 586L),     // 本窝自己的
            mkPiglet(9902L, "S-101", 777L)      // 寄养来的
        ));
        when(weaningMapper.countUnweanedPigletsInLitter(777L)).thenReturn(2);

        WeaningBo bo = mkBo(386L, 586L, 3, new BigDecimal("24.000"));
        bo.setDetails(List.of(
            mkDetail(1, "S-001", "8.000"),
            mkDetail(2, "S-101", "8.000"),
            mkDetail(3, "NOT-IN-DB", "8.000")));
        service.recordWeaning(bo);

        ArgumentCaptor<List<PigWeaningDetail>> cap = ArgumentCaptor.forClass(List.class);
        verify(weaningDetailMapper).insertBatch(cap.capture());
        assertThat(cap.getValue()).extracting(PigWeaningDetail::getBirthFarrowId)
            .as("认不出出生窝的一律记本窝 —— 那是保守的一侧，不会白拿一个免费名额")
            .containsExactly(586L, 777L, 586L);
    }

    /** 已落库的断奶明细行（耳号 + 出生窝，用于区分「本窝的」与「寄养来的」）。 */
    private PigWeaningDetail mkSavedDetail(Long weaningId, String earNo, Long birthFarrowId) {
        PigWeaningDetail d = new PigWeaningDetail();
        d.setWeaningId(weaningId);
        d.setEarNo(earNo);
        d.setBirthFarrowId(birthFarrowId);
        return d;
    }

    @Test
    @DisplayName("跳过集合刻意不含 YF / END / HB：那些猪来断奶是数据错乱，非法流转是唯一防线，不能一起放掉")
    void skipSetExcludesNonLactationStatuses() throws Exception {
        java.lang.reflect.Field f = WeaningServiceImpl.class.getDeclaredField("POST_LACTATION_STATUSES");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> skip = (Set<String>) f.get(null);

        assertThat(skip)
            .as("母猪分娩之后能合法到达的非终态，除 FM 外正好是这五个")
            .containsExactlyInAnyOrder(PigLifecycle.DN.name(), PigLifecycle.PZ.name(),
                PigLifecycle.LC.name(), PigLifecycle.KH.name(), PigLifecycle.FQ.name());
        assertThat(skip)
            .as("FM 必须不在里面 —— 在里面的话母猪第一次断奶也不推，她永远转不出哺乳态")
            .doesNotContain(PigLifecycle.FM.name());
        assertThat(skip)
            .as("没有任何 transition 指向 HB/YF，分娩过的母猪回不到这两个状态；放进来等于给纯脏数据开门")
            .doesNotContain(PigLifecycle.HB.name(), PigLifecycle.YF.name());
        assertThat(skip)
            .as("END 放进来就绕过了终态闸 —— 死猪也能录断奶")
            .doesNotContain(PigLifecycle.END.name());
    }

    @Test
    @DisplayName("D-0113 寄养: 别窝的仔猪跟着本窝母猪断 → 一样转栏 + 翻育肥（认耳号不认窝）")
    void fosteredPiglet_isTransferredAndFlipped() {
        Pig pig = mkSow(370L, PigLifecycle.FM);
        when(pigMapper.selectById(370L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(570L, 370L, 2, 7790L);
        when(farrowMapper.selectById(570L)).thenReturn(farrow);
        // 假库里四头：本窝 2 头 + 寄养进来的 1 头 + 一头谁也没勾的。
        // 若实现退回「按 farrowId 查整窝」，wrapper 里没有耳号参数 → 假库整表返回 → 转移会变 5 次，断言必红。
        stubPigletnoTable(List.of(
            mkPiglet(9301L, "P-001", 570L),
            mkPiglet(9302L, "P-002", 570L),
            mkPiglet(9401L, "F-001", 999L),
            mkPiglet(9402L, "F-002", 999L)
        ));

        WeaningBo bo = mkBo(370L, 570L, 3, new BigDecimal("24.000"));
        bo.setDetails(List.of(
            mkDetail(1, "P-001", "8.000"),
            mkDetail(2, "P-002", "8.000"),
            mkDetail(3, "F-001", "8.000")));
        bo.setTransferBarnCode("B09");
        service.recordWeaning(bo);

        ArgumentCaptor<TransferBo> cap = ArgumentCaptor.forClass(TransferBo.class);
        verify(transferService, times(4)).recordTransfer(cap.capture());
        assertThat(cap.getAllValues()).extracting(TransferBo::getPigId)
            .containsExactly(370L, 9301L, 9302L, 9401L)
            .doesNotContain(9402L);
        verify(pigMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("D-0113 头数守恒: 寄养头不吃养母窝的 live_born 预算 —— 本窝 2 头满额时仍可再带 1 头寄养的")
    void fosteredHeadsDoNotConsumeHostLitterBudget() {
        Pig pig = mkSow(371L, PigLifecycle.FM);
        when(pigMapper.selectById(371L)).thenReturn(pig);
        // 本窝活产仔数 2，本次断 3 头：本窝 2 头（正好满额）+ 寄养 1 头。按 weanedCount(3) 撞就会误拦。
        PigFarrow farrow = mkFarrow(571L, 371L, 2, 7791L);
        when(farrowMapper.selectById(571L)).thenReturn(farrow);
        stubPigletnoTable(List.of(
            mkPiglet(9311L, "P-011", 571L),
            mkPiglet(9312L, "P-012", 571L),
            mkPiglet(9411L, "F-011", 998L)
        ));

        WeaningBo bo = mkBo(371L, 571L, 3, new BigDecimal("24.000"));
        bo.setDetails(List.of(
            mkDetail(1, "P-011", "8.000"),
            mkDetail(2, "P-012", "8.000"),
            mkDetail(3, "F-011", "8.000")));
        service.recordWeaning(bo);

        verify(weaningMapper, times(1)).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("D-0065 头数守恒: 本窝自己的头数仍然撞 live_born —— 放宽的只是寄养那部分，不是整条闸")
    void ownHeadsStillHitLiveBornCeiling() {
        Pig pig = mkSow(373L, PigLifecycle.FM);
        when(pigMapper.selectById(373L)).thenReturn(pig);
        // 本窝活产仔数 2，却拿本窝 3 头来断 —— 与寄养无关，必须拦
        PigFarrow farrow = mkFarrow(573L, 373L, 2, 7793L);
        when(farrowMapper.selectById(573L)).thenReturn(farrow);
        stubPigletnoTable(List.of(
            mkPiglet(9321L, "P-021", 573L),
            mkPiglet(9322L, "P-022", 573L),
            mkPiglet(9323L, "P-023", 573L)
        ));

        WeaningBo bo = mkBo(373L, 573L, 3, new BigDecimal("24.000"));
        bo.setDetails(List.of(
            mkDetail(1, "P-021", "8.000"),
            mkDetail(2, "P-022", "8.000"),
            mkDetail(3, "P-023", "8.000")));

        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class);
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("D-0065 头数守恒: 匿名铺行（无耳号）仍撞 live_born —— 那种窝没有耳号可去重，只剩头数能拦")
    void anonymousSubmit_stillHitsLiveBornCeiling() {
        Pig pig = mkSow(372L, PigLifecycle.FM);
        when(pigMapper.selectById(372L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(572L, 372L, 2, 7792L);
        when(farrowMapper.selectById(572L)).thenReturn(farrow);

        WeaningBo bo = mkBo(372L, 572L, 3, new BigDecimal("18.000"));
        bo.setDetails(List.of(mkDetail(1, null, "6.000"), mkDetail(2, null, "6.000"),
            mkDetail(3, null, "6.000")));

        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class);
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("行238 去重: 本窝已断过的耳号再提交一次 → 拒绝（改造前靠状态机非法流转挡，跳过状态机后必须显式拦）")
    void partialWean_rejectsAlreadyWeanedPiglet() {
        Pig pig = mkSow(361L, PigLifecycle.DN);
        when(pigMapper.selectById(361L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(561L, 361L, 3, 7778L);
        when(farrowMapper.selectById(561L)).thenReturn(farrow);
        // 本窝 P-001 已经断过
        when(weaningMapper.selectAlreadyWeanedEarNos(any(), any()))
            .thenReturn(List.of("P-001"));

        WeaningBo bo = mkBo(361L, 561L, 1, new BigDecimal("9.000"));
        bo.setDetails(List.of(mkDetail(1, "P-001", "9.000")));

        assertThatThrownBy(() -> service.recordWeaning(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("P-001");
        // 一行都不许落：重复断会多一行明细、覆盖个体断奶重，还会吃掉头数守恒预算把整窝卡死
        verify(weaningMapper, never()).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("行238 去重: 明细全无耳号（匿名铺行）不查去重，退化整窝行为不变")
    void partialWean_anonymousDetailsSkipDedupProbe() {
        Pig pig = mkSow(362L, PigLifecycle.FM);
        when(pigMapper.selectById(362L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(562L, 362L, 2, 7779L);
        when(farrowMapper.selectById(562L)).thenReturn(farrow);

        WeaningBo bo = mkBo(362L, 562L, 2, new BigDecimal("12.000"));
        bo.setDetails(List.of(mkDetail(1, null, "6.000"), mkDetail(2, null, "6.000")));
        service.recordWeaning(bo);

        verify(weaningMapper, never()).selectAlreadyWeanedEarNos(any(), any());
        verify(weaningMapper, times(1)).insert(any(PigWeaning.class));
    }

    @Test
    @DisplayName("行239① 明细 VO 必须带母猪耳号 —— 字段声明了却不 set 等于接口对外撒谎")
    void detailVoCarriesSowEarNo() {
        Pig pig = mkSow(363L, PigLifecycle.FM);
        when(pigMapper.selectById(363L)).thenReturn(pig);
        PigFarrow farrow = mkFarrow(563L, 363L, 1, 7780L);
        when(farrowMapper.selectById(563L)).thenReturn(farrow);

        WeaningBo bo = mkBo(363L, 563L, 1, new BigDecimal("6.000"));
        bo.setDetails(List.of(mkDetail(1, "P-010", "6.000")));
        PigWeaningVo vo = service.recordWeaning(bo);

        assertThat(vo.getDetails()).isNotEmpty();
        assertThat(vo.getDetails().get(0).getSowEarNo()).isEqualTo(pig.getEarNo());
    }

}
