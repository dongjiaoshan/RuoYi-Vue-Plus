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
}
