package org.dromara.djs.warehouse.loss.service;

import java.time.LocalDate;

/**
 * 果蔬生产损耗日聚合 Service（邓博最终口径）。
 *
 * <p>把「按产品每日残差」的果蔬生产损耗持久化到统一损耗流水 {@code t_warehouse_loss_flow}
 * （{@code loss_type='production_loss'}），供损耗总览 / 库存详情损耗 tab 展示。
 * 果蔬生产损耗 = 果蔬领用 − 退回 − 录入损耗 − 饲喂 − 打包生产使用量（残差 &gt; 0 才落库），
 * 全部按 {@code belong_type='vegetable'}（自产果蔬；外购商品/包材天然排除；猪肉/其他产品无生产损耗展示需求）。</p>
 *
 * @author djs
 */
public interface IProductionLossService {

    /**
     * 聚合并落库某自然日的果蔬生产损耗（幂等：重算前先软删该日已算行，供重跑覆盖）。
     *
     * <p>由仓库统计任务 {@code WarehouseStatServiceImpl#aggregate} 驱动（T-1，先于 cropp 聚合跑，
     * 故 production_loss 对 cropp 净菜损耗率读取即时新鲜）；admin「定时任务重跑」重跑 {@code warehouse-stat} 一并覆盖。
     * 原独立 ProductionLossAggregateJob 已撤。</p>
     *
     * @param targetDate 目标日；{@code null} = 昨天（T-1，Asia/Shanghai）
     * @return 执行摘要（tenant / date / 处理产品数 / 落库条数）
     */
    String aggregate(LocalDate targetDate);
}
