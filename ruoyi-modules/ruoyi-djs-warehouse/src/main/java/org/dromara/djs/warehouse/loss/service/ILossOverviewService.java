package org.dromara.djs.warehouse.loss.service;

import org.dromara.djs.warehouse.loss.domain.vo.LossOverviewDailyVo;
import org.dromara.djs.warehouse.loss.domain.vo.LossOverviewDetailVo;

import java.util.Date;
import java.util.List;

/**
 * 损耗总览 Service（WMS-LOSS-OVERVIEW-001，仓库-admin 行63）。
 *
 * <p>compute-on-read over {@code t_warehouse_loss_flow}：每日损耗系统汇总 + 当日明细下钻。
 * 不建汇总表，与 Phase3 的 {@link ILossFlowService}（双写 hook）共用同一张 loss_flow 表。</p>
 *
 * @author djs
 * @since WMS-LOSS-OVERVIEW-001
 */
public interface ILossOverviewService {

    /**
     * 按日损耗汇总（行63 列表）。
     *
     * @param dateFrom 起始日期（含，可空）
     * @param dateTo   截止日期（含，可空）
     * @return 每日汇总（日期 + 当日 distinct 损耗产品数），按日期倒序
     */
    List<LossOverviewDailyVo> dailyOverview(Date dateFrom, Date dateTo);

    /**
     * 某日损耗明细（行63 详情弹窗）。
     *
     * @param date        统计自然日（yyyy-MM-dd，必传）
     * @param productName 产品名称模糊（可空）
     * @param lossType    损耗类型 djs_loss_type（可空）
     * @return 当日逐条损耗明细
     */
    List<LossOverviewDetailVo> dailyDetail(String date, String productName, String lossType);
}
