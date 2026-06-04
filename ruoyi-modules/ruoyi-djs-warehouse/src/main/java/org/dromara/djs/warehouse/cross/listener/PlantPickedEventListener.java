package org.dromara.djs.warehouse.cross.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.plant.pick.event.PlantPickedEvent;
import org.dromara.djs.plant.pick.event.PlantPickedPayload;
import org.dromara.djs.warehouse.veg.domain.PlantingRecord;
import org.dromara.djs.warehouse.veg.mapper.PlantingRecordMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

/**
 * 跨域事件 listener：PLT-PICK-001 采摘完成 → 自动创建毛菜处理待办（CROSS-FLOW-002）。
 *
 * <p>消费 {@link PlantPickedEvent}（{@code AFTER_COMMIT} 阶段），同步向
 * {@code t_warehouse_planting_record} INSERT 一行 {@code handle_status='pending'} 待办，
 * 让毛菜处理小程序待处理列表（WMS-VEG-001 {@code selectPendingList}）自动出现该批次。
 * V1 阶段一次采摘完成对应 1 行（doc/10 §2.F-WMS-02 + doc/11 §2.14 业务规则）。</p>
 *
 * <h3>事件 phase</h3>
 * <p>{@link TransactionPhase#AFTER_COMMIT} —— 仅采摘事务（{@code updateById(detail)} + 农事记录
 * INSERT）commit 成功后才触发，仓库 INSERT 在独立事务。若用默认 {@code @EventListener}
 * 则采摘回滚时 {@code planting_record} 会残留死数据。</p>
 *
 * <h3>不反向依赖</h3>
 * <p>listener 只读 {@link PlantPickedPayload} 载荷字段（冗余名称由 plant 域发 event 前已组装），
 * <b>绝不</b>调用 plant 模块的 Service / Mapper（避免 warehouse→plant→warehouse 依赖环）。</p>
 *
 * <h3>失败策略</h3>
 * <p>AFTER_COMMIT 阶段异常无法回滚已提交的采摘事务，全 try-catch 仅 {@code log.error}（带
 * plotId / cropId / 异常）。V2 加重试队列（doc/06 主要风险段）。</p>
 *
 * <h3>字段映射</h3>
 * <ul>
 *   <li>{@code harvestWeight} 取采摘累计权威值（{@code PlantDetails.actualYield}）</li>
 *   <li>{@code handle_status='pending'}（字典 {@code djs_veg_handle_status}，WMS-VEG-001 已 seed）</li>
 *   <li>{@code is_loss=2}（否，doc/11 §2.14）</li>
 *   <li>{@code data_date=NOW}（NOT NULL）</li>
 *   <li>{@code product_id} 不 set → NULL（D9 #18 现状，plant 域无 crop→product 映射，V1 留 NULL）</li>
 *   <li>{@code tenant_id} + 6 审计字段由 MybatisPlusInjector 自动 fill；{@code del_unique=0} 由 DDL DEFAULT；{@code id} AUTO_INCREMENT</li>
 * </ul>
 *
 * @author djs
 * @since CROSS-FLOW-002
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlantPickedEventListener {

    /** planting_record.handle_status 初始态：待处理（字典 {@code djs_veg_handle_status}）。 */
    private static final String HANDLE_STATUS_PENDING = "pending";

    /** planting_record.is_loss=2 否（doc/11 §2.14；自动流转的采摘批次默认非损失）。 */
    private static final Integer IS_LOSS_NO = 2;

    private final PlantingRecordMapper plantingRecordMapper;

    /**
     * 监听采摘完成事件 → INSERT planting_record（handle_status='pending'）。
     *
     * @param event 采摘完成事件，载荷 {@link PlantPickedPayload}（plant 域已组装全部冗余字段）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlantPicked(PlantPickedEvent event) {
        PlantPickedPayload p = event.getPayload();
        try {
            PlantingRecord record = new PlantingRecord();
            record.setPlotId(p.getPlotId());
            record.setCropId(p.getCropId());
            record.setPlotName(p.getPlotName());
            record.setCropName(p.getCropName());
            record.setPlantDate(toSqlDate(p.getPlantDate()));
            record.setHarvestDate(toSqlDate(p.getHarvestDate()));
            record.setHarvestWeight(p.getHarvestWeight());
            record.setTeamId(p.getTeamId());
            record.setTeamName(p.getTeamName());
            record.setDataDate(new java.util.Date());
            record.setIsLoss(IS_LOSS_NO);
            record.setHandleStatus(HANDLE_STATUS_PENDING);
            // product_id 不 set → NULL（D9 #18 现状，无 crop→product 映射）
            plantingRecordMapper.insert(record);
            log.info("[CROSS-FLOW-002] planting_record 创建成功 id={} plotId={} cropId={} harvestWeight={}",
                record.getId(), p.getPlotId(), p.getCropId(), p.getHarvestWeight());
        } catch (Exception e) {
            // AFTER_COMMIT 抛异常无法回滚已提交的采摘事务 → 全 swallow + log.error（V2 加重试队列）
            log.error("[CROSS-FLOW-002] 自动创建 planting_record 失败 plotId={} cropId={}: {}",
                p.getPlotId(), p.getCropId(), e.getMessage(), e);
        }
    }

    /**
     * {@link LocalDate} → {@link java.sql.Date} 转换（null 安全）。
     *
     * <p>{@code PlantingRecord.plantDate / harvestDate} 是 {@code java.sql.Date}，载荷侧是
     * {@code LocalDate}，需显式转换。载荷字段可为 null（如未填实际种植开始日期）。</p>
     *
     * @param ld 源 {@code LocalDate}，可为 null
     * @return {@code java.sql.Date}，入参 null 时返 null
     */
    private java.sql.Date toSqlDate(LocalDate ld) {
        if (ld == null) {
            return null;
        }
        return java.sql.Date.valueOf(ld);
    }
}
