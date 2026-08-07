package org.dromara.djs.plant.crop.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 作物关联产品配置入参 BO（V6 row16）。
 *
 * @author djs
 */
@Data
public class CropProductBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；新增时为空，修改时必填。 */
    private Long id;

    @NotNull(message = "作物不能为空")
    private Long cropId;

    @NotNull(message = "请选择关联产品")
    private Long productId;

    @PositiveOrZero(message = "作物绩效不能为负数")
    private BigDecimal perfPrice;
}
