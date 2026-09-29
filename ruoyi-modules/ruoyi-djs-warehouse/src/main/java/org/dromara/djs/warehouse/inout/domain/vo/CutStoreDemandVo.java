package org.dromara.djs.warehouse.inout.domain.vo;

import lombok.Data;
import java.math.BigDecimal;

/** 当天原材料外售需求。productId 是生产产品 ID，原材料 ID 由查询参数指定。 */
@Data
public class CutStoreDemandVo {
    private Long storeId;
    private String storeName;
    private Long productId;
    private String productName;
    private String productUnit;
    private BigDecimal demandQuantity;
    /** KG 产品按该门店该成品当天合计剩余需求校验；非 KG 按每份计量规则校验。 */
    private BigDecimal minimumWeight;
    private BigDecimal measureWeight;
}
