package org.dromara.djs.breed.event.eliminate.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 淘汰事件录入入参（BRD-EVENT-004 ELIMINATE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
public class EliminateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

    @NotNull(message = "eliminate.date.required")
    private LocalDateTime cullingDate;

    /** 淘汰记录人 sys_user.user_id（mp EmployeePicker 所选；必填）。 */
    @NotNull(message = "eliminate.recorder.required")
    private Long cullingRecorderId;

    /** 淘汰原因（字典 eliminate_reason）。 */
    @NotBlank(message = "eliminate.reason.required")
    @Size(max = 32, message = "eliminate.reason.size")
    private String cullingReason;

    /** 淘汰去向（字典 eliminate_dest）。V1 必填（Kevin 2026-05-29 拍板：追溯/账目须知去向）。 */
    @NotBlank(message = "eliminate.dest.required")
    @Size(max = 32, message = "eliminate.dest.size")
    private String cullingDest;

    /** 淘汰体重 kg（row140：所有数据必填）。 */
    @NotNull(message = "eliminate.weight.required")
    private BigDecimal cullingWeight;

    /** 淘汰照片 OSS IDs（强制至少 1 张）。 */
    @NotBlank(message = "eliminate.photo.required")
    @Size(max = 1024, message = "eliminate.photo.size")
    private String ossIds;

    /** 备注（可选，mp 端已移除备注输入框；admin 端可填，对齐同域 DieBo.remark 写法）。 */
    @Size(max = 500, message = "eliminate.remark.size")
    private String remark;
}
