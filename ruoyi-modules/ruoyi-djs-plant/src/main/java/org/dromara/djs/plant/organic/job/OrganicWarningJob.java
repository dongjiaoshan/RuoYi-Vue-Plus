package org.dromara.djs.plant.organic.job;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.service.ConfigService;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.plant.organic.mapper.CropOrganicMapper;
import org.dromara.djs.plant.organic.mapper.PlotOrganicMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 有机证书到期预警定时任务（PLT-MD-003）。
 *
 * <p>V1：Spring {@code @Scheduled(cron = "0 0 0 * * ?")} 每天 0 点扫描。
 *    V2：迁移到 SnailJob 控制台（保留本类，加 SnailJob 注解即可，无业务逻辑改动）。</p>
 *
 * <p>阈值天数读 {@code sys_config.plant.organic.warning_days}（默认 60，DDL seed）。
 *    阈值若读不到（key 不存在），降级到代码常量 60。</p>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganicWarningJob {

    /** sys_config 阈值键。 */
    public static final String CONFIG_KEY_WARNING_DAYS = "plant.organic.warning_days";

    /** 降级默认值（sys_config 缺失时使用）。 */
    private static final int FALLBACK_DAYS = 60;

    private final PlotOrganicMapper plotOrganicMapper;
    private final CropOrganicMapper cropOrganicMapper;
    private final ConfigService configService;
    private final DjsJobRegistry jobRegistry;

    /** job 名（注册表 key + 日志名 + admin 重跑下拉项）。 */
    public static final String JOB_NAME = "organic-warning";

    /**
     * 注册重扫逻辑到 {@link DjsJobRegistry}，供 admin 手动重跑调用。
     * 无日期参数：扫描以「当前日期」为基准（忽略 targetDate），重跑即重扫一次。
     */
    @PostConstruct
    public void register() {
        jobRegistry.register(JOB_NAME, date -> doScan());
    }

    /**
     * 定时扫描入口：每天 0 点触发。
     *
     * <p>经 {@link DjsJobRunner} 跑以注入 tenant_id —— 裸 {@code @Scheduled} 线程无上下文，
     *    否则 {@code markWarning} 的 UPDATE 条件 tenant_id 为 null 会匹配不到业务数据（静默 no-op）。</p>
     */
    @Scheduled(cron = "${djs.schedule.organic-warning-cron:0 0 0 * * ?}")
    public void scanWarning() {
        DjsJobRunner.run(JOB_NAME, this::doScan);
    }

    /**
     * 实际扫描逻辑（供定时任务 + 管理员手动端点复用；手动端点走 HTTP 有租户上下文，直接调本方法）。
     *
     * <p>幂等：扫描条件 {@code is_warning=2 AND DATEDIFF(valid, CURRENT_DATE) <= days}，
     *    已置 1 的不重复更新；同一日多次跑也只首次写入。</p>
     */
    public void doScan() {
        int days = resolveWarningDays();
        log.info("[OrganicWarningJob] 扫描有机证书到期预警，阈值 {} 天", days);

        int plotUpdated = plotOrganicMapper.markWarning(days);
        int cropUpdated = cropOrganicMapper.markWarning(days);

        log.info("[OrganicWarningJob] 扫描完成：土地证书 {} 条 / 作物证书 {} 条 置预警", plotUpdated, cropUpdated);
    }

    /**
     * 读取阈值天数；任何异常（key 不存在 / value 非数字）降级到 {@link #FALLBACK_DAYS}。
     */
    private int resolveWarningDays() {
        try {
            Integer days = configService.getConfigInt(CONFIG_KEY_WARNING_DAYS);
            if (days != null && days > 0) {
                return days;
            }
        } catch (Exception e) {
            log.warn("[OrganicWarningJob] 读取 sys_config {} 失败，降级 {} 天", CONFIG_KEY_WARNING_DAYS, FALLBACK_DAYS, e);
        }
        return FALLBACK_DAYS;
    }
}
