package org.dromara.djs.store.returns.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 仓库新增单位退回：仅原材料，直接录入确认量与记录丢弃量。 */
@Data
public class WarehouseReturnUnitBo {
    @NotNull private LocalDate returnDate;
    @NotBlank @Size(max = 64) private String returnUnit;
    @Valid @NotEmpty private List<Item> items;
    @Data public static class Item {
        @NotNull private Long productId;
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 3) private BigDecimal receivedQty;
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 3) private BigDecimal discardQty;
        private Long locationId;
        @Size(max = 500) private String warehouseRemark;
    }
}
