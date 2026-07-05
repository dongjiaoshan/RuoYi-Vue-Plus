package org.dromara.djs.warehouse.loss.job;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.warehouse.loss.service.IProductionLossService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 生产损耗日聚合定时任务（row38，邓博）。
 *
 * <p>每日凌晨算 T-1 的「按产品生产损耗残差」（领用 − 退回 − 录入损耗 − 饲喂 &gt; 0）落 {@code loss_flow}
 * （{@code loss_type='production_loss'}），供损耗总览展示。聚合逻辑复用
 * {@link IProductionLossService#aggregate}（与 admin「定时任务重跑」按指定日期同源）。</p>
 *
 * <p>经 {@link DjsJobRunner} 跑以注入 tenant_id（裸定时线程无上下文，否则聚合 WHERE / INSERT 拿不到租户）。
 * 错峰 0:50——晚于仓库统计 0:00、养殖 0:30，确保当日流水已入账。cron 可经
 * {@code djs.schedule.production-loss-cron} 覆盖。注册到 {@link DjsJobRegistry} 供 admin 按日重跑。</p>
 *
 * @author djs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionLossAggregateJob {

    private final IProductionLossService productionLossService;
    private final DjsJobRegistry jobRegistry;

    /** job 名（注册表 key + 日志名 + admin 重跑下拉项）。 */
    public static final String JOB_NAME = "production-loss-aggregate";

    /** 注册重算逻辑到 registry，供 admin 手动重跑（按目标日）。与定时触发同源。 */
    @PostConstruct
    public void register() {
        jobRegistry.register(JOB_NAME, productionLossService::aggregate);
    }

    /** 每日 0:50 触发，算昨天 T-1 生产损耗。 */
    @Scheduled(cron = "${djs.schedule.production-loss-cron:0 50 0 * * ?}")
    public void aggregate() {
        DjsJobRunner.run(JOB_NAME, () -> productionLossService.aggregate(null));
    }
}
