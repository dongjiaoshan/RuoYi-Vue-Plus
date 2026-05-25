package org.dromara.djs.breed.event.heat.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 查情事件入参（BRD-EVENT-002 OESTRUS）。
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
public class HeatBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "heat.pig_id.required")
    private Long pigId;

    @NotNull(message = "heat.date.required")
    private LocalDateTime heatDate;

    /** 查情结果（字典 djs_heat_result，service 不校验枚举具体值）。 */
    @NotBlank(message = "heat.result.required")
    @Pattern(regexp = "^[A-Za-z0-9]{1,16}$", message = "heat.result.invalid")
    private String heatResult;

    /** 是否确认妊娠（true → 触发 OESTRUS 状态机，PZ→PH）。 */
    @NotNull(message = "heat.confirmed.required")
    private Boolean isPregnantConfirmed;

    @Size(max = 500, message = "heat.remark.size")
    private String remark;
}
