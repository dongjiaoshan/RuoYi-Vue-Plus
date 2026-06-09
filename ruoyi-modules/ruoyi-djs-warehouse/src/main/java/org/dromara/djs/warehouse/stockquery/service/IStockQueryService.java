package org.dromara.djs.warehouse.stockquery.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.stockquery.domain.query.StockQueryFlowQuery;
import org.dromara.djs.warehouse.stockquery.domain.query.StockQueryStockQuery;
import org.dromara.djs.warehouse.stockquery.domain.vo.StockQueryFlowVo;
import org.dromara.djs.warehouse.stockquery.domain.vo.StockQueryItemVo;
import org.dromara.djs.warehouse.stockquery.domain.vo.StockQueryStatVo;

import java.util.List;

/**
 * mp 库存查询 hub Service（FIX-WMS-MP-STOCKQUERY-001，原型图27/53/55/56）。
 *
 * <p>read-only 聚合：库存查询 / 出入库流水 / 退货统计 / 损耗统计 4 类查询复用现有表（{@code t_warehouse_stock}
 * / {@code t_warehouse_stock_flow}），盘点记录 tab 由 controller 直接调共享 {@code IStockCheckService.queryHeaderPage}
 * （传 checkStatus='completed'），不在本 service 重复实现。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-STOCKQUERY-001
 */
public interface IStockQueryService {

    /**
     * 库存查询分页（库位 + 产品名筛选；仅产品维度库存行）。
     */
    TableDataInfo<StockQueryItemVo> queryStockPage(StockQueryStockQuery query, PageQuery pageQuery);

    /**
     * 出入库流水分页（入 / 出方向由 controller 锁定 inoutType；产品名 + 日期区间筛选）。
     */
    TableDataInfo<StockQueryFlowVo> queryFlowPage(StockQueryFlowQuery query, PageQuery pageQuery);

    /**
     * 退货统计（按产品聚合，flow_type=return_in）。
     */
    List<StockQueryStatVo> queryReturnStat();

    /**
     * 损耗统计（按产品聚合，flow_type=loss）。
     */
    List<StockQueryStatVo> queryLossStat();

}
