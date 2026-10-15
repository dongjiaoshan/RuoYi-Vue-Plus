package org.dromara.djs.warehouse.inout.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/** V6 row293 / D-0144：完成所选库存卡，只传身份，剩余量由事务锁定后读取。 */
@Data
public class VegOutFinishBo {
    @NotNull(message = "请选择果蔬产品")
    private Long productId;
    @NotEmpty(message = "请选择出库地块")
    private List<@NotNull(message = "库存篮不能为空") Long> stockIds;
}
