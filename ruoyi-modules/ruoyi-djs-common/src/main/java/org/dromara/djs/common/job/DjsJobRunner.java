package org.dromara.djs.common.job;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.job.service.IDjsJobLogService;

import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * djs 定时任务统一执行模板。
 *
 * <p>解决所有 djs 统计 job 共有的三个横切问题：</p>
 * <ol>
 *   <li><b>租户注入</b>：裸 {@code @Scheduled} 线程无 HTTP / Sa-Token 上下文，
 *       MyBatis-Plus 租户拦截器 {@code PlusTenantLineHandler.getTenantId()} 拿到 null，
 *       INSERT 不注 {@code tenant_id}、UPDATE/SELECT 条件错位 → 数据写坏或匹配不到。
 *       这里用 {@link TenantHelper#dynamic(String, Runnable)} 设线程内动态租户，
 *       让拦截器在跑批线程里也注入指定 {@code tenant_id}。</li>
 *   <li><b>异常隔离 + 审计</b>：job 异常只记日志不外抛，避免单个 job 失败拖垮调度线程；
 *       统一打 start / done / fail 日志便于排查。</li>
 *   <li><b>执行落库</b>（DENGBO row7）：每次执行向 {@code t_djs_job_log} 落一行
 *       （{@code running → success/fail}），supply admin「定时任务重跑」页查询 +
 *       失败重跑。落库经 {@link IDjsJobLogService} 旁路完成，bean 不存在 / 落库异常
 *       绝不影响 job 主体（catch 兜底）。</li>
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

    /** 触发方式：定时调度。 */
    public static final String TRIGGER_SCHEDULE = "schedule";

    /** 触发方式：手动重跑。 */
    public static final String TRIGGER_MANUAL = "manual";

    /**
     * 在指定租户上下文中执行一个 job；异常只记日志不外抛，执行落审计日志。
     *
     * @param jobName  日志用 job 名
     * @param tenantId 租户 id（V1 传 {@link #DEFAULT_TENANT}）
     * @param task     job 主体
     */
    public static void runForTenant(String jobName, String tenantId, Runnable task) {
        TenantHelper.dynamic(tenantId, () ->
            execute(jobName, tenantId, null, TRIGGER_SCHEDULE, ignored -> task.run()));
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

    /**
     * 按目标日重跑（admin 手动重跑用，落 {@code target_date} + {@code trigger_type=manual}）。
     *
     * <p>由 {@code DjsJobRerunController} 在 begin..end 区间逐日调用，每日一条审计行。
     * 无日期 job 传 {@code targetDate=null} 跑一次（task 内部忽略入参）。</p>
     *
     * @param jobName     日志用 job 名
     * @param tenantId    租户 id（V1 传 {@link #DEFAULT_TENANT}）
     * @param targetDate  重算目标日（可为 null）
     * @param triggerType 触发方式（{@link #TRIGGER_MANUAL} / {@link #TRIGGER_SCHEDULE}）
     * @param task        重算逻辑（入参为目标日）
     */
    public static void runForDate(String jobName, String tenantId, LocalDate targetDate,
                                  String triggerType, Consumer<LocalDate> task) {
        TenantHelper.dynamic(tenantId, () ->
            execute(jobName, tenantId, targetDate, triggerType, task));
    }

    /**
     * 统一执行 + 审计落库主体（已在租户上下文内调用）。
     *
     * <p>落库（recordStart/Done/Fail）经 {@link IDjsJobLogService} 旁路完成；bean 未就绪或
     * 落库异常被 catch 吞掉（只 warn），绝不阻断 job 主体；job 主体异常被 catch 转 fail 日志，
     * 不外抛（避免拖垮调度线程）。</p>
     */
    private static void execute(String jobName, String tenantId, LocalDate targetDate,
                                String triggerType, Consumer<LocalDate> task) {
        long start = System.currentTimeMillis();
        log.info("[DjsJob:{}] start tenant={} targetDate={} trigger={}", jobName, tenantId, targetDate, triggerType);
        IDjsJobLogService logService = resolveLogService();
        Long logId = safeRecordStart(logService, jobName, targetDate, triggerType);
        try {
            task.accept(targetDate);
            long cost = System.currentTimeMillis() - start;
            log.info("[DjsJob:{}] done tenant={} cost={}ms", jobName, tenantId, cost);
            safeRecordDone(logService, logId, cost);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("[DjsJob:{}] FAILED tenant={} cost={}ms", jobName, tenantId, cost, e);
            safeRecordFail(logService, logId, cost, String.valueOf(e.getMessage()));
        }
    }

    /** 取审计 service bean；容器未就绪 / bean 不存在时返 null（只记日志，不挂）。 */
    private static IDjsJobLogService resolveLogService() {
        try {
            return SpringUtils.getBean(IDjsJobLogService.class);
        } catch (Exception e) {
            log.warn("[DjsJob] 审计日志 service 未就绪，本次执行不落库：{}", e.getMessage());
            return null;
        }
    }

    private static Long safeRecordStart(IDjsJobLogService logService, String jobName,
                                        LocalDate targetDate, String triggerType) {
        if (logService == null) {
            return null;
        }
        try {
            return logService.recordStart(jobName, targetDate, triggerType);
        } catch (Exception e) {
            log.warn("[DjsJob:{}] 审计 recordStart 失败（不影响主体）：{}", jobName, e.getMessage());
            return null;
        }
    }

    private static void safeRecordDone(IDjsJobLogService logService, Long logId, long cost) {
        if (logService == null || logId == null) {
            return;
        }
        try {
            logService.recordDone(logId, cost);
        } catch (Exception e) {
            log.warn("[DjsJob] 审计 recordDone 失败（不影响主体）：{}", e.getMessage());
        }
    }

    private static void safeRecordFail(IDjsJobLogService logService, Long logId, long cost, String error) {
        if (logService == null || logId == null) {
            return;
        }
        try {
            logService.recordFail(logId, cost, error);
        } catch (Exception e) {
            log.warn("[DjsJob] 审计 recordFail 失败（不影响主体）：{}", e.getMessage());
        }
    }
}
