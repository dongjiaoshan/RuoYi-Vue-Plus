package org.dromara.djs.warehouse.demand.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 需求量调整入参（V6-R140「需求调整管理」）。
 *
 * @author djs
 * @since V6-R140
 */
@Data
public class DemandAdjustBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 调整后需求量（必填，必须大于 0）。
     *
     * <p>0 不走本端点：清零等于删单，删单有自己的状态机路径（{@code deleteWithValidByIds}），
     * 不在调整里顺手做掉。</p>
     */
    @NotNull(message = "{demand.adjust.quantity_required}")
    @DecimalMin(value = "0", inclusive = false, message = "{demand.adjust.quantity_positive}")
    // @Digits 对齐库表 demand_quantity DECIMAL(12,3)（与 StoreDemandQuantityBo 同字段同口径）：
    // 不加的话 4 位小数会被 MySQL **静默四舍五入**（1.2345 落成 1.235，用户不知情，留痕行记的也是舍入后的值），
    // 而整数部分超 12 位会漏成通用 500「发生未知异常」。
    @Digits(integer = 9, fraction = 3, message = "{demand.adjust.quantity_scale}")
    private BigDecimal demandQuantity;

    /**
     * 调整备注（选填，与需求量一起落进留痕表）。
     */
    @Size(max = 500, message = "{demand.adjust.remark_too_long}")
    private String adjustRemark;
}
