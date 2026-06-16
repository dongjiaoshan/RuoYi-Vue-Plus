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

    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

    /** 录入人员 userId（mp EmployeePicker role=breed_worker 选；空则 service 回落 LoginHelper.getUserId()）。 */
    private Long operatorId;

    @NotNull(message = "null_return.date.required")
    private LocalDateTime nullReturnDate;

    /** 异常类型：abort / return / idle。 */
    @NotBlank(message = "null_return.abnormal_type.required")
    @Pattern(regexp = "^(abort|return|idle)$", message = "null_return.abnormal_type.invalid")
    private String abnormalType;

    @Size(max = 500, message = "null_return.remark.size")
    private String remark;
}
