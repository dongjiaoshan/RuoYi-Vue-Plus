package org.dromara.djs.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * djs 业务定时任务总开关（独立于 SnailJob 调度中心）。
 *
 * <p>V1 用 Spring {@code @Scheduled} 在 ruoyi-admin 进程内单机跑统计聚合 job
 * （养殖/仓库 日→月→年），不依赖 {@code ruoyi-extend/ruoyi-snailjob-server}。
 * 框架自带的 {@code SnailJobConfig} 上的 {@code @EnableScheduling} 被
 * {@code snail-job.enabled=false} 门控，整个 Spring 调度容器未启用，
 * 故这里独立开 {@code @EnableScheduling}，让 djs 自己的 @Scheduled 生效。</p>
 *
 * <p>开关 {@code djs.schedule.enabled}（默认 true）；置 false 整体停掉 djs 定时任务。
 * 单农场单实例无需分布式调度；V2 多实例再迁 SnailJob（保留 job 类加注解即可）。</p>
 *
 * <p>⚠️ {@code @EnableScheduling} 是全局的，会激活所有 {@code @Scheduled} bean。
 * 当前 djs 域仅养殖统计聚合 job + 有机证书到期预警 job，二者均经
 * {@link org.dromara.djs.common.job.DjsJobRunner} 跑（租户上下文安全）。</p>
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "djs.schedule", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DjsSchedulingConfig {

    public DjsSchedulingConfig() {
        log.info("[DjsSchedulingConfig] djs 定时任务已启用（Spring @Scheduled，单机模式，不依赖 SnailJob 调度中心）");
    }
}
