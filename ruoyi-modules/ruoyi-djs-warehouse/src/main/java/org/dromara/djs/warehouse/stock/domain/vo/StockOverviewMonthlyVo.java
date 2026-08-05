package org.dromara.djs.warehouse.stock.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 库存月汇总列表行（admin row190）。
 *
 * <p>与日汇总一样 compute-on-read（回放 {@code t_warehouse_stock_flow}），不建汇总表、不加跑批。</p>
 *
 * @author djs
 */
@Data
@ExcelIgnoreUnannotated
public class StockOverviewMonthlyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计月份（yyyy-MM）。 */
    @ExcelProperty(value = "月份")
    private String statMonth;

    /** 当月涉及的产品数（去重）。 */
    @ExcelProperty(value = "汇总产品数量")
    private Integer productCount;
}
