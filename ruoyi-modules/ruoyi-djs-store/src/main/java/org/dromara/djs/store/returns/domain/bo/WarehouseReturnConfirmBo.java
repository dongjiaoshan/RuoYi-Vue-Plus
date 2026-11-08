package org.dromara.djs.store.returns.domain.bo;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

/** 仓库退回处理：实际入库量与仅记录的丢弃量分别填写，均按原材料单位。 */
@Data
public class WarehouseReturnConfirmBo {
    @NotNull private Long id;
    @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 3)
    private BigDecimal receivedQty;
    @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 3)
    private BigDecimal discardQty;
    private Long locationId;
    @Size(max = 500) private String warehouseRemark;
}
