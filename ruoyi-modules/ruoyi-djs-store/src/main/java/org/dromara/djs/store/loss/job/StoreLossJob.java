package org.dromara.djs.store.loss.job;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.store.loss.service.IStoreLossService;
import org.dromara.djs.store.stat.service.StoreProductionIndicatorService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** 门店生产指标和损耗的定时回刷，手动重跑与定时任务共用逐日计算。 */
@Component
@RequiredArgsConstructor
public class StoreLossJob {

    public static final String JOB_NAME = "store-loss";
    private static final String BUSINESS_ZONE = "Asia/Shanghai";
    private static final int REFRESH_DAYS = 31;

    private final IStoreLossService storeLossService;
    private final StoreProductionIndicatorService indicatorService;
    private final DjsJobRegistry jobRegistry;

    @PostConstruct
    public void register() {
        jobRegistry.register(JOB_NAME, storeLossService::aggregate);
    }

    @Scheduled(cron = "${djs.schedule.store-loss-cron:0 30 1 * * ?}", zone = BUSINESS_ZONE)
    public void aggregate() {
        LocalDate today = LocalDate.now(ZoneId.of(BUSINESS_ZONE));
        DjsJobRunner.run(JOB_NAME, () -> indicatorService.rebuildRollingWindow(today.minusDays(REFRESH_DAYS), today.minusDays(1)));
    }
}
