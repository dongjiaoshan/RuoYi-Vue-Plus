package org.dromara.djs.warehouse.burn.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 燎毛间产品入库重量调整入参（V6-R43）。
 *
 * @author djs
 * @since V6-R43
 */
@Data
public class BurnInhouseAdjustBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品入库行主键（= {@code t_warehouse_product_inhouse.id}）。
     */
    @NotNull(message = "入库记录 ID 不能为空")
    private Long id;

    /**
     * 调整后的产品入库重量 kg（> 0）。
     */
    @NotNull(message = "产品入库重量不能为空")
    @DecimalMin(value = "0.001", message = "产品入库重量必须大于 0")
    private BigDecimal productWeight;

}
