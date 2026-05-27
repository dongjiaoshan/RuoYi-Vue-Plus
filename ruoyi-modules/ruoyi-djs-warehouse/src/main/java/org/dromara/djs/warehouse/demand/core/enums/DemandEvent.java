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

    /** 确认：SUBMITTED → CONFIRMED；白条业态校验已指定猪只。 */
    CONFIRM("确认"),

    /** 开始排产：CONFIRMED → IN_PRODUCTION。 */
    START_PRODUCTION("开始排产"),

    /** 部分发货：IN_PRODUCTION → PARTIAL_SHIPPED（CROSS-FLOW-003 listener 触发）。 */
    PARTIAL_SHIP("部分发货"),

    /** 完成：IN_PRODUCTION / PARTIAL_SHIPPED → COMPLETED（CROSS-FLOW-003 listener 触发）。 */
    COMPLETE("完成"),

    /** 取消：DRAFT / SUBMITTED / CONFIRMED → CANCELLED；IN_PRODUCTION 之后禁止取消。 */
    CANCEL("取消");

    private final String label;

    DemandEvent(String label) {
        this.label = label;
    }
}
