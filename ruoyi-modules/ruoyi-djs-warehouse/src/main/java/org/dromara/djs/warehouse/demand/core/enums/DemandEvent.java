package org.dromara.djs.warehouse.demand.core.enums;

import lombok.Getter;

/**
 * 需求单状态机事件（WMS-DEMAND-001）。
 *
 * <p>本枚举仅作为 {@link DemandStatus} 转移触发器；语义见 {@code DemandStateMachine#SIMPLE}。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Getter
public enum DemandEvent {

    /** 提交：DRAFT → SUBMITTED。 */
    SUBMIT("提交"),

    /** 确认：SUBMITTED → CONFIRMED（用户侧最终态，无后续排产环节）。 */
    CONFIRM("确认"),

    /** 部分发货：CONFIRMED / PARTIAL_SHIPPED → PARTIAL_SHIPPED（CROSS-FLOW-003 listener 触发）。 */
    PARTIAL_SHIP("部分发货"),

    /** 完成：CONFIRMED / PARTIAL_SHIPPED → COMPLETED（CROSS-FLOW-003 listener 触发）。 */
    COMPLETE("完成"),

    /** 取消：DRAFT / SUBMITTED / CONFIRMED → CANCELLED；IN_PRODUCTION 之后禁止取消。 */
    CANCEL("取消");

    private final String label;

    DemandEvent(String label) {
        this.label = label;
    }
}
