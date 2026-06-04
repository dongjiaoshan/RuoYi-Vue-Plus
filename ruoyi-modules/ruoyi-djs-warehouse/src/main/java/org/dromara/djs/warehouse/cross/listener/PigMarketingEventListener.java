package org.dromara.djs.warehouse.cross.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.breed.core.event.PigMarketingEvent;
import org.dromara.djs.breed.event.slaughter.domain.PigMarketing;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceContentConst;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

/**
 * 跨域事件 listener：BRD-EVENT-004 出栏 → 自动创建白条入库待办（CROSS-FLOW-001）。
 *
 * <p>消费 {@link PigMarketingEvent}（{@code AFTER_COMMIT} 阶段），同步向
 * {@code t_warehouse_bar_info} INSERT 一行白条记录，{@code status='pending_singe'}。
 * V1 阶段一头猪 1 行（doc/06 §F-WMS-01 + doc/11 §2.8 业务规则）：燎毛入库时才会按
 * 半扇 / 猪头 / 猪蹄 衍生 N 行 {@code product_inhouse} 进冻品库。</p>
 *
 * <h3>事件 phase</h3>
 * <p>{@link TransactionPhase#AFTER_COMMIT} —— 仅养殖事务 commit 成功后才触发，
 * 仓库 INSERT 在独立事务。若使用默认 {@code @EventListener}（BEFORE_COMMIT）则养殖
 * 事务回滚时 bar_info 会残留死数据。</p>
 *
 * <h3>跨模块 entity 引用</h3>
 * <p>listener 拿到 {@link PigMarketing} 后**只读必需字段**（{@code earNo / marketingDate / outWeight}），
 * **不调用** breed 模块的 service 方法（避免反向依赖循环）。</p>
 *
 * <h3>失败策略</h3>
 * <p>AFTER_COMMIT 阶段异常无意义（养殖事务已提交），全 try-catch 仅 {@code log.error}。
 * V2 加重试队列（doc/06 主要风险段）。</p>
 *
 * <h3>跨模块字段映射</h3>
 * <ul>
 *   <li>{@link PigMarketing#getEarNo()} → {@link BarInfo#setEarNo}</li>
 *   <li>{@link PigMarketing#getMarketingDate()}（{@code LocalDateTime}）→ {@link BarInfo#setMarketingTime}（{@code java.util.Date}）</li>
 *   <li>{@link PigMarketing#getOutWeight()} → {@link BarInfo#setMarketingWeight}</li>
 *   <li>{@code bar_id} 由 {@link IBizCodeGenerator#generate} 用 {@link BizCodeType#BAR_NO} 规则生成
 *       （Redisson 锁 + 序号表 UNIQUE 双保护，与 BURN_NO / CUT_NO 范式一致）</li>
 *   <li>{@code in_method=1}（燎毛间，字典 {@code djs_bar_in_method}）</li>
 *   <li>{@code status='pending_singe'}（待燎毛，字典 {@code djs_bar_status}）</li>
 * </ul>
 *
 * @author djs
 * @since CROSS-FLOW-001 (D10)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PigMarketingEventListener {

    /**
     * bar_info.status 初始态：待燎毛（字典 {@code djs_bar_status}）。
     */
    private static final String BAR_STATUS_PENDING_SINGE = "pending_singe";

    /**
     * bar_info.in_method=1 燎毛间（字典 {@code djs_bar_in_method}；后续工序入库走燎毛流程）。
     */
    private static final Integer IN_METHOD_SINGE_ROOM = 1;

    private final BarInfoMapper barInfoMapper;

    private final IBizCodeGenerator bizCodeGenerator;

    private final ITraceService traceService;

    /**
     * 监听出栏事件 → INSERT bar_info（status='pending_singe'）。
     *
     * @param event 出栏事件，载荷 {@link PigMarketing} 同事务 INSERT 已完成（含 id / earNo / marketingDate / outWeight）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPigMarketing(PigMarketingEvent event) {
        PigMarketing marketing = event.getMarketing();
        String earNo = marketing.getEarNo();
        try {
            BarInfo bar = new BarInfo();
            bar.setBarId(bizCodeGenerator.generate(BizCodeType.BAR_NO, Map.of()));
            bar.setEarNo(earNo);
            bar.setMarketingTime(toDate(marketing.getMarketingDate()));
            bar.setMarketingWeight(marketing.getOutWeight());
            bar.setStatus(BAR_STATUS_PENDING_SINGE);
            bar.setInMethod(IN_METHOD_SINGE_ROOM);
            // tenant_id / 6 个审计字段由 MybatisPlusInjector 自动 fill；del_unique=0 由 DDL DEFAULT
            barInfoMapper.insert(bar);
            log.info("[CROSS-FLOW-001] bar_info 创建成功 barId={} earNo={} marketingId={}",
                bar.getBarId(), earNo, marketing.getId());
            // TRC-CORE-001：出栏上市追溯事件（按耳号反查 trace_code；猪肉链当前无生成入口 → warn 跳过）
            traceService.recordEventByEarNo(earNo, TraceContentConst.MARKETING);
        } catch (Exception e) {
            // AFTER_COMMIT 抛异常不会回滚养殖事务（已提交），但会让上游误以为出栏失败 → 全 swallow + log
            log.error("[CROSS-FLOW-001] 自动创建 bar_info 失败 earNo={} marketingId={}: {}",
                earNo, marketing.getId(), e.getMessage(), e);
        }
    }

    /**
     * {@link LocalDateTime} → {@link Date} 转换（系统时区）。
     *
     * <p>{@code BarInfo.marketingTime} 是 {@code java.util.Date}，{@code PigMarketing.marketingDate}
     * 是 {@code LocalDateTime}，需显式转换。两端字段语义一致（出栏时刻）。</p>
     *
     * @param ldt {@code marketingDate}，可为 null（外购无出栏时刻 — V1 不出现，BRD-EVENT-004 已强校验）
     * @return {@code java.util.Date}，null 安全
     */
    private Date toDate(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }
}
