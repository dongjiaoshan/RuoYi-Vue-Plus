package org.dromara.djs.warehouse.inout.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 果蔬库存篮查询行（V6 row283），一行 = 一个库存篮。 */
@Data
public class VegOutStockRow {
    private Long stockId;
    private Long productId;
    private String productName;
    private String productUnit;
    private Long plotId;
    private String plotCode;
    private String plotName;
    private String earNo;
    private Long locationId;
    private String locationName;
    private Integer thirdPhase;
    private BigDecimal stockWeight;
}
