package org.dromara.djs.warehouse.inout.domain.bo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 果蔬入库工作台「确认入库」（V6 row282），落到毛菜处理采摘录入。 */
@Data
public class VegInSubmitBo {
    /** 所选采摘地块对应的种植记录。 */
    @NotNull(message = "请选择采摘地块")
    private Long plantingRecordId;
    /** 入库产品（作物产品配置之一；作物未配置产品时可空）。 */
    private Long productId;
    @NotNull(message = "请输入入库重量")
    @DecimalMin(value = "0.001", message = "入库重量必须大于 0")
    @Digits(integer = 9, fraction = 3, message = "入库重量最多三位小数")
    private BigDecimal weight;
    @NotEmpty(message = "请选择采摘班组")
    private List<Long> teamIds;
    @NotNull(message = "请选择绩效百分比")
    @Min(value = 1, message = "绩效百分比须为 1-100 的整数")
    @Max(value = 100, message = "绩效百分比须为 1-100 的整数")
    @JsonDeserialize(using = IntegerPercentDeserializer.class)
    private Integer perfPercent;
}
