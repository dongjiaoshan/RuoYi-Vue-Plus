package org.dromara.djs.warehouse.flow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 出库统计行（V6-R167）：按 <b>产品 × 出库去向</b> 两维聚合区间内出库量。
 *
 * <p>列序 = 甲方 row167 第 4 点原序，导出与列表逐列一致（第 5 点）。</p>
 *
 * @author djs
 * @since V6-R167
 */
@Data
@ExcelIgnoreUnannotated
public class InoutStatOutVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品名称。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /** 产品类型（djs_product_type 翻译后）。 */
    @ExcelProperty(value = "产品类型")
    private String productTypeName;

    /** 规格（空时 service 兜 "-"）。 */
    @ExcelProperty(value = "规格")
    private String productSpec;

    /** 出库去向（djs_stock_out_dest 翻译后；空 / 字典未命中兜「未指定」）。 */
    @ExcelProperty(value = "出库去向")
    private String outDestName;

    /** 出库量（Σ change_quantity）。 */
    @ExcelProperty(value = "出库量")
    private BigDecimal outboundQty;

    /** 单位。 */
    @ExcelProperty(value = "单位")
    private String productUnit;

    /** 产品类型原始值（mapper 出，service 翻译用，不导出）。 */
    private Integer productType;

    /** 出库去向原始值（mapper 出，service 翻译用，不导出）。 */
    private String stockOutDest;
}
