package org.dromara.djs.warehouse.check.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 盘点单 header 聚合 VO（WMS-STOCK-001 admin 列表）。
 *
 * <p>盘点单维度（一个 {@code checkId} 一行），由 service 聚合 header 行 + 各 line 行：</p>
 * <ul>
 *   <li>header 字段：{@code checkId / locationId / checkDate / checkStatus}</li>
 *   <li>line 聚合：{@code lineCount}（明细数）+ {@code diffSum}（盈亏计 = SUM(diff_stock)）</li>
 * </ul>
 *
 * @author djs
 * @since WMS-STOCK-001
 */
@Data
@ExcelIgnoreUnannotated
public class StockCheckHeaderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * header 行主键。
     */
    @ExcelProperty(value = "盘点单ID")
    private Long id;

    @ExcelProperty(value = "盘点单号")
    private String checkId;

    private Long locationId;

    /**
     * 库位名称（service 层 JOIN 回填）。
     */
    @ExcelProperty(value = "库位")
    private String locationName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "盘点日期")
    private Date checkDate;

    @ExcelProperty(value = "状态")
    private String checkStatus;

    /**
     * 明细行数（service 聚合）。
     */
    @ExcelProperty(value = "明细数")
    private Integer lineCount;

    /**
     * 盈亏计 = SUM(diff_stock)（service 聚合；&gt;0 净盘盈 / &lt;0 净盘亏）。
     */
    @ExcelProperty(value = "盈亏计")
    private BigDecimal diffSum;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
