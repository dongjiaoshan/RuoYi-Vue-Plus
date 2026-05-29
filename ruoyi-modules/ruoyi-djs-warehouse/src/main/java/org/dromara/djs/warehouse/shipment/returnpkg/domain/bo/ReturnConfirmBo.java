package org.dromara.djs.warehouse.shipment.returnpkg.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * admin 退货确认 BO（{@code POST /djs/warehouse/return/{id}/confirm}）。
 *
 * <p>仅 {@code store_to_warehouse} 方向触发 stock_flow 联动；其他方向更新表字段但不联动。</p>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Data
public class ReturnConfirmBo {

    @NotNull(message = "确认重量不能为空")
    @Positive(message = "确认重量必须大于 0")
    private BigDecimal confirmWeight;

    /** 备注。 */
    private String remark;
}
