package org.dromara.djs.warehouse.demand.core.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.util.I18nMessages;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

import static org.dromara.djs.warehouse.demand.core.enums.DemandEvent.CANCEL;
import static org.dromara.djs.warehouse.demand.core.enums.DemandEvent.COMPLETE;
import static org.dromara.djs.warehouse.demand.core.enums.DemandEvent.CONFIRM;
import static org.dromara.djs.warehouse.demand.core.enums.DemandEvent.PARTIAL_SHIP;
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
 * 不访问 DB，方便单测覆盖转移矩阵（参 BRD-CORE-001 {@code PigStateMachine} 范式）。</p>
 *
 * <p>主链路：{@code SUBMITTED → CONFIRMED → (PARTIAL_SHIPPED) → COMPLETED}。起点为 {@code SUBMITTED}
 * ——V1 无「存草稿」录入入口，需求一经录入即提交，故 {@code DRAFT} 态在 V1 不可达；{@code DRAFT}
 * 相关转移边（{@code DRAFT→SUBMIT / DRAFT→CANCEL}）为兼容 / V2 预留，保留在转移表里不删。
 * 无「开始排产」环节——CONFIRMED 是用户侧最终态，确认后由发货链路（CROSS-FLOW-003 listener）
 * 自动推进 PARTIAL_SHIP / COMPLETE。{@code IN_PRODUCTION} 态已废弃（新流程不再产生），仅保留
 * 兼容转移供存量数据走完发货。</p>
 *
 * <p>4 业态（white_bar/vegetable/gift_box/other）共享同一套状态机。白条 CONFIRM 不再强制
 * 已指定猪只（D-FIX-24 决策 #7a）——指派猪只为 CONFIRMED 后可选后置动作。</p>
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
        // 确认（CONFIRMED 即用户最终态：无「开始排产」环节，确认后直接进发货链路）
        Map.entry(new TransitionKey(SUBMITTED, CONFIRM), CONFIRMED),
        // 部分发货（CROSS-FLOW-003 listener 调；从 CONFIRMED 直接进入，不经排产中）
        Map.entry(new TransitionKey(CONFIRMED, PARTIAL_SHIP), PARTIAL_SHIPPED),
        // 继续部分发货（仍未达到 demand_quantity）
        Map.entry(new TransitionKey(PARTIAL_SHIPPED, PARTIAL_SHIP), PARTIAL_SHIPPED),
        // 完成（CONFIRMED 一次发足即完成）
        Map.entry(new TransitionKey(CONFIRMED, COMPLETE), COMPLETED),
        Map.entry(new TransitionKey(PARTIAL_SHIPPED, COMPLETE), COMPLETED),
        // 取消（任意未发货态均可取消）
        Map.entry(new TransitionKey(DRAFT, CANCEL), CANCELLED),
        Map.entry(new TransitionKey(SUBMITTED, CANCEL), CANCELLED),
        Map.entry(new TransitionKey(CONFIRMED, CANCEL), CANCELLED),
        // 兼容存量数据：历史卡在 IN_PRODUCTION 的需求仍可正常走发货 / 完成（新流程不再产生该态）
        Map.entry(new TransitionKey(IN_PRODUCTION, PARTIAL_SHIP), PARTIAL_SHIPPED),
        Map.entry(new TransitionKey(IN_PRODUCTION, COMPLETE), COMPLETED)
        // PARTIAL_SHIPPED 之后不允许取消，要走退货流程（STR-RETURN-001 / WMS-SHIP-001）
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
