package org.dromara.djs.warehouse.pack.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KG 打包的需求规划与扣减（{@link ProductProductionServiceImpl#planKgDemandRows} +
 * {@link ProductProductionServiceImpl#deductKgPlannedRows}）。
 *
 * <p><b>本组用例守的核心不变量：规划出几行需求，上游就落几条产出记录。</b>门店同一天分批下单会给同一产品
 * 落多行需求，而一条产出记录在出车清点时被绑死到一行需求（{@code availableProductionWrapper} 只认
 * {@code demand_id IS NULL}）。两边不守恒就必然出事：</p>
 * <ul>
 *   <li>只扣一行 → 剩下的行永远备不齐，整店被「全有或全无」出车闸拦死，而页头满足率按生产量算仍是 100%。</li>
 *   <li>扣满 N 行只落 1 条记录 → 多出来的需求过了闸却无货可选，整店发一半卡在车边。</li>
 * </ul>
 *
 * <p>另一条同等重要的不变量：<b>下界不得比改动前更严</b>。工人手上只有一头猪的量时必须照样能提交、
 * 照样扣满第一行，否则打包台当场记不了账。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KG 打包：需求行规划 = 产出记录数")
class KgDemandCrossRowDeductTest {

    @Mock private ProductProductionMapper productionMapper;
    @Mock private ProductInhouseMapper inhouseMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private LocationInfoMapper locationInfoMapper;
    @Mock private LocationStockMapper locationStockMapper;
    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private org.dromara.djs.common.store.mapper.StoreMapper storeMapper;
    @Mock private org.dromara.djs.plant.plot.mapper.PlotInfoMapper plotInfoMapper;
    @Mock private DemandManageMapper demandManageMapper;
    @Mock private org.dromara.djs.warehouse.cross.mapper.BarInfoMapper barInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private org.dromara.djs.warehouse.trace.service.ITraceService traceService;
    @Mock private org.dromara.djs.warehouse.check.service.IStockCheckService stockCheckService;

    private ProductProductionServiceImpl service;

    private static final Long PRODUCT_ID = 9304000000000129L;   // 黑毛猪猪肚（kg）
    private static final Long STORE_ID = 2087480445936111617L;
    private static final Long ROW_A = 2095441874320445442L;     // 门店 17:20 那批
    private static final Long ROW_B = 2095443669633552386L;     // 门店 17:27 那批
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 4);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @BeforeEach
    void setup() {
        service = new ProductProductionServiceImpl(
            productionMapper, inhouseMapper, productInfoMapper,
            locationInfoMapper, locationStockMapper, stockFlowMapper, storeMapper, plotInfoMapper,
            demandManageMapper, barInfoMapper, bizCodeGenerator, traceService, stockCheckService);
        when(demandManageMapper.incrementShipped(anyLong(), anyString(), any())).thenReturn(1);
    }

    private static ProductInfo kgProduct() {
        ProductInfo p = new ProductInfo();
        p.setId(PRODUCT_ID);
        p.setProductUnit("kg");
        return p;
    }

    /** 一行需求：{@code demand_date} / {@code demand_quantity} / {@code shipped_count}。 */
    private static DemandManage row(Long id, LocalDate date, String qty, String shipped) {
        DemandManage d = new DemandManage();
        d.setId(id);
        d.setDemandDate(date);
        d.setDemandQuantity(new BigDecimal(qty));
        d.setShippedCount(new BigDecimal(shipped));
        return d;
    }

    private void givenCandidates(DemandManage... rows) {
        when(demandManageMapper.selectUncompletedDemands(PRODUCT_ID, STORE_ID)).thenReturn(List.of(rows));
    }

    /** 规划 + 扣减一把跑完，返回规划出的行（上游据此决定落几条产出记录）。 */
    private List<DemandManage> planAndDeduct(String weighedKg) {
        List<DemandManage> planned = service.planKgDemandRows(
            kgProduct(), STORE_ID, new BigDecimal(weighedKg), "platform");
        service.deductKgPlannedRows(planned, PRODUCT_ID, STORE_ID, new BigDecimal(weighedKg));
        return planned;
    }

    /** 实际落到 incrementShipped 的扣减量，按需求行分组。 */
    private List<BigDecimal> deltasFor(Long demandId) {
        ArgumentCaptor<Long> ids = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<BigDecimal> deltas = ArgumentCaptor.forClass(BigDecimal.class);
        verify(demandManageMapper, atLeast(0)).incrementShipped(ids.capture(), anyString(), deltas.capture());
        List<Long> idList = ids.getAllValues();
        List<BigDecimal> deltaList = deltas.getAllValues();
        return java.util.stream.IntStream.range(0, idList.size())
            .filter(i -> demandId.equals(idList.get(i)))
            .mapToObj(deltaList::get)
            .toList();
    }

    /** 该需求行恰好被扣一次，且扣的量按数值等于 expected（不比 BigDecimal scale）。 */
    private void assertSingleDelta(Long demandId, String expected) {
        List<BigDecimal> deltas = deltasFor(demandId);
        assertThat(deltas).as("需求行 %s 的扣减次数", demandId).hasSize(1);
        assertThat(deltas.get(0)).as("需求行 %s 的扣减量", demandId).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("★同日两行各 1kg，一次称 2kg → 规划 2 行、两行都扣满，上游据此落 2 条记录")
    void sameDayTwoRows_planTwoAndFillBoth() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"),
                        row(ROW_B, TODAY, "1.000", "0.000"));

        List<DemandManage> planned = planAndDeduct("2.000");

        assertThat(planned).extracting(DemandManage::getId).containsExactly(ROW_A, ROW_B);
        assertSingleDelta(ROW_A, "1.000");
        assertSingleDelta(ROW_B, "1.000");
    }

    @Test
    @DisplayName("★不得比改动前更严：同日两行各 1kg，只称到 1.2kg（手上一头猪）→ 放行、扣满第一行，不抛")
    void partialWeighStillAccepted_noNewRejection() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"),
                        row(ROW_B, TODAY, "1.000", "0.000"));

        List<DemandManage> planned = planAndDeduct("1.200");

        assertThat(planned).extracting(DemandManage::getId).containsExactly(ROW_A);
        assertSingleDelta(ROW_A, "1.000");
        assertThat(deltasFor(ROW_B)).isEmpty();
    }

    @Test
    @DisplayName("★续打：第一行已满、第二行还缺，再称 1.1kg → 只规划并扣满第二行")
    void secondPackFillsRemainingRow() {
        givenCandidates(row(ROW_B, TODAY, "1.000", "0.000"));

        List<DemandManage> planned = planAndDeduct("1.100");

        assertThat(planned).extracting(DemandManage::getId).containsExactly(ROW_B);
        assertSingleDelta(ROW_B, "1.000");
    }

    @Test
    @DisplayName("规划行数 = 产出记录重量条数，且各条之和恒等于本次称重（富余落最后一条）")
    void recordWeightsMatchPlannedRowsAndSumToWeighed() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"),
                        row(ROW_B, TODAY, "1.000", "0.000"));

        List<DemandManage> planned = service.planKgDemandRows(
            kgProduct(), STORE_ID, new BigDecimal("2.300"), "platform");
        List<BigDecimal> parts = ProductProductionServiceImpl.splitByDemandRows(
            new BigDecimal("2.300"), planned);

        assertThat(parts).hasSameSizeAs(planned);
        assertThat(parts.get(0)).isEqualByComparingTo("1.000");
        assertThat(parts.get(1)).isEqualByComparingTo("1.300");
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("2.300");
    }

    @Test
    @DisplayName("连第一行都填不满（0.8 < 1.0）→ 抛「重量未满足需求」，且一行都不扣")
    void belowFirstRow_throwsAndWritesNothing() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"),
                        row(ROW_B, TODAY, "1.000", "0.000"));

        assertThatThrownBy(() -> planAndDeduct("0.800"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("重量未满足需求");

        verify(demandManageMapper, never()).incrementShipped(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("只吃最早那一天：今天 1kg + 明天 1kg，称 2kg → 只规划今天那行，明天的不动")
    void tomorrowRowNeverPlanned() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"),
                        row(ROW_B, TOMORROW, "1.000", "0.000"));

        List<DemandManage> planned = planAndDeduct("2.000");

        assertThat(planned).extracting(DemandManage::getId).containsExactly(ROW_A);
        assertSingleDelta(ROW_A, "1.000");
        assertThat(deltasFor(ROW_B)).isEmpty();
    }

    @Test
    @DisplayName("PARTIAL_SHIPPED 续打（真实可达状态）：A 剩 1.5 / B 剩 1.0，称 2.5 → 两行都扣满，扣的是剩余量不是需求量")
    void partiallyShippedRows_takeRemainderNotQuantity() {
        givenCandidates(row(ROW_A, TODAY, "2.000", "0.500"),
                        row(ROW_B, TODAY, "1.000", "0.000"));

        List<DemandManage> planned = planAndDeduct("2.500");

        assertThat(planned).extracting(DemandManage::getId).containsExactly(ROW_A, ROW_B);
        assertSingleDelta(ROW_A, "1.500");
        assertSingleDelta(ROW_B, "1.000");
    }

    @Test
    @DisplayName("礼盒组件（deliver_dest=gift）不扣门店需求，也不按需求行拆记录")
    void giftDestNeverPlans() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"));

        List<DemandManage> planned = service.planKgDemandRows(
            kgProduct(), STORE_ID, new BigDecimal("2.000"), "gift");
        service.deductKgPlannedRows(planned, PRODUCT_ID, STORE_ID, new BigDecimal("2.000"));

        assertThat(planned).isEmpty();
        verify(demandManageMapper, never()).incrementShipped(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("非 KG 产品不走本路径：规划恒空（份数扣减由 deductDemandOnPack 负责）")
    void nonKgProductNeverPlans() {
        ProductInfo copies = new ProductInfo();
        copies.setId(PRODUCT_ID);
        copies.setProductUnit("份");
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"));

        assertThat(service.planKgDemandRows(copies, STORE_ID, new BigDecimal("2.000"), "platform")).isEmpty();
    }

    @Test
    @DisplayName("无匹配未完成需求 → 规划空、不抛不扣（打包主链路不被需求扣减阻塞）")
    void noCandidates_skipsSilently() {
        when(demandManageMapper.selectUncompletedDemands(PRODUCT_ID, STORE_ID)).thenReturn(List.of());

        assertThatCode(() -> assertThat(planAndDeduct("2.000")).isEmpty()).doesNotThrowAnyException();
        verify(demandManageMapper, never()).incrementShipped(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("KG 未选门店 → 在写任何产出记录之前就抛「请选择门店」")
    void nullStore_throwsBeforeAnyWrite() {
        assertThatThrownBy(() -> service.planKgDemandRows(
            kgProduct(), null, new BigDecimal("2.000"), "platform"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请选择门店");
    }

    @Test
    @DisplayName("并发守卫仍在：incrementShipped affected=0 → 抛 ServiceException 回滚本次打包")
    void concurrentGuardStillThrows() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"),
                        row(ROW_B, TODAY, "1.000", "0.000"));
        when(demandManageMapper.incrementShipped(eq(ROW_B), anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> planAndDeduct("2.000"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("并发履约");
    }

    @Test
    @DisplayName("demand_date 为空（防御性）→ 退化为只认首行，不 NPE")
    void nullDemandDate_fallsBackToFirstRow() {
        givenCandidates(row(ROW_A, null, "1.000", "0.000"),
                        row(ROW_B, null, "1.000", "0.000"));

        List<DemandManage> planned = planAndDeduct("2.000");

        assertThat(planned).extracting(DemandManage::getId).containsExactly(ROW_A);
        assertThat(deltasFor(ROW_B)).isEmpty();
    }
}
