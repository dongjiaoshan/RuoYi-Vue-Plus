package org.dromara.djs.warehouse.inout.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 果蔬出库工作台「确认出库」（V6 row283）。 */
@Data
public class VegOutWorkbenchSubmitBo {
    @NotNull(message = "请选择果蔬产品")
    private Long productId;
    /** 所选地块卡背后的库存篮 id。 */
    @NotEmpty(message = "请选择出库地块")
    private List<Long> stockIds;
    @NotNull(message = "请输入出库重量")
    @DecimalMin(value = "0.001", message = "出库重量必须大于 0")
    @Digits(integer = 9, fraction = 3, message = "出库重量最多三位小数")
    private BigDecimal weight;
    /** warehouse = 仓库出库；feed = 猪养殖饲料（有机饲喂）。 */
    @NotBlank(message = "请选择去向")
    @Pattern(regexp = "warehouse|feed", message = "无效的去向")
    private String destination;
    /** 仓库出库去向（字典 djs_stock_out_dest），仅 destination=warehouse 时必填。 */
    @Size(max = 32, message = "出库去向过长")
    private String outDest;
}
