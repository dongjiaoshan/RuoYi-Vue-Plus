package org.dromara.djs.breed.event.castrate.job;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.breed.event.castrate.service.ICastrateService;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 公仔猪超龄自动阉割定时任务（admin row186）。
 *
 * <p>每日把日龄超过 7 天、尚未阉割的公仔猪自动置为已阉割：每头写一条阉割记录 +
 * 一条 CASTRATE 事件台账 + 置 {@code is_castrated=2}，与人工录入同口径，
 * 业务逻辑全在 {@link ICastrateService#autoCastrateOverAgePiglets}，本类只负责触发。</p>
 *
 * <p>经 {@link DjsJobRunner} 跑以注入 tenant_id（裸定时线程无上下文）；
 * 同时注册进 {@link DjsJobRegistry}，支持 admin「定时任务重跑」页按日期补跑。</p>
 *
 * @author djs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PigletAutoCastrateJob {

    private final ICastrateService castrateService;
    private final DjsJobRegistry jobRegistry;

    /** job 名（注册表 key + 日志名 + admin 重跑下拉项）。 */
    public static final String JOB_NAME = "breed-auto-castrate";

    /**
     * 注册重算逻辑，供 admin 手动重跑（按目标日）调用；与 {@link #autoCastrate()} 定时触发同源。
     */
    @PostConstruct
    public void register() {
        jobRegistry.register(JOB_NAME, castrateService::autoCastrateOverAgePiglets);
    }

    /**
     * 每日 0:30 触发（排在 0:00 的养殖统计聚合之后，避免同时抢库）。
     * cron 可经 {@code djs.schedule.breed-auto-castrate-cron} 覆盖。
     */
    @Scheduled(cron = "${djs.schedule.breed-auto-castrate-cron:0 30 0 * * ?}")
    public void autoCastrate() {
        DjsJobRunner.run(JOB_NAME, () -> castrateService.autoCastrateOverAgePiglets(LocalDate.now()));
    }
}
