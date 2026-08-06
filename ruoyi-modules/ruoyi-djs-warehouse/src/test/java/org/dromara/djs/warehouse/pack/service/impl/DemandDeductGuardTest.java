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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

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
 * {@link ProductProductionServiceImpl#deductDemandOnPack} 并发上界守卫单测（F1FIX TXN-2）。
 *
 * <p>守卫语义：{@code incrementShipped} 的 UPDATE 在 DB 端原子校验
 * {@code COALESCE(shipped_count,0) + delta <= demand_quantity}；affected==0（并发打包
 * 已把剩余份数吃掉 / 需求行已删）→ service 抛 {@link ServiceException} 回滚本次打包，
 * 不允许 shipped_count 超上界（历史脏行「需求 5 发货 88」先例的根因收口）。</p>
 *
 * @author djs
 * @since F1FIX-TXN2
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("deductDemandOnPack 并发上界守卫")
class DemandDeductGuardTest {

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

    private static final Long DEMAND_ID = 50001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final Long STORE_ID = 5001L;

    @BeforeEach
    void setup() {
        service = new ProductProductionServiceImpl(
            productionMapper, inhouseMapper, productInfoMapper,
            locationInfoMapper, locationStockMapper, stockFlowMapper, storeMapper, plotInfoMapper,
            demandManageMapper, barInfoMapper, bizCodeGenerator, traceService, stockCheckService);

        DemandManage demand = new DemandManage();
        demand.setId(DEMAND_ID);
        demand.setDemandQuantity(new BigDecimal("10"));
        demand.setShippedCount(new BigDecimal("5"));
        when(demandManageMapper.selectUncompletedDemands(PRODUCT_ID, STORE_ID))
            .thenReturn(java.util.List.of(demand));
    }

    private static DemandManage demandRow(Long id, String qty, String shipped) {
        DemandManage d = new DemandManage();
        d.setId(id);
        d.setDemandQuantity(new BigDecimal(qty));
        d.setShippedCount(new BigDecimal(shipped));
        return d;
    }

    @Test
    @DisplayName("守卫命中（affected=1）→ 正常扣减不抛")
    void incrementShippedAffectedOne_ok() {
        when(demandManageMapper.incrementShipped(eq(DEMAND_ID), anyString(), any())).thenReturn(1);

        assertThatCode(() -> service.deductDemandOnPack(PRODUCT_ID, STORE_ID, new BigDecimal("3")))
            .doesNotThrowAnyException();

        verify(demandManageMapper, times(1))
            .incrementShipped(eq(DEMAND_ID), eq("1001"), eq(new BigDecimal("3")));
    }

    @Test
    @DisplayName("并发超打：读到剩余 5 校验通过，但 DB 守卫 affected=0 → 抛 ServiceException")
    void incrementShippedAffectedZero_throws() {
        // 两工人并发：本请求读快照 remain=5、packQty=3 通过应用层校验，
        // 提交时另一请求已把剩余吃掉 → UPDATE 上界守卫不命中返 0。
        when(demandManageMapper.incrementShipped(eq(DEMAND_ID), anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.deductDemandOnPack(PRODUCT_ID, STORE_ID, new BigDecimal("3")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("并发打包");
    }

    @Test
    @DisplayName("先读校验：本次打包量超**所有未完成需求行**剩余总量 → 直接抛，一行 UPDATE 都不发")
    void preCheckExceedRemain_throwsWithoutUpdate() {
        assertThatThrownBy(() -> service.deductDemandOnPack(PRODUCT_ID, STORE_ID, new BigDecimal("6")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("剩余");

        verify(demandManageMapper, never()).incrementShipped(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("跨行扣减（V6 row27/row34）：屏幕上的剩余是多行求和，打这个总数必须能扣通，按需求日升序逐行吃")
    void deductAcrossMultipleDemandRows() {
        // 今天剩 3（10-7）、明天剩 8（8-0）→ 打包台 chip 显示 11
        DemandManage today = demandRow(50001L, "10", "7");
        DemandManage tomorrow = demandRow(50002L, "8", "0");
        when(demandManageMapper.selectUncompletedDemands(PRODUCT_ID, STORE_ID))
            .thenReturn(java.util.List.of(today, tomorrow));
        when(demandManageMapper.incrementShipped(anyLong(), anyString(), any())).thenReturn(1);

        assertThatCode(() -> service.deductDemandOnPack(PRODUCT_ID, STORE_ID, new BigDecimal("11")))
            .doesNotThrowAnyException();

        // 先把今天那条吃满 3，剩下 8 落到明天那条 —— 顺序与量都要对
        verify(demandManageMapper).incrementShipped(eq(50001L), eq("1001"), eq(new BigDecimal("3")));
        verify(demandManageMapper).incrementShipped(eq(50002L), eq("1001"), eq(new BigDecimal("8")));
    }

    @Test
    @DisplayName("跨行扣减未吃满：打 5（今天剩 3 + 明天剩 8）→ 今天 3 + 明天 2，明天那条不吃满")
    void deductPartiallyIntoSecondRow() {
        when(demandManageMapper.selectUncompletedDemands(PRODUCT_ID, STORE_ID))
            .thenReturn(java.util.List.of(demandRow(50001L, "10", "7"), demandRow(50002L, "8", "0")));
        when(demandManageMapper.incrementShipped(anyLong(), anyString(), any())).thenReturn(1);

        service.deductDemandOnPack(PRODUCT_ID, STORE_ID, new BigDecimal("5"));

        verify(demandManageMapper).incrementShipped(eq(50001L), eq("1001"), eq(new BigDecimal("3")));
        verify(demandManageMapper).incrementShipped(eq(50002L), eq("1001"), eq(new BigDecimal("2")));
    }

    @Test
    @DisplayName("超过跨行总量 → 抛且一行都不写（报的是总量，不是第一行的剩余）")
    void exceedTotalAcrossRows_throwsWithoutAnyUpdate() {
        when(demandManageMapper.selectUncompletedDemands(PRODUCT_ID, STORE_ID))
            .thenReturn(java.util.List.of(demandRow(50001L, "10", "7"), demandRow(50002L, "8", "0")));

        assertThatThrownBy(() -> service.deductDemandOnPack(PRODUCT_ID, STORE_ID, new BigDecimal("12")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("剩余 11");

        verify(demandManageMapper, never()).incrementShipped(anyLong(), anyString(), any());
    }
}
