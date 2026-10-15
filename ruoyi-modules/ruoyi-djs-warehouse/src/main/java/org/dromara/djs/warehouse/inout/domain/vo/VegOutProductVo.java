package org.dromara.djs.warehouse.inout.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 果蔬出库工作台产品卡（V6 row283）：库存里的果蔬产品，跨库位合计。 */
@Data
public class VegOutProductVo {
    private Long productId;
    private String productName;
    private String productUnit;
    /** 有库存的真实地块数（无地块 / 三期库存不计）。 */
    private Integer plotCount;
    /** 库存总量（全部库位合计）。 */
    private BigDecimal totalStock;
}
