package org.dromara.djs.warehouse.tare.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 去皮规格新增 / 编辑入参。可选位置前端多选传数组，service 落库转 CSV。
 *
 * @author djs
 * @since V7-R2
 */
@Data
public class TareSpecBo {

    private Long id;

    @NotBlank(message = "请填写规格名称")
    @Size(max = 64, message = "规格名称最多 64 个字")
    private String specName;

    @NotNull(message = "请填写去皮重量")
    @DecimalMin(value = "0.001", message = "去皮重量需大于 0")
    @Digits(integer = 9, fraction = 3, message = "去皮重量最多三位小数")
    private BigDecimal tareWeight;

    @NotNull(message = "请选择是否默认")
    private Integer isDefault;

    @NotEmpty(message = "请至少选择一个可选位置")
    private List<String> positions;

}
