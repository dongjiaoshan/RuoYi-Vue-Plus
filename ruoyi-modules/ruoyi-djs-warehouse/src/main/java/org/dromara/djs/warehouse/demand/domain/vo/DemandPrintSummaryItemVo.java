package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/** 已确认需求汇总打印数据。 */
@Data
public class DemandPrintSummaryItemVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品名称；汇总以此为合并键。 */
    private String productName;
    /** 同名需求规格去重并列。 */
    private String productSpec;
    /** 同名且同计量单位的已确认需求量。 */
    private BigDecimal demandQuantity;
    /** 单位；kg/公斤统一为kg，白条为头。 */
    private String productUnit;
    /** 关联原材料名称，多个原材料去重并列。 */
    private String rawMaterialName;
    /** 产品类别，读取需求时用于白条单位换算。 */
    private String belongType;
}
