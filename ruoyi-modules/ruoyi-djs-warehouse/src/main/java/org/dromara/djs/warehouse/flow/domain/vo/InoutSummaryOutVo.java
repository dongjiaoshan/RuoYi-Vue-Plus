package org.dromara.djs.warehouse.flow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 出库汇总行（V6-R156）：按 <b>产品 × 出库去向</b> 两维聚合当月出库量。
 *
 * <p>列序 = 甲方 row156 第 3 点原序，导出与列表逐列一致（第 4 点）。</p>
 *
 * @author djs
 * @since V6-R156
 */
@Data
@ExcelIgnoreUnannotated
public class InoutSummaryOutVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品编码（{@code product_info.product_id} 业务码，如 P0001 / Y00099）。
     *
     * <p>甲方要它当列表第一列，即这张表的身份列，所以它也是聚合的分组键之一：
     * 名称 / 类型 / 规格 / 单位全同的重复产品档案各占一行、量各归各，不再合并成一行。</p>
     *
     * <p><b>字段声明位置必须在最前</b>：FastExcel 按字段声明序出列，挪到后面 Excel 的列序就与页面不一致。</p>
     */
    @ExcelProperty(value = "产品编码")
    private String productCode;

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
