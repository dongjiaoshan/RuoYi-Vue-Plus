package org.dromara.djs.warehouse.shipment.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.shipment.domain.Shipment;
import org.dromara.djs.warehouse.shipment.domain.bo.ShipmentCheckBo;
import org.dromara.djs.warehouse.shipment.event.ShipmentConfirmedEvent;
import org.dromara.djs.warehouse.shipment.mapper.ShipmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ShipmentServiceImpl} 单元测试（WMS-SHIP-001）。
 *
 * <p>覆盖跨表事务一致性的 3 个核心场景：</p>
 * <ol>
 *   <li>happy path：demand 状态 CONFIRMED + 2 个未清点产品 → mark + insert shipment + insert 2 flow + publishEvent</li>
 *   <li>demand 状态非法（DRAFT）→ 抛 ServiceException + 任何 INSERT 都不发生</li>
 *   <li>并发清点冲突（affected < ids.size()）→ 抛 + 不写 shipment / stock_flow / publishEvent</li>
 * </ol>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ShipmentServiceImpl 单元测试")
class ShipmentServiceImplTest {

    @Mock
    private ShipmentMapper shipmentMapper;
    @Mock
    private ProductProductionMapper productProductionMapper;
    @Mock
    private StockFlowMapper stockFlowMapper;
    @Mock
    private DemandManageMapper demandMapper;
    @Mock
    private ProductInfoMapper productInfoMapper;
    @Mock
    private LocationInfoMapper locationInfoMapper;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ShipmentServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setup() {
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(1L);

        service = new ShipmentServiceImpl(shipmentMapper, productProductionMapper, stockFlowMapper,
            demandMapper, productInfoMapper, locationInfoMapper, bizCodeGenerator, eventPublisher);

        // 业务码 stub
        when(bizCodeGenerator.generate(eq(BizCodeType.SHIP_NO), anyMap())).thenReturn("S260610TEST0001");
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap()))
            .thenReturn("F260610OT0001", "F260610OT0002");
    }

    @AfterEach
    void cleanup() {
        if (loginHelperMock != null) {
            loginHelperMock.close();
        }
    }

    // ============== happy path ==============

    @Test
    @DisplayName("happy: demand CONFIRMED + 2 production 未清点 → mark + insert shipment + 2 flow + publishEvent")
    void confirmCheck_happyPath() {
        Long demandId = 100L;
        Long storeId = 9L;
        DemandManage demand = newDemand(demandId, storeId, DemandStatus.CONFIRMED);
        when(demandMapper.selectById(demandId)).thenReturn(demand);

        List<Long> productionIds = List.of(11L, 12L);
        List<ProductProduction> productions = List.of(
            newProduction(11L, demandId, "PROD-001", new BigDecimal("3.000")),
            newProduction(12L, demandId, "PROD-002", new BigDecimal("4.500"))
        );
        when(productProductionMapper.selectList(any())).thenReturn(productions);
        when(productProductionMapper.markDeliveryChecked(any(), any())).thenReturn(2);

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(demandId);
        bo.setProductionIds(productionIds);
        bo.setTotalQuantity(new BigDecimal("7.500"));
        bo.setShipUnit("kg");
        bo.setDeliverType(1);

        Long shipmentId = service.confirmCheck(bo);

        // 1. UPDATE markDeliveryChecked 调过 1 次（批量）
        verify(productProductionMapper, times(1)).markDeliveryChecked(eq(productionIds), any());
        // 2. INSERT shipment 1 次
        ArgumentCaptor<Shipment> shipmentCap = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentMapper, times(1)).insert(shipmentCap.capture());
        Shipment captured = shipmentCap.getValue();
        assertThat(captured.getShipmentNo()).isEqualTo("S260610TEST0001");
        assertThat(captured.getStoreId()).isEqualTo(storeId);
        assertThat(captured.getShipmentStatus()).isEqualTo("shipped");
        assertThat(captured.getCheckerId()).isEqualTo(1L);
        // 3. INSERT stock_flow 2 次（按 production 逐条）
        verify(stockFlowMapper, times(2)).insert(any(StockFlow.class));
        // 4. publishEvent 1 次
        ArgumentCaptor<ShipmentConfirmedEvent> eventCap =
            ArgumentCaptor.forClass(ShipmentConfirmedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCap.capture());
        assertThat(eventCap.getValue().getDemandId()).isEqualTo(demandId);
        assertThat(eventCap.getValue().getShippedQuantity()).isEqualByComparingTo("7.500");
    }

    // ============== demand 状态非法 ==============

    @Test
    @DisplayName("error: demand DRAFT → 抛 + 不写任何表")
    void confirmCheck_demandDraft_rejected() {
        DemandManage demand = newDemand(100L, 9L, DemandStatus.DRAFT);
        when(demandMapper.selectById(100L)).thenReturn(demand);

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(100L);
        bo.setProductionIds(List.of(11L));
        bo.setTotalQuantity(new BigDecimal("3.0"));
        bo.setShipUnit("kg");
        bo.setDeliverType(1);

        assertThatThrownBy(() -> service.confirmCheck(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("DRAFT");

        verify(productProductionMapper, never()).markDeliveryChecked(anyList(), any());
        verify(shipmentMapper, never()).insert(any(Shipment.class));
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(eventPublisher, never()).publishEvent(any(ShipmentConfirmedEvent.class));
    }

    // ============== 并发清点冲突 ==============

    @Test
    @DisplayName("error: markDeliveryChecked affected < ids.size → 抛并发冲突 + 不写 shipment / flow / event")
    void confirmCheck_concurrentConflict() {
        Long demandId = 100L;
        DemandManage demand = newDemand(demandId, 9L, DemandStatus.IN_PRODUCTION);
        when(demandMapper.selectById(demandId)).thenReturn(demand);

        List<Long> productionIds = List.of(11L, 12L);
        when(productProductionMapper.selectList(any())).thenReturn(List.of(
            newProduction(11L, demandId, "PROD-001", new BigDecimal("3.0")),
            newProduction(12L, demandId, "PROD-002", new BigDecimal("4.0"))
        ));
        // 模拟并发：只有 1 行被乐观锁更新成功
        when(productProductionMapper.markDeliveryChecked(any(), any())).thenReturn(1);

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(demandId);
        bo.setProductionIds(productionIds);
        bo.setTotalQuantity(new BigDecimal("7.0"));
        bo.setShipUnit("kg");
        bo.setDeliverType(1);

        assertThatThrownBy(() -> service.confirmCheck(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("concurrent_conflict");

        verify(shipmentMapper, never()).insert(any(Shipment.class));
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(eventPublisher, never()).publishEvent(any(ShipmentConfirmedEvent.class));
    }

    // ---------- helpers ----------

    private DemandManage newDemand(Long id, Long storeId, DemandStatus status) {
        DemandManage d = new DemandManage();
        d.setId(id);
        d.setStoreId(storeId);
        d.setProductType("white_bar");
        d.setDemandStatus(status.name());
        return d;
    }

    private ProductProduction newProduction(Long id, Long demandId, String produceNo, BigDecimal qty) {
        ProductProduction p = new ProductProduction();
        p.setId(id);
        p.setDemandId(demandId);
        p.setProduceNo(produceNo);
        p.setProduceQuantity(qty);
        p.setIsDeliveryCheck(0);
        return p;
    }
}
