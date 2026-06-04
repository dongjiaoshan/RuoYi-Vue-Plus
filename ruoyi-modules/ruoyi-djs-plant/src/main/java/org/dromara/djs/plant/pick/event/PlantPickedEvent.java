package org.dromara.djs.plant.pick.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * 采摘完成跨域事件（PLT-PICK-001 + CROSS-FLOW-002）。
 *
 * <p>{@code AppletPickServiceImpl#submitPick} 在 finish=true 且本次由非 completed 流转到
 * completed 时，由 {@code ApplicationEventPublisher#publishEvent} 同步发布。消费者是
 * warehouse 域 {@code PlantPickedEventListener}（{@code AFTER_COMMIT} 阶段向
 * {@code t_warehouse_planting_record} INSERT 一行 {@code handle_status='pending'} 待办，
 * 让毛菜处理小程序待处理列表自动出现该批次）。</p>
 *
 * <p>载荷用不可变 {@link PlantPickedPayload} 而非裸 {@code PlantDetails}，避免 listener
 * 反查 plant 表造成反向依赖。</p>
 *
 * @author djs
 * @since CROSS-FLOW-002
 */
@Getter
public class PlantPickedEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 采摘完成载荷（plant 域已组装好全部冗余字段）。 */
    private final PlantPickedPayload payload;

    public PlantPickedEvent(Object source, PlantPickedPayload payload) {
        super(source);
        this.payload = payload;
    }
}
