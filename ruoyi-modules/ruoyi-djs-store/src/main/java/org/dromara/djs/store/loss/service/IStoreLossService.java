package org.dromara.djs.store.loss.service;

import java.time.LocalDate;

/**
 * 门店损耗记录聚合 Service（DENGBO-R15）。
 *
 * <p>每晚定时任务把两类损耗落盘到 {@code t_store_loss_record}：门店日损耗（读盘点台账 loss_qty 原样搬）
 * + 白条分割损耗（到店重−退回入库重 钳 0，按门店汇总一行）。</p>
 *
 * @author djs
 * @since DENGBO-R15
 */
public interface IStoreLossService {

    /**
     * 聚合指定日的门店损耗到 {@code t_store_loss_record}（幂等：先软删该日旧行再重插）。
     *
     * @param targetDate 目标日；{@code null} 取今天（{@code Asia/Shanghai}）
     */
    void aggregate(LocalDate targetDate);
}
