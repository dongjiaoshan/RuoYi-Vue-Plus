package org.dromara.djs.warehouse.pack.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProductProductionServiceImpl#deductKgDemandComplete} 跨行扣减回归。
 *
 * <p>KG 产品一次称重必须扣满「最早那个需求日当天的全部未完成需求行」，不是只扣最早<b>一行</b>。
 * 门店同一天分批下单会给同一产品落多行需求，打包台屏上的「剩余需求」是跨行求和；只扣一行的话
 * 工人照屏幕称够了量、系统只认一部分，剩下的行永远备不齐，十几个小时后在发货月台被「全有或全无」
 * 出车闸拦死整店，而页头满足率（按生产量算）仍是 100%，现场读数自相矛盾。</p>
 *
 * <p>下界只钳「最早那一天」：候选行含 {@code demand_date >= 今天}，今天的车没发时明天的行也在里面，
 * 拿两天总量当下界会逼工人一次把两天的货称完。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KG 打包扣减：当天多行需求必须全部扣满")
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

    /** 实际落到 incrementShipped 的 (demandId, delta)，按调用顺序。 */
    private List<BigDecimal> capturedDeltasFor(Long demandId) {
        ArgumentCaptor<Long> ids = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<BigDecimal> deltas = ArgumentCaptor.forClass(BigDecimal.class);
        verify(demandManageMapper, org.mockito.Mockito.atLeast(0))
            .incrementShipped(ids.capture(), anyString(), deltas.capture());
        List<Long> idList = ids.getAllValues();
        List<BigDecimal> deltaList = deltas.getAllValues();
        return java.util.stream.IntStream.range(0, idList.size())
            .filter(i -> demandId.equals(idList.get(i)))
            .mapToObj(deltaList::get)
            .toList();
    }

    /** 该需求行恰好被扣一次，且扣的量按数值等于 expected（不比 BigDecimal scale）。 */
    private void assertSingleDelta(Long demandId, String expected) {
        List<BigDecimal> deltas = capturedDeltasFor(demandId);
        assertThat(deltas).as("需求行 %s 的扣减次数", demandId).hasSize(1);
        assertThat(deltas.get(0)).as("需求行 %s 的扣减量", demandId).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("★核心回归：同日两行各 1kg，一次称 2kg → 两行都扣满（旧实现只扣最早一行，另一行永远 0）")
    void sameDayTwoRows_bothFilled() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"),
                        row(ROW_B, TODAY, "1.000", "0.000"));

        service.deductKgDemandComplete(PRODUCT_ID, STORE_ID, new BigDecimal("2.000"));

        assertSingleDelta(ROW_A, "1.000");
        assertSingleDelta(ROW_B, "1.000");
        verify(demandManageMapper, times(2)).incrementShipped(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("称重不够当天总量（1.5 < 1+1）→ 抛「重量未满足需求」，且一行都不扣（不留半截写入）")
    void weighedBelowDayTotal_throwsAndWritesNothing() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"),
                        row(ROW_B, TODAY, "1.000", "0.000"));

        assertThatThrownBy(() -> service.deductKgDemandComplete(PRODUCT_ID, STORE_ID, new BigDecimal("1.500")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("重量未满足需求");

        verify(demandManageMapper, never()).incrementShipped(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("下界只看最早那一天：今天 1kg + 明天 1kg，称 1kg → 放行、只扣今天那行，明天那行不动")
    void tomorrowRowNotInLowerBound() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"),
                        row(ROW_B, TOMORROW, "1.000", "0.000"));

        assertThatCode(() -> service.deductKgDemandComplete(PRODUCT_ID, STORE_ID, new BigDecimal("1.000")))
            .doesNotThrowAnyException();

        assertSingleDelta(ROW_A, "1.000");
        assertThat(capturedDeltasFor(ROW_B)).isEmpty();
    }

    @Test
    @DisplayName("称重富余不会超额履约：两行各 1kg，称 5kg → 每行仍只扣自己的 1kg，合计 2kg")
    void surplusNeverOverFulfils() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"),
                        row(ROW_B, TODAY, "1.000", "0.000"));

        service.deductKgDemandComplete(PRODUCT_ID, STORE_ID, new BigDecimal("5.000"));

        assertSingleDelta(ROW_A, "1.000");
        assertSingleDelta(ROW_B, "1.000");
    }

    @Test
    @DisplayName("部分已备：同日 A 已扣满 / B 剩 1kg，称 1kg → 只补 B，A 不重复扣")
    void partiallyFilledDay_onlyRemainderTaken() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "1.000"),
                        row(ROW_B, TODAY, "1.000", "0.000"));

        service.deductKgDemandComplete(PRODUCT_ID, STORE_ID, new BigDecimal("1.000"));

        assertThat(capturedDeltasFor(ROW_A)).isEmpty();
        assertSingleDelta(ROW_B, "1.000");
    }

    @Test
    @DisplayName("无匹配未完成需求 → 不抛不扣（打包主链路不被需求扣减阻塞）")
    void noCandidates_skipsSilently() {
        when(demandManageMapper.selectUncompletedDemands(PRODUCT_ID, STORE_ID)).thenReturn(List.of());

        assertThatCode(() -> service.deductKgDemandComplete(PRODUCT_ID, STORE_ID, new BigDecimal("2.000")))
            .doesNotThrowAnyException();

        verify(demandManageMapper, never()).incrementShipped(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("并发守卫仍在：incrementShipped affected=0 → 抛 ServiceException 回滚本次打包")
    void concurrentGuardStillThrows() {
        givenCandidates(row(ROW_A, TODAY, "1.000", "0.000"),
                        row(ROW_B, TODAY, "1.000", "0.000"));
        when(demandManageMapper.incrementShipped(eq(ROW_B), anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.deductKgDemandComplete(PRODUCT_ID, STORE_ID, new BigDecimal("2.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("并发履约");
    }

    @Test
    @DisplayName("demand_date 为空（防御性）→ 退化为只认首行，不 NPE")
    void nullDemandDate_fallsBackToFirstRow() {
        givenCandidates(row(ROW_A, null, "1.000", "0.000"),
                        row(ROW_B, null, "1.000", "0.000"));

        assertThatCode(() -> service.deductKgDemandComplete(PRODUCT_ID, STORE_ID, new BigDecimal("1.000")))
            .doesNotThrowAnyException();

        assertSingleDelta(ROW_A, "1.000");
        assertThat(capturedDeltasFor(ROW_B)).isEmpty();
    }
}
