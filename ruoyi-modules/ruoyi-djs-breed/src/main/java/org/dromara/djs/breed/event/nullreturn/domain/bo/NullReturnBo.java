package org.dromara.djs.breed.event.nullreturn.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 返空事件入参（BRD-EVENT-002 NULL_RETURN）。
 *
 * <p>BO 用对外友好的长字符串 {@code abnormalType}（abort / idle / return）；
 * service 层映射到 DB 字段值 {@code R/A/N}（CR-20260524-07），同时给状态机 payload
 * 透传原值，由 {@code PigStateMachine} 决定终态（LC/KH/FQ）。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
public class NullReturnBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "null_return.pig_id.required")
    private Long pigId;

    @NotNull(message = "null_return.date.required")
    private LocalDateTime nullReturnDate;

    /** 异常类型：abort / return / idle。 */
    @NotBlank(message = "null_return.abnormal_type.required")
    @Pattern(regexp = "^(abort|return|idle)$", message = "null_return.abnormal_type.invalid")
    private String abnormalType;

    /** 异常原因（可选自由文本）。 */
    @Size(max = 32, message = "null_return.reason.size")
    private String abnormalReason;

    @Size(max = 500, message = "null_return.remark.size")
    private String remark;
}
