package org.dromara.djs.breed.dashboard.job;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.breed.dashboard.service.IDashboardService;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 养殖统计聚合定时任务（BRD-DASH-001 调度接线）。
 *
 * <p>每日凌晨按业务日重算最近一个滚动窗口的 日({@code t_farm_indicator_record}) →
 * 月({@code t_farm_monthly_production}) → 年({@code t_farm_year_production}) 聚合表（UPSERT 幂等）。
 * 聚合逻辑复用 {@link IDashboardService#triggerAggregateRange}（与手动端点
 * {@code POST /djs/breed/dashboard/trigger-aggregate} 同源），本类只负责「定时触发 + 租户上下文」，
 * 不含业务逻辑。</p>
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
     * 每晚滚动重算的窗口天数（默认 60）。
     *
     * <p>为什么是 60 不是 30/45：① 要盖住「上一个自然月 + 本月」，月初跑时 45 天仍可能够不着上月月初；
     * ② 日表从 2026-07-31 起有行，60 天窗口让**历史回补不需要任何手工调用** —— 上线后第一次定时跑
     * 就把全部历史按业务日重算了一遍。代价是每晚多跑十几秒。</p>
     */
    @Value("${djs.schedule.breed-aggregate-rebuild-days:60}")
    private int rebuildDays;

    /**
     * 注册重算逻辑到 {@link DjsJobRegistry}，供 admin 手动重跑（按目标日）调用。
     * 与 {@link #aggregate()} 定时触发同源（共用 {@link IDashboardService#triggerAggregate}）。
     */
    @PostConstruct
    public void register() {
        jobRegistry.register(JOB_NAME, dashboardService::triggerAggregate);
    }

    /**
     * 每日 0:00 触发，按业务日重算「最近 {@code rebuildDays} 天」滚动窗口（BRD-STAT-004）。
     *
     * <p>不只算 T-1 的原因：单据经常业务日 ≠ 录入日（9/8 出栏 9/9 上午才录、8 月有 8 窝分娩晚录），
     * 只算 T-1 那些补录永远进不了它该在的那一天。窗口默认 60 天（见 {@link #rebuildDays}）；
     * 日→月→年的顺序由 triggerAggregateRange 内部保证，逐日各自开事务。</p>
     *
     * <p>cron 与窗口天数可经 {@code djs.schedule.breed-aggregate-cron} /
     * {@code djs.schedule.breed-aggregate-rebuild-days} 覆盖。</p>
     */
    @Scheduled(cron = "${djs.schedule.breed-aggregate-cron:0 0 0 * * ?}")
    public void aggregate() {
        DjsJobRunner.run(JOB_NAME, () -> {
            LocalDate end = LocalDate.now().minusDays(1);
            LocalDate start = end.minusDays(Math.max(rebuildDays, 1) - 1L);
            dashboardService.triggerAggregateRange(start, end);
        });
    }
}
