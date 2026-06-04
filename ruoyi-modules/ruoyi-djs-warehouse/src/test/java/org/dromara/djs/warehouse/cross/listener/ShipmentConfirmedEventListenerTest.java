package org.dromara.djs.warehouse.cross.listener;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.service.IDemandStatusService;
import org.dromara.djs.warehouse.shipment.event.ShipmentConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ShipmentConfirmedEventListener} 单测（CROSS-FLOW-003 / D13-14）。
 *
 * <h3>覆盖场景（5 类）</h3>
 * <ol>
 *   <li>happy 部分发货：累加返 1，重读 shipped &lt; demand + status=IN_PRODUCTION
 *       → transition(PARTIAL_SHIP) 调 1 次，operator = event.operatorId 透传</li>
 *   <li>达量完成：shipped == demand（compareTo 边界）+ status=IN_PRODUCTION
 *       → transition(COMPLETE)</li>
 *   <li>并发二次终态短路：重读 status 已 COMPLETED（isTerminal）→ transition never；累加仍发生</li>
 *   <li>transition 抛 ServiceException → listener swallow（AFTER_COMMIT 不重抛）</li>
 *   <li>incrementShipped 返 0（demand 不存在）→ 短路 return，transition never，不再 selectById</li>
 * </ol>
 *
 * <p>纯 Mockito，不需要 Spring 上下文。mock {@link DemandManageMapper} + {@link IDemandStatusService}。</p>
 *
 * @author djs
 * @since CROSS-FLOW-003
 */
@Tag("local")
@ExtendWith(MockitoExtension.class)
class ShipmentConfirmedEventListenerTest {

    private static final String TENANT_V1 = "1001";
    private static final Long SHIPMENT_ID = 9001L;
    private static final Long DEMAND_ID = 5001L;
    private static final Long OPERATOR_ID = 1003L;

    @Mock
    private DemandManageMapper demandMapper;

    @Mock
    private IDemandStatusService demandStatusService;

