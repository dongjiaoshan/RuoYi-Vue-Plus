package org.dromara.djs.breed.dashboard.job;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.breed.dashboard.service.IDashboardService;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 养殖统计聚合定时任务（BRD-DASH-001 调度接线）。
 *
 * <p>每日凌晨重算 T-1 的 日({@code t_farm_sow_record}) → 月({@code t_farm_monthly_production})
 * → 年({@code t_farm_year_production}) 聚合表（UPSERT 幂等）。聚合逻辑复用
 * {@link IDashboardService#triggerAggregate}（与手动端点 {@code POST /djs/breed/dashboard/trigger-aggregate}
 * 同源），本类只负责「定时触发 + 租户上下文」，不含业务逻辑。</p>
 *
 * <p>V1：Spring {@code @Scheduled} 单机跑（依赖 {@link org.dromara.djs.common.config.DjsSchedulingConfig}
 * 开 {@code @EnableScheduling}）。经 {@link DjsJobRunner} 跑以注入 tenant_id（裸定时线程无上下文）。
 * 定时失败的人工补跑入口仍是手动 trigger-aggregate 端点。V2 迁 SnailJob，保留本类加注解即可。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardAggregateJob {

    private final IDashboardService dashboardService;
    private final DjsJobRegistry jobRegistry;

    /** job 名（注册表 key + 日志名 + admin 重跑下拉项）。 */
    public static final String JOB_NAME = "breed-aggregate";

    /**
     * 注册重算逻辑到 {@link DjsJobRegistry}，供 admin 手动重跑（按目标日）调用。
     * 与 {@link #aggregate()} 定时触发同源（共用 {@link IDashboardService#triggerAggregate}）。
     */
    @PostConstruct
    public void register() {
        jobRegistry.register(JOB_NAME, dashboardService::triggerAggregate);
    }

    /**
     * 每日 0:00 触发，重算昨天 T-1（邓博 row9：每晚 12 点更新，日→月→年顺序由 triggerAggregate 内部保证）。
     * cron 可经 {@code djs.schedule.breed-aggregate-cron} 覆盖。
     */
    @Scheduled(cron = "${djs.schedule.breed-aggregate-cron:0 0 0 * * ?}")
    public void aggregate() {
        DjsJobRunner.run(JOB_NAME, () -> dashboardService.triggerAggregate(null));
    }
}
