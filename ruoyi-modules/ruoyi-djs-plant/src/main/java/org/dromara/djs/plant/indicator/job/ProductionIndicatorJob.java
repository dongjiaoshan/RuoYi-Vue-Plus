package org.dromara.djs.plant.indicator.job;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.plant.indicator.service.ProductionIndicatorService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** 每日重算 T-31 至 T-1，补入窗口内按业务日登记的迟到单据。 */
@Component
@RequiredArgsConstructor
public class ProductionIndicatorJob {

    public static final String JOB_NAME = "plant-production-indicators";

    private final ProductionIndicatorService service;
    private final DjsJobRegistry registry;

    @PostConstruct
    public void register() {
        registry.register(JOB_NAME, date -> service.recalculate(date, date));
    }

    @Scheduled(cron = "${djs.schedule.plant-production-indicators-cron:0 20 0 * * ?}",
        zone = ProductionIndicatorService.BUSINESS_ZONE)
    public void aggregate() {
        LocalDate today = LocalDate.now(ZoneId.of(ProductionIndicatorService.BUSINESS_ZONE));
        DjsJobRunner.run(JOB_NAME, () -> service.recalculate(today.minusDays(31), today.minusDays(1)));
    }
}
