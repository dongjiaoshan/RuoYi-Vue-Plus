package org.dromara.djs.warehouse.demand.core.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

import static org.dromara.djs.warehouse.demand.core.enums.DemandEvent.CANCEL;
import static org.dromara.djs.warehouse.demand.core.enums.DemandEvent.COMPLETE;
import static org.dromara.djs.warehouse.demand.core.enums.DemandEvent.CONFIRM;
import static org.dromara.djs.warehouse.demand.core.enums.DemandEvent.PARTIAL_SHIP;
import static org.dromara.djs.warehouse.demand.core.enums.DemandEvent.START_PRODUCTION;
import static org.dromara.djs.warehouse.demand.core.enums.DemandEvent.SUBMIT;
import static org.dromara.djs.warehouse.demand.core.enums.DemandStatus.CANCELLED;
import static org.dromara.djs.warehouse.demand.core.enums.DemandStatus.COMPLETED;
import static org.dromara.djs.warehouse.demand.core.enums.DemandStatus.CONFIRMED;
import static org.dromara.djs.warehouse.demand.core.enums.DemandStatus.DRAFT;
import static org.dromara.djs.warehouse.demand.core.enums.DemandStatus.IN_PRODUCTION;
import static org.dromara.djs.warehouse.demand.core.enums.DemandStatus.PARTIAL_SHIPPED;
import static org.dromara.djs.warehouse.demand.core.enums.DemandStatus.SUBMITTED;

/**
 * 需求单状态机（WMS-DEMAND-001 业务心脏）。
 *
 * <p>手写 FSM，不引 spring-statemachine / Flowable。{@link #nextStatus} 为纯函数，
 * 不访问 DB，方便单测覆盖 7×6 转移矩阵（参 BRD-CORE-001 {@code PigStateMachine} 范式）。</p>
 *
 * <p>4 业态（white_bar/vegetable/gift_box/other）共享同一套状态机；业态差异由
 * {@link DemandStatusService} 在 transition 入口按 {@code product_type} 做业务前置校验
 * （白条 CONFIRM 必须已指定猪只）。</p>
 *
 * <p>错误信息走 i18n，key 在 {@code ruoyi-admin/.../i18n/messages_zh_CN.properties} +
 * {@code messages_en_US.properties}。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Component
public class DemandStateMachine {

    /** (from, event) → to 简单转移表。 */
    private static final Map<TransitionKey, DemandStatus> SIMPLE = Map.ofEntries(
        // 提交
        Map.entry(new TransitionKey(DRAFT, SUBMIT), SUBMITTED),
        // 确认
        Map.entry(new TransitionKey(SUBMITTED, CONFIRM), CONFIRMED),
        // 排产
        Map.entry(new TransitionKey(CONFIRMED, START_PRODUCTION), IN_PRODUCTION),
        // 部分发货（CROSS-FLOW-003 listener 调）
        Map.entry(new TransitionKey(IN_PRODUCTION, PARTIAL_SHIP), PARTIAL_SHIPPED),
        // 继续部分发货（仍未达到 demand_quantity）
        Map.entry(new TransitionKey(PARTIAL_SHIPPED, PARTIAL_SHIP), PARTIAL_SHIPPED),
        // 完成
        Map.entry(new TransitionKey(IN_PRODUCTION, COMPLETE), COMPLETED),
        Map.entry(new TransitionKey(PARTIAL_SHIPPED, COMPLETE), COMPLETED),
        // 取消（任意非终态非排产后状态均可取消）
        Map.entry(new TransitionKey(DRAFT, CANCEL), CANCELLED),
        Map.entry(new TransitionKey(SUBMITTED, CANCEL), CANCELLED),
        Map.entry(new TransitionKey(CONFIRMED, CANCEL), CANCELLED)
        // IN_PRODUCTION / PARTIAL_SHIPPED 之后不允许取消，要走退货流程（STR-RETURN-001 / WMS-SHIP-001）
    );

    /**
     * 计算事件触发后的目标状态（纯函数，不访问 DB）。
     *
     * @param from  当前状态（不可为 null）
     * @param event 触发事件（不可为 null）
     * @return 目标状态
     * @throws ServiceException 终态推进 / 非法转移
     * @throws NullPointerException from 或 event 为 null
     */
    public DemandStatus nextStatus(DemandStatus from, DemandEvent event) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(event, "event must not be null");

        if (from.isTerminal()) {
            throw new ServiceException(I18nMessages.t("demand.state.terminal", from.getLabel()), 400);
        }

        DemandStatus to = SIMPLE.get(new TransitionKey(from, event));
        if (to == null) {
            throw new ServiceException(
                I18nMessages.t("demand.state.illegal_transition", from.getLabel(), event.getLabel()), 400);
        }
        return to;
    }

    /** transition 查表 key。 */
    public record TransitionKey(DemandStatus from, DemandEvent event) {
    }
}
