package org.dromara.djs.warehouse.inout.domain.bo;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class BurnWorkbenchSubmitBo {
    @NotNull private Long barInfoId;
    @NotNull private Long productId;
    @NotNull @DecimalMin("0.001") @Digits(integer=9, fraction=3) private BigDecimal weight;
    /** 称重（秤读数 kg，可选；D-0147 ③）。与 tareWeight 要么都不传，要么都传且 称重 − 去皮 = weight（误差 ≤ 0.001kg）。 */
    private BigDecimal grossWeight;
    /** 去皮重量（kg，可选；不去皮传 0；D-0147 ③）。 */
    private BigDecimal tareWeight;
    @NotBlank @Pattern(regexp="warehouse|store|outbound") private String destination;
    private Long storeId;
    @Size(max=32) private String outDest;
    @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private String requestId;
}
