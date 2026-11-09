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
 *   <li>按已绑定且已出车的成品汇总实际发货量，不改写打包阶段的 {@code shipped_count}。</li>
 *   <li>实际发货量达到 {@code demand_quantity} 时推进完成，否则推进部分发货状态。</li>
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
     * 本次实际发货量，与需求单位一致：白条每半扇1，KG按实重，其他按成品的需求扣减量。
     * 不用于累加 {@code demand.shipped_count}；该字段由打包环节记录备齐量。
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
