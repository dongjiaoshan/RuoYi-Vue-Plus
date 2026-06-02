package org.dromara.djs.warehouse.trace.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.shipment.event.ShipmentConfirmedEvent;
import org.dromara.djs.warehouse.trace.domain.TraceContentConst;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 发货追溯事件 listener（TRC-CORE-001）：消费 {@link ShipmentConfirmedEvent} → 给本次发货 demand 下
 * 已清点产品写 {@code ship} 追溯事件。
 *
 * <h3>事件 phase</h3>
 * <p>{@link TransactionPhase#AFTER_COMMIT} —— 仅发货事务 commit 成功后触发，追溯写在独立流程。
 * 追溯写失败**不回滚**已提交的发货事务（{@link ITraceService#recordEvent} 内部 try-catch 容错 +
 * 本 listener 整体 try-catch swallow，对齐 {@code PigMarketingEventListener} 范式）。</p>
 *
 * <h3>produce_code 反查</h3>
 * <p>{@link ShipmentConfirmedEvent} 只携带 shipmentId / demandId，不带 productionIds。按 {@code demand_id}
 * 查该需求下 {@code is_delivery_check=1}（已清点发货）且 {@code trace_code} 非空的 production，逐条 recordEvent(ship)。
 * V1 容错：trace_code 为空（如猪肉链当前无生成入口）的 production 跳过。</p>
 *
 * @author djs
 * @since TRC-CORE-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipTraceEventListener {

    private final ProductProductionMapper productProductionMapper;

    private final ITraceService traceService;

    /**
     * 监听发货确认事件 → 给已清点产品写 ship 追溯事件。
     *
     * @param event 发货确认事件（含 shipmentId / demandId / shippedQuantity）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipmentConfirmed(ShipmentConfirmedEvent event) {
        try {
            List<ProductProduction> productions = productProductionMapper.selectList(
                new LambdaQueryWrapper<ProductProduction>()
                    .eq(ProductProduction::getDemandId, event.getDemandId())
                    .eq(ProductProduction::getIsDeliveryCheck, 1)
                    .isNotNull(ProductProduction::getTraceCode));
            int recorded = 0;
            for (ProductProduction p : productions) {
                if (StringUtils.isNotBlank(p.getTraceCode())) {
                    traceService.recordEvent(p.getTraceCode(), TraceContentConst.SHIP);
                    recorded++;
                }
            }
            log.info("[TRC-CORE-001] ship trace events recorded shipmentId={} demandId={} count={}",
                event.getShipmentId(), event.getDemandId(), recorded);
        } catch (Exception e) {
            // AFTER_COMMIT 阶段抛异常无意义（发货事务已提交）→ swallow + log
            log.warn("[TRC-CORE-001] ship trace event failed (skipped) shipmentId={} demandId={}: {}",
                event.getShipmentId(), event.getDemandId(), e.getMessage());
        }
    }
}
