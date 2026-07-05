package org.dromara.djs.warehouse.loss.service;

import java.time.LocalDate;

/**
 * 生产损耗日聚合 Service（row38，邓博）。
 *
 * <p>把「按产品每日残差」的生产损耗持久化到统一损耗流水 {@code t_warehouse_loss_flow}
 * （{@code loss_type='production_loss'}），供损耗总览 / 库存详情损耗 tab 展示。
 * 生产损耗 = 产品领用 − 产品退回 − 产品录入损耗 − 产品饲喂（残差 &gt; 0 才落库）。</p>
 *
 * @author djs
 */
public interface IProductionLossService {

    /**
     * 聚合并落库某自然日的生产损耗（幂等：重算前先软删该日已算行，供重跑覆盖）。
     *
     * <p>由 {@code ProductionLossAggregateJob} 定时（T-1）调，也由 admin「定时任务重跑」按指定日期调
     * （registry job_name={@code production-loss-aggregate}）。</p>
     *
     * @param targetDate 目标日；{@code null} = 昨天（T-1，Asia/Shanghai）
     * @return 执行摘要（tenant / date / 处理产品数 / 落库条数）
     */
    String aggregate(LocalDate targetDate);
}
