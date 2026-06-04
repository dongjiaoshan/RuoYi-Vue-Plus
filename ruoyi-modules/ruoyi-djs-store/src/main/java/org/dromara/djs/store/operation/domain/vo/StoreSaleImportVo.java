package org.dromara.djs.store.operation.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 门店销售流水 Excel 导入 VO（STR-OP-001）。
 *
 * <p>导入列：产品名称 / 销售日期 / 销售数量 / 销售总额。{@code productId} + {@code saleUnit}
 * 由后端按 {@code productName} + storeId 反查关联表回填；查不到的行记入失败统计（不静默吞）。
 * {@code source} 固定 excel_import 由 listener 写入。</p>
 *
 * <p>导入不允许 {@code @Accessors(chain=true)}（FastExcel 找不到 set 方法）。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Data
@NoArgsConstructor
public class StoreSaleImportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品名称（后端按 name + storeId 反查 product_id / sale_unit）。
     */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /**
     * 销售日期。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "销售日期")
    private Date saleDate;

    /**
     * 销售数量。
     */
    @ExcelProperty(value = "销售数量")
    private BigDecimal saleQty;

    /**
     * 销售总额（元）。
     */
    @ExcelProperty(value = "销售总额")
    private BigDecimal saleAmount;

}
