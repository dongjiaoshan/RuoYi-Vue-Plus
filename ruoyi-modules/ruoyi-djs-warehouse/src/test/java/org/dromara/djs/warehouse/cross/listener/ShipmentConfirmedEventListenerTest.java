package org.dromara.djs.warehouse.cross.listener;

import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.service.IDemandStatusService;
import org.dromara.djs.warehouse.shipment.service.IShipmentService;
import org.dromara.djs.warehouse.shipment.event.ShipmentConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** 状态推进只看本需求真实发货事实，不重复累加生产时扣减值。 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class ShipmentConfirmedEventListenerTest {
    @Mock DemandManageMapper demandMapper;
    @Mock IDemandStatusService demandStatusService;
    @Mock IShipmentService shipmentService;
    ShipmentConfirmedEventListener listener;

    @BeforeEach void setup() {
        listener = new ShipmentConfirmedEventListener(demandMapper, demandStatusService, shipmentService);
    }
    private DemandManage demand(String state, String prepared) {
        DemandManage d = new DemandManage(); d.setId(1L); d.setDemandStatus(state);
        d.setDemandQuantity(new BigDecimal("2")); d.setShippedCount(new BigDecimal(prepared));
        return d;
    }
    private void fire() {
        listener.onShipmentConfirmed(new ShipmentConfirmedEvent(this, 9L, 1L, BigDecimal.ONE, 1003L));
    }
    @Test void firstProduceThenCreateDemandStillCompletesFromActualShipments() {
        when(demandMapper.selectById(1L)).thenReturn(demand("CONFIRMED", "0"));
        when(shipmentService.deliveredQuantity(1L)).thenReturn(new BigDecimal("2"));
        fire();
        verify(demandStatusService).transition(eq(1L), eq(DemandEvent.COMPLETE), eq(1003L), anyString());
        verify(demandMapper, never()).incrementShipped(any(), any(), any());
    }
    @Test void preparedFullButOnlyPartActuallySentRemainsPartial() {
        when(demandMapper.selectById(1L)).thenReturn(demand("IN_PRODUCTION", "2"));
        when(shipmentService.deliveredQuantity(1L)).thenReturn(BigDecimal.ONE);
        fire();
        verify(demandStatusService).transition(eq(1L), eq(DemandEvent.PARTIAL_SHIP), eq(1003L), anyString());
    }
    @Test void laterShipmentCompletesSameDemandFromCumulativeFacts() {
        when(demandMapper.selectById(1L)).thenReturn(demand("PARTIAL_SHIPPED", "1"));
        when(shipmentService.deliveredQuantity(1L)).thenReturn(new BigDecimal("2.000"));
        fire();
        verify(demandStatusService).transition(eq(1L), eq(DemandEvent.COMPLETE), eq(1003L), anyString());
    }
    @Test void completedAndCancelledAndMissingAreNoOps() {
        when(demandMapper.selectById(1L)).thenReturn(demand("COMPLETED", "2"), demand("CANCELLED", "2"), null);
        fire(); fire(); fire();
        verifyNoInteractions(shipmentService, demandStatusService);
        verify(demandMapper, never()).incrementShipped(any(), any(), any());
    }
    @Test void afterCommitTransitionFailureDoesNotReportShipmentFailure() {
        when(demandMapper.selectById(1L)).thenReturn(demand("CONFIRMED", "0"));
        when(shipmentService.deliveredQuantity(1L)).thenReturn(BigDecimal.ONE);
        doThrow(new IllegalStateException("transition conflict")).when(demandStatusService).transition(any(), any(), any(), anyString());
        assertThatCode(this::fire).doesNotThrowAnyException();
    }
}
