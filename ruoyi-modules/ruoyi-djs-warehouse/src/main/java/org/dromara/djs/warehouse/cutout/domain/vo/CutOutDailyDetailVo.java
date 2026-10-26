package org.dromara.djs.warehouse.cutout.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.format.DateTimeFormat;
import cn.idev.excel.annotation.format.NumberFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 猪肉日分割出库统计「查看」明细行（V7-R6）。
 *
 * <p>列序 = 甲方原文序：产品编码 / 产品名称 / 出库量 / 单位 / 猪只耳号 / 出库时间。
 * FastExcel 按字段声明序出列，导出与抽屉表格逐列一致，挪字段顺序会让两边错位。</p>
 *
 * @author djs
 * @since V7-R6
 */
@Data
@ExcelIgnoreUnannotated
public class CutOutDailyDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品编码（{@code product_info.product_id} 业务码；档案缺失时 service 兜 "-"）。 */
    @ExcelProperty(value = "产品编码")
    private String productCode;

    /** 产品名称。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /** 出库量（kg，3 位小数）。 */
    @NumberFormat("0.000")
    @ExcelProperty(value = "出库量")
    private BigDecimal quantity;

    /** 单位（本报表统计的都是称重出库，恒 kg）。 */
    @ExcelProperty(value = "单位")
    private String unit;

    /** 猪只耳号（无耳号兜 "-"）。 */
    @ExcelProperty(value = "猪只耳号")
    private String earNo;

    /** 出库时间（出库操作时刻）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "出库时间")
    private Date outTime;
}
