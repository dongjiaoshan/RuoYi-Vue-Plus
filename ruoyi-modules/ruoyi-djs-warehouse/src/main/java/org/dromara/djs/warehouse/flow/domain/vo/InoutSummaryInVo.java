package org.dromara.djs.warehouse.flow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 入库汇总行（V6-R155）：按 <b>产品 × 入库方式 × 供应商</b> 三维聚合当月入库量。
 *
 * <p>列序 = 甲方 row155 第 3 点原序，导出与列表逐列一致（第 4 点）。</p>
 *
 * <p>字典 label 在 service 端翻译成 {@code *Name} 展示字段（单通道）：这样「无供应商」这类
 * 非字典兜底值只写一次，Excel 与页面必然一致；不走 dict-tag + ExcelDictFormat 双通道。</p>
 *
 * @author djs
 * @since V6-R155
 */
@Data
@ExcelIgnoreUnannotated
public class InoutSummaryInVo implements Serializable {

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

    /** 规格（product_info.product_spec，空时 service 兜 "-"）。 */
    @ExcelProperty(value = "规格")
    private String productSpec;

    /** 入库方式（djs_flow_type 翻译后）。 */
    @ExcelProperty(value = "入库方式")
    private String inModeName;

    /** 入库量（Σ change_quantity）。 */
    @ExcelProperty(value = "入库量")
    private BigDecimal inboundQty;

    /** 单位。 */
    @ExcelProperty(value = "单位")
    private String productUnit;

    /** 供应商（空时 service 兜「无供应商」—— 甲方「供应商为空的统计到一起」的落点显示）。 */
    @ExcelProperty(value = "供应商")
    private String supplierName;

    /** 产品类型原始值（mapper 出，service 翻译用，不导出）。 */
    private Integer productType;

    /** 入库方式原始值（mapper 出，service 翻译用，不导出）。 */
    private String flowType;
}
