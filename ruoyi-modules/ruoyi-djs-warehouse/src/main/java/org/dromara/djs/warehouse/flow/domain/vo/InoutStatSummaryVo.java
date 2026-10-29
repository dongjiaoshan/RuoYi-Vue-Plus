package org.dromara.djs.warehouse.flow.domain.vo;

import lombok.Data;
import java.math.BigDecimal;

/** 当前筛选条件下全部出入库流水的汇总，不受分页影响。 */
@Data
public class InoutStatSummaryVo {
    private Integer productCount;
    /** 单位为 KG 的量，单位大小写及首尾空格归一。 */
    private BigDecimal weightQty;
    /** 非 KG 产品按原单位数值合计，不折算成重量。 */
    private BigDecimal nonWeightQty;
    private Integer flowTypeCount;
    /** 有效供应商主数据数量，无供应商不计数。 */
    private Integer supplierCount;
    /** 非空出库去向数量，未指定去向不计数。 */
    private Integer outDestCount;
}
