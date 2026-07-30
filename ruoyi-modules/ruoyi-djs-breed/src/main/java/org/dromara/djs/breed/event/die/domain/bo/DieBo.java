package org.dromara.djs.breed.event.die.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 死亡事件录入入参（BRD-EVENT-004 DIE）。
 *
 * <p>状态机 transition：任何非 END from → END；状态机内部不做 from 校验，仅 from.isTerminal()
 * 直接拒绝。本 BO 在 service 层强制要求至少 1 张多角度凭证照片（dead_photo）。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
public class DieBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 猪只 ID。 */
    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

    /** 死亡日期 + 时间。 */
    @NotNull(message = "{die.date.required}")
    private LocalDateTime deathDate;

    /** 死亡分类（字典 death_type：normal/abnormal）。 */
    @NotBlank(message = "{die.kind.required}")
    @Size(max = 16, message = "{die.kind.size}")
    private String deathKind;

    /** 死亡原因（字典 death_reason）。V1 必填（mp 死亡录入页所有字段必填）。 */
    @NotBlank(message = "{die.reason.required}")
    @Size(max = 32, message = "{die.reason.size}")
    private String deathReason;

    /** 死亡去向（字典 death_dest）。V1 必填（Kevin 2026-05-29 拍板：追溯/账目须知去向）。 */
    @NotBlank(message = "{die.dest.required}")
    @Size(max = 32, message = "{die.dest.size}")
    private String deathDest;

    /** 死亡重量 kg（仔猪 / 育肥可填）。V1 必填（mp 死亡录入页所有字段必填）。 */
    @NotNull(message = "{die.weight.required}")
    private BigDecimal deathWeight;

    /** 死亡记录人员（sys_user.user_id；EmployeePicker 选中后回填）。V1 必填。 */
    @NotNull(message = "{die.recorder.required}")
    private Long recorderId;

    /**
     * 死亡照片 OSS IDs（逗号分隔）。
     * 强制至少 1 张（多角度建议 ≥ 2）。
     */
    @NotBlank(message = "{die.photo.required}")
    @Size(max = 1024, message = "{die.photo.size}")
    private String ossIds;

    /** 备注。 */
    @Size(max = 500, message = "{die.remark.size}")
    private String remark;
}
