package org.dromara.djs.breed.core.event;

import lombok.Getter;
import org.dromara.djs.breed.event.slaughter.domain.PigMarketing;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * 出栏跨域事件（BRD-EVENT-004 SLAUGHTER + CROSS-FLOW-001）。
 *
 * <p>{@code SlaughterServiceImpl} 完成 INSERT + 状态机后由
 * {@code ApplicationEventPublisher#publishEvent} 同步发布。下游 CROSS-FLOW-001
 * （燎毛接收）会监听本 event；V1 阶段仅 publish，消费者未实现（已 raise 到 _open-issues）。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Getter
public class PigMarketingEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 出栏记录（同事务内 INSERT 完成，含 id / pigId / earNo / marketingDate / outDest 等）。 */
    private final PigMarketing marketing;

    public PigMarketingEvent(Object source, PigMarketing marketing) {
        super(source);
        this.marketing = marketing;
    }
}
