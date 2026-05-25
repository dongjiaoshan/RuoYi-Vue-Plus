package org.dromara.djs.breed.event.farrow.event;

import lombok.Getter;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * 分娩事件 Spring event（BRD-EVENT-002 → BRD-EVENT-003 解耦）。
 *
 * <p>{@code FarrowServiceImpl} 在事务内 publish 本事件，
 * {@code PigEarTagServiceImpl}（或上游 listener）通过 {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 异步消费，做"分娩后提醒贴仔猪耳标"。</p>
 *
 * <p>本事件不直接触发耳标 INSERT — 仔猪耳号由用户在 mp 端手工录入数量 + 性别后再调
 * {@code IPigEarTagService.batchTag}。本 event 只起"通知 / 索引"作用，不入业务关键路径。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Getter
public class PigFarrowEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 刚 INSERT 的分娩记录（含 id / pigId / earNo / liveBorn / breedingId / parity / farrowDate）。 */
    private final PigFarrow farrow;

    public PigFarrowEvent(Object source, PigFarrow farrow) {
        super(source);
        this.farrow = farrow;
    }
}
