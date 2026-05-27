package org.dromara.djs.warehouse.product.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.warehouse.product.domain.GiftBox;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 礼盒组件视图对象（WMS-MD-002）。
 *
 * <p>{@code componentProductName} 由 service 在拉详情时一次性 batch JOIN 注入
 * （避免列表场景 N+1）。</p>
 *
 * @author djs
 * @since WMS-MD-002
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = GiftBox.class)
public class GiftBoxVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "礼盒主体 ID")
    private Long boxProductId;

    @ExcelProperty(value = "组件 SKU ID")
    private Long componentProductId;

    /**
     * 组件 SKU 名称（service 详情接口反查注入；列表场景不注入）。
     */
    @ExcelProperty(value = "组件名称")
    private String componentProductName;

    @ExcelProperty(value = "数量")
    private BigDecimal componentCount;

    @ExcelProperty(value = "单位")
    private String componentUnit;

    @ExcelProperty(value = "排序")
    private Integer componentSort;

}
