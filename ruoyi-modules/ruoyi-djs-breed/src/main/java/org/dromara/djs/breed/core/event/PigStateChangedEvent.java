package org.dromara.djs.breed.core.event;

import lombok.Getter;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.PigStatusRecord;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * 猪只状态变更 Spring 事件（BRD-CORE-001）。
 *
 * <p>每次状态机推进成功后由 {@code PigCoreServiceImpl#fireEvent} 同步发布；
 * 下游订阅方（dashboard / 推送 / CROSS-FLOW-001 燎毛触发）通过
 * {@code @EventListener}（搭配 {@code @TransactionalEventListener(phase=AFTER_COMMIT)}）订阅。</p>
 *
 * <p>本 ticket 不实现任何 listener，listener 由各业务 ticket（dashboard / 推送）各自挂。</p>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Getter
public class PigStateChangedEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 刚写入的 status_record（含 id / event_type / related_event_id）。 */
    private final PigStatusRecord record;

    /** 状态机更新后的 pig 快照（含 currentStatus / endReason / parity / barnId 等新值）。 */
    private final Pig pig;

    /** 推进前的 lifecycle（pig 创建态 INTRO 时为 null）。 */
    private final PigLifecycle fromState;

    /** 推进后的 lifecycle。 */
    private final PigLifecycle toState;

    public PigStateChangedEvent(Object source, PigStatusRecord record, Pig pig,
                                PigLifecycle fromState, PigLifecycle toState) {
        super(source);
        this.record = record;
        this.pig = pig;
        this.fromState = fromState;
        this.toState = toState;
    }
}
