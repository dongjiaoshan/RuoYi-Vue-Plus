package org.dromara.djs.warehouse.shipment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 发货确认事件（WMS-SHIP-001 发布 / CROSS-FLOW-003 D14 listener 消费）。
 *
 * <p>发布场景：{@code ShipmentServiceImpl#confirmCheck} 完成 3 表事务后
 * {@code AFTER_COMMIT} 触发（{@code ApplicationEventPublisher#publishEvent}）。</p>
 *
 * <p>D14 listener 应：</p>
 * <ol>
 *   <li>UPDATE {@code t_warehouse_demand_manage SET shipped_count = shipped_count + shippedQuantity}</li>
 *   <li>判断 {@code shipped_count >= demand_quantity} → 触发 {@code IDemandStatusService.transition(COMPLETE)}；
 *       否则若 status='IN_PRODUCTION' → 触发 {@code transition(PARTIAL_SHIP)}</li>
 *   <li>INSERT {@code t_trace_event}（warehouse_ship 节点）</li>
 * </ol>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Getter
public class ShipmentConfirmedEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发货记录 ID（snowflake）。 */
    private final Long shipmentId;

    /** 关联需求 ID。 */
    private final Long demandId;

    /** 本次发货数量（CROSS-FLOW-003 用于累加 demand.shipped_count）。 */
    private final BigDecimal shippedQuantity;

    public ShipmentConfirmedEvent(Object source, Long shipmentId, Long demandId, BigDecimal shippedQuantity) {
        super(source);
        this.shipmentId = shipmentId;
        this.demandId = demandId;
        this.shippedQuantity = shippedQuantity;
    }
}