    private ShipmentConfirmedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ShipmentConfirmedEventListener(demandMapper, demandStatusService);
    }

    /**
     * 构造发货确认事件（shippedQuantity / operatorId 由 case 决定）。
     */
    private ShipmentConfirmedEvent buildEvent(BigDecimal shippedQty) {
        return new ShipmentConfirmedEvent(this, SHIPMENT_ID, DEMAND_ID, shippedQty, OPERATOR_ID);
    }

    /**
     * 构造重读出的 demand（累加后状态）。
     */
    private DemandManage buildDemand(String status, BigDecimal shipped, BigDecimal demand) {
        DemandManage d = new DemandManage();
        d.setId(DEMAND_ID);
        d.setDemandStatus(status);
        d.setShippedCount(shipped);
        d.setDemandQuantity(demand);
        return d;
    }

    @Test
    @DisplayName("happy 部分发货：shipped < demand + IN_PRODUCTION → transition(PARTIAL_SHIP)，operator 透传")
    void onShipmentConfirmed_partialShip_firesPartialShipTransition() {
        ShipmentConfirmedEvent event = buildEvent(new BigDecimal("3"));
        when(demandMapper.incrementShipped(eq(DEMAND_ID), eq(TENANT_V1), eq(new BigDecimal("3"))))
            .thenReturn(1);
        // 累加后重读：已发 3，需求 10 → 未达量
        when(demandMapper.selectById(DEMAND_ID))
            .thenReturn(buildDemand("IN_PRODUCTION", new BigDecimal("3"), new BigDecimal("10")));

        listener.onShipmentConfirmed(event);

        ArgumentCaptor<Long> operatorCaptor = ArgumentCaptor.forClass(Long.class);
        verify(demandStatusService, times(1)).transition(
            eq(DEMAND_ID), eq(DemandEvent.PARTIAL_SHIP), operatorCaptor.capture(), anyString());
        assertThat(operatorCaptor.getValue()).isEqualTo(OPERATOR_ID);
    }

    @Test
    @DisplayName("达量完成：shipped == demand（compareTo 边界）+ IN_PRODUCTION → transition(COMPLETE)")
    void onShipmentConfirmed_reachedQuantity_firesCompleteTransition() {
        ShipmentConfirmedEvent event = buildEvent(new BigDecimal("4"));
        when(demandMapper.incrementShipped(eq(DEMAND_ID), eq(TENANT_V1), any()))
            .thenReturn(1);
        // 累加后重读：已发 10.00，需求 10（scale 不同，compareTo == 0 仍算达量）
        when(demandMapper.selectById(DEMAND_ID))
            .thenReturn(buildDemand("IN_PRODUCTION", new BigDecimal("10.00"), new BigDecimal("10")));

        listener.onShipmentConfirmed(event);

        verify(demandStatusService, times(1)).transition(
            eq(DEMAND_ID), eq(DemandEvent.COMPLETE), eq(OPERATOR_ID), anyString());
    }

    @Test
    @DisplayName("超量也算完成：shipped > demand + PARTIAL_SHIPPED → transition(COMPLETE)")
    void onShipmentConfirmed_overShipped_firesCompleteTransition() {
        ShipmentConfirmedEvent event = buildEvent(new BigDecimal("5"));
        when(demandMapper.incrementShipped(eq(DEMAND_ID), eq(TENANT_V1), any()))
            .thenReturn(1);
        // 累加后重读：已发 12，需求 10 → 超量
        when(demandMapper.selectById(DEMAND_ID))
            .thenReturn(buildDemand("PARTIAL_SHIPPED", new BigDecimal("12"), new BigDecimal("10")));

        listener.onShipmentConfirmed(event);

        verify(demandStatusService, times(1)).transition(
            eq(DEMAND_ID), eq(DemandEvent.COMPLETE), eq(OPERATOR_ID), anyString());
    }

    @Test
    @DisplayName("并发二次终态短路：重读已 COMPLETED → transition never（累加仍发生）")
    void onShipmentConfirmed_alreadyTerminal_skipsTransition() {
        ShipmentConfirmedEvent event = buildEvent(new BigDecimal("2"));
        when(demandMapper.incrementShipped(eq(DEMAND_ID), eq(TENANT_V1), any()))
            .thenReturn(1);
        // 并发：第二个事件累加后重读已是终态
        when(demandMapper.selectById(DEMAND_ID))
            .thenReturn(buildDemand("COMPLETED", new BigDecimal("12"), new BigDecimal("10")));

        listener.onShipmentConfirmed(event);

        // 累加发生了（短路在判状态之后，累加之前已执行）
        verify(demandMapper, times(1)).incrementShipped(eq(DEMAND_ID), eq(TENANT_V1), any());
        // 但不再推进状态
        verify(demandStatusService, never()).transition(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("transition 抛 ServiceException → listener swallow，不重抛（AFTER_COMMIT 阶段）")
    void onShipmentConfirmed_transitionThrows_listenerSwallows() {
        ShipmentConfirmedEvent event = buildEvent(new BigDecimal("3"));
        when(demandMapper.incrementShipped(eq(DEMAND_ID), eq(TENANT_V1), any()))
            .thenReturn(1);
        when(demandMapper.selectById(DEMAND_ID))
            .thenReturn(buildDemand("IN_PRODUCTION", new BigDecimal("3"), new BigDecimal("10")));
        doThrow(new ServiceException("demand.state.illegal_transition"))
            .when(demandStatusService).transition(any(), any(), any(), anyString());

        // AFTER_COMMIT 阶段抛异常无意义（发货事务已提交），listener 必须 swallow
        assertThatCode(() -> listener.onShipmentConfirmed(event)).doesNotThrowAnyException();
        verify(demandStatusService, times(1)).transition(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("incrementShipped 返 0（demand 不存在）→ 短路 return，不 selectById 不 transition")
    void onShipmentConfirmed_demandNotFound_shortCircuits() {
        ShipmentConfirmedEvent event = buildEvent(new BigDecimal("3"));
        when(demandMapper.incrementShipped(eq(DEMAND_ID), eq(TENANT_V1), any()))
            .thenReturn(0);
        // 不 stub selectById：本 case 就是验它 never 被调

        listener.onShipmentConfirmed(event);

        verify(demandMapper, never()).selectById(any());
        verify(demandStatusService, never()).transition(any(), any(), any(), anyString());
    }
}
