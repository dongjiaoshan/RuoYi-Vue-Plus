package org.dromara.djs.warehouse.stock.service;

import org.dromara.djs.warehouse.stock.domain.vo.StockOverviewDailyVo;
import org.dromara.djs.warehouse.stock.domain.vo.StockOverviewDetailVo;

import java.util.Date;
import java.util.List;

/**
 * 库存总览聚合服务（WMS-STOCK-OVERVIEW-001，仓库-admin 行56）。
 *
 * <p>compute-on-read 回放 {@code t_warehouse_stock_flow}，不建汇总表（Kevin 拍板）。</p>
 *
 * @author djs
 * @since WMS-STOCK-OVERVIEW-001
 */
public interface IStockOverviewService {

    /**
     * 按自然日汇总库存总览（行56 列表）。
     *
     * @param dateFrom 起始日期（含，可空）
     * @param dateTo   截止日期（含，可空）
     * @return 每日库存总览（日期倒序）
     */
    List<StockOverviewDailyVo> queryDaily(Date dateFrom, Date dateTo);

    /**
     * 当日库存明细（行56 详情弹窗）：某自然日按 (产品, 库位) 的库存情况，
     * 含期初 / 入库 / 出库 / 损耗 / 饲喂 / 期末。
     *
     * @param date        统计自然日（必传，yyyy-MM-dd）
     * @param productName 产品名称模糊（可空）
     * @param locationId  库位 id 精确（可空）
     * @return 当日库存明细
     */
    List<StockOverviewDetailVo> queryDetail(String date, String productName, Long locationId);

}
