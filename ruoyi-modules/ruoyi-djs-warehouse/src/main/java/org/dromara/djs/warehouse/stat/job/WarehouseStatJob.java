package org.dromara.djs.warehouse.stat.job;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.warehouse.stat.service.IWarehouseStatService;
import org.dromara.djs.warehouse.stat.service.impl.WarehouseStatServiceImpl;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TreeSet;

/**
 * 仓库统计聚合定时任务（WMS-STAT-001 调度接线，邓博 admin row16/17/18）。
 *
 * <p>每日凌晨重算 T-1 的 日({@code t_warehouse_indicator_record}) + 作物日
 * ({@code t_warehouse_cropp_record}) → 当月月表({@code t_warehouse_monthly_record}) 聚合（UPSERT 幂等），
 * 再回补最近 {@value WarehouseStatServiceImpl#INDICATOR_REFRESH_DAYS} 天日表的仓库生产指标。
 * 聚合逻辑复用 {@link IWarehouseStatService#aggregate}（与手动端点
 * {@code POST /djs/warehouse/stat/trigger-aggregate} 同源），本类只负责「定时触发 + 租户上下文」。</p>
 *
 * <p>V1：Spring {@code @Scheduled} 单机跑（依赖 common 模块
 * {@code DjsSchedulingConfig} 开 {@code @EnableScheduling}）。经 {@link DjsJobRunner} 跑以注入 tenant_id
 * （裸定时线程无上下文，否则 INSERT 写 NULL）。错峰 0:45（避开养殖 0:30 + 0:00 流水号重置）。
 * cron 可经 {@code djs.schedule.warehouse-stat-cron} 覆盖。V2 迁 SnailJob 保留本类即可。</p>
 *
 * @author djs
 * @since WMS-STAT-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseStatJob {

    private final IWarehouseStatService warehouseStatService;
    private final DjsJobRegistry jobRegistry;

    /** job 名（注册表 key + 日志名 + admin 重跑下拉项）。 */
    public static final String JOB_NAME = "warehouse-stat";
    public static final String VEG_STOCK_SNAPSHOT_JOB_NAME = "warehouse-veg-stock-snapshot";
    private static final LocalTime STOCK_CLOSE_TIME = LocalTime.of(23, 50);

    /**
     * 注册重算逻辑到 {@link DjsJobRegistry}，供 admin 手动重跑（按目标日）调用。
     * 两个 job 与调度入口复用同一个聚合或采集服务。
     */
    @PostConstruct
    public void register() {
        jobRegistry.register(JOB_NAME, warehouseStatService::aggregate);
        jobRegistry.register(VEG_STOCK_SNAPSHOT_JOB_NAME, warehouseStatService::captureVegStockSnapshot);
    }

    /**
     * 每日 0:00 触发：重算昨天 T-1（邓博 row9：每晚 12 点；养殖/仓库独立域，并发安全），
     * 再回补 T-32 至 T-2，以及窗口内的变更影响的更早领用、生产和需求日。
     */
    @Scheduled(cron = "${djs.schedule.warehouse-stat-cron:0 0 0 * * ?}", zone = WarehouseStatServiceImpl.BUSINESS_ZONE)
    public void aggregate() {
        DjsJobRunner.run(JOB_NAME, () -> {
            LocalDate yesterday = WarehouseStatServiceImpl.businessToday().minusDays(1);
            LocalDate from = yesterday.minusDays(WarehouseStatServiceImpl.INDICATOR_REFRESH_DAYS);
            warehouseStatService.aggregate(yesterday);
            warehouseStatService.refreshProductionIndicators(from, yesterday.minusDays(1));
            LocalDate until = yesterday.plusDays(1);
            var earlier = new TreeSet<>(warehouseStatService.findEarlierCutPickupDates(from, until));
            earlier.addAll(warehouseStatService.findEarlierVegIndicatorDates(from, until));
            for (LocalDate date : earlier) {
                warehouseStatService.refreshProductionIndicators(date, date);
            }
        });
    }
    /** 收工时先拍当日库位余额，并立即刷新该日和该月的库存指标。 */
    @Scheduled(cron = "${djs.schedule.warehouse-veg-stock-snapshot-cron:0 50 23 * * ?}",
        zone = WarehouseStatServiceImpl.BUSINESS_ZONE)
    public void captureVegStockSnapshot() {
        DjsJobRunner.runForDate(VEG_STOCK_SNAPSHOT_JOB_NAME, DjsJobRunner.DEFAULT_TENANT,
            WarehouseStatServiceImpl.businessToday(), DjsJobRunner.TRIGGER_SCHEDULE,
            warehouseStatService::captureVegStockSnapshot);
    }
    /** 发布或重启错过当日调度时刻时，仍允许当晚首次采集。 */
    @EventListener(ApplicationReadyEvent.class)
    public void captureVegStockAfterLateStart() {
        var now = ZonedDateTime.now(ZoneId.of(WarehouseStatServiceImpl.BUSINESS_ZONE));
        if (!now.toLocalTime().isBefore(STOCK_CLOSE_TIME)) {
            DjsJobRunner.runForDate(VEG_STOCK_SNAPSHOT_JOB_NAME, DjsJobRunner.DEFAULT_TENANT,
                now.toLocalDate(), DjsJobRunner.TRIGGER_SCHEDULE, warehouseStatService::captureMissingVegStockSnapshot);
        }
    }
}
