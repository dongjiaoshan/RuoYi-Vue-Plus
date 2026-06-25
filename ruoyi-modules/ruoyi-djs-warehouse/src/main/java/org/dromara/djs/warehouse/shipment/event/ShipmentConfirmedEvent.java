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
 *       否则 → 触发 {@code transition(PARTIAL_SHIP)}（从 CONFIRMED 直接进入，无排产环节）</li>
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

    /**
     * 本次发货「份数」= 清点成品件数（每件打包=1 份；CROSS-FLOW-003 累加 demand.shipped_count）。
     * 量纲必须是份（与 demand_quantity 一致），不是 kg —— 否则需求量算碎值且状态机误判完成。
     */
    private final BigDecimal shippedQuantity;

    /**
     * 发货确认人 user_id（CROSS-FLOW-003 用于状态推进的审计 operator）。
     *
     * <p>AFTER_COMMIT 监听线程无 sa-token 上下文，{@code IDemandStatusService.transition} 的
     * {@code resolveOperator(null)} 会取不到 {@code LoginHelper.getUserId()} 抛
     * {@code demand.operator.required}。故由发布点（{@code ShipmentServiceImpl#confirmCheck}）
     * 显式带上 checkerId，listener 透传给 transition 做审计 operator。</p>
     */
    private final Long operatorId;

    public ShipmentConfirmedEvent(Object source, Long shipmentId, Long demandId,
                                  BigDecimal shippedQuantity, Long operatorId) {
        super(source);
        this.shipmentId = shipmentId;
        this.demandId = demandId;
        this.shippedQuantity = shippedQuantity;
        this.operatorId = operatorId;
    }
}
