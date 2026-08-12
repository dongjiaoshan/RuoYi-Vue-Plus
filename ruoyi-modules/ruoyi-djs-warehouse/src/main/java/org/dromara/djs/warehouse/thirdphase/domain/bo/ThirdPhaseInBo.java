package org.dromara.djs.warehouse.thirdphase.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 三期作物入库提交入参（V6 row88 新增入库弹框）。
 *
 * @author djs
 * @since V6-R88
 */
@Data
public class ThirdPhaseInBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 入库产品 id（仅果蔬类原材料，service 二次守门）。 */
    @NotNull(message = "请选择入库产品")
    private Long productId;

    /**
     * 入库量（kg，最多 3 位小数、必须大于 0）。
     *
     * <p>{@code integer=9} 与 DDL {@code DECIMAL(12,3)} 对齐（12-3=9 位整数），
     * 超长在这里就 400 掉，不留给 DB 报「Out of range」。</p>
     */
    @NotNull(message = "请填写入库量")
    @DecimalMin(value = "0.001", message = "入库量必须大于 0")
    @Digits(integer = 9, fraction = 3, message = "入库量最多 3 位小数")
    private BigDecimal stockNum;

    /** 入库库位 id（仅毛菜保鲜室 L0006，service 二次守门）。 */
    @NotNull(message = "请选择入库库位")
    private Long locationId;

    /** 备注（可空）。 */
    private String remark;
}
