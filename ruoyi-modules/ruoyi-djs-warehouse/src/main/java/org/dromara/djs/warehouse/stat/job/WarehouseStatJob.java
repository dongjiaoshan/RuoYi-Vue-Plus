package org.dromara.djs.warehouse.stat.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.warehouse.stat.service.IWarehouseStatService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 仓库统计聚合定时任务（WMS-STAT-001 调度接线，邓博 admin row16/17/18）。
 *
 * <p>每日凌晨重算 T-1 的 日({@code t_warehouse_indicator_record}) + 作物日
 * ({@code t_warehouse_cropp_record}) → 当月月表({@code t_warehouse_monthly_record}) 聚合（UPSERT 幂等）。
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

    /** 每日 0:45 触发，重算昨天 T-1（错峰养殖 0:30）。 */
    @Scheduled(cron = "${djs.schedule.warehouse-stat-cron:0 45 0 * * ?}")
    public void aggregate() {
        DjsJobRunner.run("warehouse-stat", () -> warehouseStatService.aggregate(null));
    }
}
