package org.dromara.djs.warehouse.inout.domain.bo;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CutWorkbenchSubmitBo {
    /** 未领用时传来源行；已领用时传分割记录，必须二选一。 */
    private Long inhouseId;
    private Long cutRecordId;
    @NotNull private Long productId;
    @NotNull @DecimalMin("0.001") @Digits(integer=9, fraction=3) private BigDecimal weight;
    @NotBlank @Pattern(regexp="fresh|frozen|outbound") private String destination;
    @Size(max=32) private String outDest;
    @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private String requestId;
}
