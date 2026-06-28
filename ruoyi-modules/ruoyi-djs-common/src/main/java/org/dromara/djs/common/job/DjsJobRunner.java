package org.dromara.djs.common.job;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;

/**
 * djs 定时任务统一执行模板。
 *
 * <p>解决所有 djs 统计 job 共有的两个横切问题：</p>
 * <ol>
 *   <li><b>租户注入</b>：裸 {@code @Scheduled} 线程无 HTTP / Sa-Token 上下文，
 *       MyBatis-Plus 租户拦截器 {@code PlusTenantLineHandler.getTenantId()} 拿到 null，
 *       INSERT 不注 {@code tenant_id}、UPDATE/SELECT 条件错位 → 数据写坏或匹配不到。
 *       这里用 {@link TenantHelper#dynamic(String, Runnable)} 设线程内动态租户，
 *       让拦截器在跑批线程里也注入指定 {@code tenant_id}。</li>
 *   <li><b>异常隔离 + 审计</b>：job 异常只记日志不外抛，避免单个 job 失败拖垮调度线程；
 *       统一打 start / done / fail 日志便于排查。</li>
 * </ol>
 *
 * <p><b>约定</b>：所有 djs 统计 / 跑批 job 必须经本模板跑，不要在 {@code @Scheduled}
 * 方法体里直接写库。</p>
 *
 * @author djs
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DjsJobRunner {

    /** V1 单农场固定租户 id。 */
    public static final String DEFAULT_TENANT = "1001";

    /**
     * 在指定租户上下文中执行一个 job；异常只记日志不外抛。
     *
     * @param jobName  日志用 job 名
     * @param tenantId 租户 id（V1 传 {@link #DEFAULT_TENANT}）
     * @param task     job 主体
     */
    public static void runForTenant(String jobName, String tenantId, Runnable task) {
        long start = System.currentTimeMillis();
        log.info("[DjsJob:{}] start tenant={}", jobName, tenantId);
        try {
            TenantHelper.dynamic(tenantId, task);
            log.info("[DjsJob:{}] done tenant={} cost={}ms", jobName, tenantId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[DjsJob:{}] FAILED tenant={} cost={}ms", jobName, tenantId, System.currentTimeMillis() - start, e);
        }
    }

    /**
     * 便捷重载：默认租户 {@link #DEFAULT_TENANT}（V1 单农场）。
     *
     * @param jobName 日志用 job 名
     * @param task    job 主体
     */
    public static void run(String jobName, Runnable task) {
        runForTenant(jobName, DEFAULT_TENANT, task);
    }
}
