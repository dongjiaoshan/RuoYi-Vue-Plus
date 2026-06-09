package org.dromara.djs.warehouse.stockquery.domain.query;

import lombok.Data;

/**
 * mp 库存查询入参（FIX-WMS-MP-STOCKQUERY-001 独占）。
 *
 * <p>独立于 admin {@code org.dromara.djs.warehouse.stock.domain.query.LocationStockQuery}，仅暴露 mp
 * 「库存查询」tab 需要的两个筛选维度（原型图27）：库位 + 产品名称。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-STOCKQUERY-001
 */
@Data
public class StockQueryStockQuery {

    /**
     * 库位 ID（精确；跨层 snowflake string，前端 query 传字符串）。
     */
    private Long locationId;

    /**
     * 产品名称（模糊）。
     */
    private String productName;

}
