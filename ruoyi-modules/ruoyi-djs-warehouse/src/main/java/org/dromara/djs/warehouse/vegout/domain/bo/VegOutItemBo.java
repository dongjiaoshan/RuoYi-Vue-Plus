package org.dromara.djs.warehouse.vegout.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 毛菜间出库单项（admin row185/row187）：一条库存行出多少。
 *
 * @author djs
 */
@Data
public class VegOutItemBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 库存行 id（t_warehouse_location_stock.id）。按行出库，避免前端透传可篡改的 location/product。 */
    @NotNull(message = "库存行 id 不能为空")
    private Long stockId;

    /** 出库量（kg，> 0）。 */
    @NotNull(message = "出库量不能为空")
    @DecimalMin(value = "0.001", message = "出库量必须大于 0")
    private BigDecimal quantity;

    /**
     * 出库销售单价（row194）。前端默认带出产品 {@code sale_price}，用户可改；
     * 为空时 service 回落产品主数据价格。落库为流水行上的快照，改产品价格不影响历史单。
     */
    private BigDecimal outUnitPrice;
}
