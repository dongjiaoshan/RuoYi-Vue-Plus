package org.dromara.djs.warehouse.cross.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.service.IDemandStatusService;
import org.dromara.djs.warehouse.shipment.event.ShipmentConfirmedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * 发货确认 → 需求状态机反向更新 listener（CROSS-FLOW-003）。
 *
 * <p>消费 {@link ShipmentConfirmedEvent}（WMS-SHIP-001 发布），完成两件事：</p>
 * <ol>
 *   <li>原子累加 {@code t_warehouse_demand_manage.shipped_count += shippedQuantity}</li>
 *   <li>按累计量推进需求状态：{@code shipped >= demand} → {@link DemandEvent#COMPLETE}；
 *       否则 → {@link DemandEvent#PARTIAL_SHIP}（合法性交给 {@code DemandStateMachine}）</li>
 * </ol>
 *
 * <h3>事件 phase（全 sprint 最硬约束）</h3>
 * <p>{@link TransactionPhase#AFTER_COMMIT} —— WMS-SHIP-001 在 {@code @Transactional} 内同步
 * {@code publishEvent}；若用默认 {@code @EventListener}（提交前消费），本 listener 抛异常会
 * <b>回滚发货主事务</b>，违反「发货完成事实不可回滚」。必须等发货 3 表事务 commit 后才反向更新。</p>
 *
 * <h3>幂等性（doc/06 主要风险）</h3>
 * <ul>
 *   <li>累加走 DB 端原子 {@code SET shipped_count = COALESCE(...) + ?}（{@link DemandManageMapper#incrementShipped}），
 *       并发双发货不丢更新（不 select+set）。</li>
 *   <li>累加后<b>重读</b>最新状态：终态（COMPLETED/CANCELLED）直接 return —— 并发第二个事件不再 fire
 *       transition，避免重复 COMPLETE 撞终态抛异常。</li>
 *   <li>状态推进委托 {@link IDemandStatusService#transition}，其内部 Redisson 锁
 *       {@code djs:demand:transition:<id>} 串行化同一需求的状态推进 + audit_history append。</li>
 * </ul>
 *
 * <h3>operator（AFTER_COMMIT 无 sa-token 上下文）</h3>
 * <p>监听线程脱离请求上下文，{@code resolveOperator(null)} 取不到 {@code LoginHelper.getUserId()}
 * 会抛 {@code demand.operator.required} → 状态推进静默失败（shipped_count 累加了但状态没推进，
 * 产生「幽灵未完成」）。故 {@link ShipmentConfirmedEvent#getOperatorId()} 由发布点带上发货确认人
 * （checkerId），本 listener 显式透传给 transition。</p>
 *
 * <h3>失败策略</h3>
 * <p>AFTER_COMMIT 阶段异常无意义（发货事务已提交，无法回滚发货事实），全方法体 try-catch 仅
 * {@code log.error}，<b>不重抛</b>（重抛只会污染日志且无补偿效果）。V2 加重试队列
 * （doc/06 主要风险段）。与 {@code PigMarketingEventListener} / {@code ShipTraceEventListener} 范式一致。</p>
 *
 * @author djs
 * @since CROSS-FLOW-003 (D13-14)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentConfirmedEventListener {

    /**
     * V1 单租户常量（未启全局 MP 多租户拦截器；原生 {@code @Update} 需显式带 tenant_id）。
     */
    private static final String TENANT_V1 = "1001";

    private final DemandManageMapper demandMapper;

    private final IDemandStatusService demandStatusService;

    /**
     * 监听发货确认事件 → 累加 shipped_count + 推进需求状态机。
     *
     * @param event 发货确认事件（含 shipmentId / demandId / shippedQuantity / operatorId）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipmentConfirmed(ShipmentConfirmedEvent event) {
        try {
            // 1. 原子累加 shipped_count（DB 端 +=，并发安全；不 select+set）
            int rows = demandMapper.incrementShipped(
                event.getDemandId(), TENANT_V1, event.getShippedQuantity());
            if (rows == 0) {
                log.warn("[CROSS-FLOW-003] demand 不存在或已删，跳过状态推进 shipmentId={} demandId={}",
                    event.getShipmentId(), event.getDemandId());
                return;
            }

            // 2. 累加后重读最新状态（拿到累加后真实值，避免用旧值判定）
            DemandManage demand = demandMapper.selectById(event.getDemandId());
            if (demand == null) {
                log.warn("[CROSS-FLOW-003] 累加后重读 demand 为 null，跳过推进 demandId={}", event.getDemandId());
                return;
            }

            // 3. 幂等短路：未知状态 / 已终态直接 return（防并发双 COMPLETE 撞终态）
            DemandStatus current = DemandStatus.fromCodeSafe(demand.getDemandStatus());
            if (current == null) {
                log.warn("[CROSS-FLOW-003] 未知 demand_status='{}' demandId={}，跳过推进",
                    demand.getDemandStatus(), demand.getId());
                return;
            }
            if (current.isTerminal()) {
                log.info("[CROSS-FLOW-003] demandId={} 已终态 {}，shipped_count 已累加，不再推进状态",
                    demand.getId(), current);
                return;
            }

            // 4. 判定目标事件（BigDecimal 用 compareTo，绝不用 == / equals）
            BigDecimal shipped = demand.getShippedCount() == null ? BigDecimal.ZERO : demand.getShippedCount();
            BigDecimal need = demand.getDemandQuantity() == null ? BigDecimal.ZERO : demand.getDemandQuantity();
            DemandEvent targetEvent = (need.signum() > 0 && shipped.compareTo(need) >= 0)
                ? DemandEvent.COMPLETE       // 累计达量 → 完成
                : DemandEvent.PARTIAL_SHIP;  // 未达量 → 部分发货

            // 5. 推进状态（走 IDemandStatusService：内部 Redisson 锁 + 状态机校验 + audit_history append）
            demandStatusService.transition(
                demand.getId(),
                targetEvent,
                event.getOperatorId(),
                String.format("CROSS-FLOW-003 发货自动推进 shipmentId=%s shippedQty=%s",
                    event.getShipmentId(), event.getShippedQuantity()));

            log.info("[CROSS-FLOW-003] 发货反向更新成功 demandId={} shipped={} need={} event={}",
                demand.getId(), shipped, need, targetEvent);
        } catch (Exception e) {
            // AFTER_COMMIT 阶段：发货主事务已提交，回滚无意义 + 异常会让上游误判发货失败 → swallow + log
            log.error("[CROSS-FLOW-003] 发货反向更新失败 shipmentId={} demandId={}: {}",
                event.getShipmentId(), event.getDemandId(), e.getMessage(), e);
        }
    }
}
