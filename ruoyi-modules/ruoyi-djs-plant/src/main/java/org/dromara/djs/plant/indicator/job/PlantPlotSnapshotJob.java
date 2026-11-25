package org.dromara.djs.plant.indicator.job;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.plant.indicator.service.PlotSnapshotService;
import org.dromara.djs.plant.indicator.service.ProductionIndicatorService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlantPlotSnapshotJob {

    public static final String JOB_NAME = "plant-plot-snapshot";

    private final PlotSnapshotService snapshots;
    private final DjsJobRegistry registry;

    @PostConstruct
    public void register() {
        registry.register(JOB_NAME, snapshots::capture);
    }

    @Scheduled(cron = "${djs.schedule.plant-plot-snapshot-cron:0 50 23 * * ?}",
        zone = ProductionIndicatorService.BUSINESS_ZONE)
    public void capture() {
        DjsJobRunner.runForDate(JOB_NAME, DjsJobRunner.DEFAULT_TENANT, snapshots.today(),
            DjsJobRunner.TRIGGER_SCHEDULE, snapshots::capture);
    }
}
