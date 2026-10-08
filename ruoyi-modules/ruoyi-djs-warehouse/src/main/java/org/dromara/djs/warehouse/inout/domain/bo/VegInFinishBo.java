package org.dromara.djs.warehouse.inout.domain.bo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 果蔬入库工作台「处理完成」（V6 row282）= 小程序「地块是否称重完成」按 0 kg 收口。 */
@Data
public class VegInFinishBo {
    @NotNull(message = "请选择采摘地块")
    private Long plantingRecordId;
    @NotEmpty(message = "请选择采摘班组")
    private List<Long> teamIds;
    @NotNull(message = "请选择绩效百分比")
    @Min(value = 1, message = "绩效百分比须为 1-100 的整数")
    @Max(value = 100, message = "绩效百分比须为 1-100 的整数")
    @JsonDeserialize(using = IntegerPercentDeserializer.class)
    private Integer perfPercent;
}
