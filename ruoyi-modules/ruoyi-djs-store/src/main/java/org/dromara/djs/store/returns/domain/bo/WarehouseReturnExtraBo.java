package org.dromara.djs.store.returns.domain.bo;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** 以真实待处理退回行为锚，在同一退回单中补充清单产品。 */
@Data
public class WarehouseReturnExtraBo {
    @NotNull private Long anchorId;
    @NotEmpty @Size(max = 100) private List<@NotNull Long> productIds;
}
