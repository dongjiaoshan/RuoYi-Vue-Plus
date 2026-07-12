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
        when(demandManageMapper.selectOldestUncompletedDemand(PRODUCT_ID, STORE_ID)).thenReturn(demand);
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
    @DisplayName("先读校验：本次打包量超剩余份数 → 直接抛，不发 UPDATE")
    void preCheckExceedRemain_throwsWithoutUpdate() {
        assertThatThrownBy(() -> service.deductDemandOnPack(PRODUCT_ID, STORE_ID, new BigDecimal("6")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("剩余");

        verify(demandManageMapper, never()).incrementShipped(anyLong(), anyString(), any());
    }
}
