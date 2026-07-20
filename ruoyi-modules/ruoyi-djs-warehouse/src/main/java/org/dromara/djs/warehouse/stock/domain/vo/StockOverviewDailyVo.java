package org.dromara.djs.warehouse.stock.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 库存总览每日汇总视图对象（WMS-STOCK-OVERVIEW-001，仓库-admin 行56）。
 *
 * <p>compute-on-read：直接 over {@code t_warehouse_location_stock}（当日有库存的产品）聚合，
 * 不建汇总表。每行 = 某自然日 + 当日库存非 0 的产品/商品 distinct 数量。明细在
 * {@link StockOverviewDetailVo}（按日下钻，回放 stock_flow 算期初/入/出/期末）。</p>
 *
 * @author djs
 * @since WMS-STOCK-OVERVIEW-001
 */
@Data
public class StockOverviewDailyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计日期（yyyy-MM-dd）。 */
    private String statDate;

    /** 当日库存非 0 的产品/商品 distinct 数量。 */
    private Integer productCount;
}
