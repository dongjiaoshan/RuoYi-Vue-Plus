package org.dromara.djs.store.loss.job;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.store.loss.service.IStoreLossService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 门店损耗记录定时任务（DENGBO-R15）。
 *
 * <p>每晚落盘两类损耗到 {@code t_store_loss_record}：门店日损耗（读盘点台账 loss_qty 原样搬）
 * + 白条分割损耗（到店重−退回入库重 钳 0）。聚合逻辑复用 {@link IStoreLossService#aggregate}
 * （与 admin 手动重跑同源），本类只负责「定时触发 + 租户上下文」。</p>
 *
 * <p>V1：Spring {@code @Scheduled} 单机跑（依赖 common 模块 {@code DjsSchedulingConfig} 开
 * {@code @EnableScheduling}）。经 {@link DjsJobRunner} 跑以注入 tenant_id（裸定时线程无上下文，
 * 否则 INSERT 写 NULL）。cron 1:30 错峰（避开养殖 0:30 + 仓库 0:00/0:45）。
 * cron 可经 {@code djs.schedule.store-loss-cron} 覆盖。</p>
 *
 * @author djs
 * @since DENGBO-R15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreLossJob {

    private final IStoreLossService storeLossService;
    private final DjsJobRegistry jobRegistry;

    /** job 名（注册表 key + 日志名 + admin 重跑下拉项）。 */
    public static final String JOB_NAME = "store-loss";

    /**
     * 注册聚合逻辑到 {@link DjsJobRegistry}，供 admin 手动重跑（按目标日）调用。
     * 与 {@link #aggregate()} 定时触发同源（共用 {@link IStoreLossService#aggregate}）。
     */
    @PostConstruct
    public void register() {
        jobRegistry.register(JOB_NAME, storeLossService::aggregate);
    }

    /** 每天 1:30 触发，落盘当天两类门店损耗（错峰养殖/仓库跑批）。 */
    @Scheduled(cron = "${djs.schedule.store-loss-cron:0 30 1 * * ?}")
    public void aggregate() {
        DjsJobRunner.run(JOB_NAME, () -> storeLossService.aggregate(null));
    }
}
