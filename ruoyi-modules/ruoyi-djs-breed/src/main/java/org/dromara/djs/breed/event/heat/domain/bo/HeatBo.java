package org.dromara.djs.breed.event.heat.domain.bo;

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

    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

    @NotNull(message = "{heat.date.required}")
    private LocalDateTime heatDate;

    /**
     * 查情结果（字典 djs_heat_result）。602-5 后 mp 查情页不再录入此字段（按客户口径查情只记发情情况 +
     * 不配种原因）；字段保留可空以兼容 admin 端历史录入与记录回看，service 不校验枚举具体值。
     */
    @Pattern(regexp = "^[A-Za-z0-9]{1,16}$", message = "{heat.result.invalid}")
    private String heatResult;

    /**
     * 是否确认妊娠（602-5 后 mp 不再录入；保留可空兼容 admin。OESTRUS 方案 A：true 时仅落审计记录，
     * 状态保持 PZ，不切配怀态，见 ADR-0010）。null 视同 false。
     */
    private Boolean isPregnantConfirmed;

    /** 发情不配种原因描述（602-5 新增，mp 查情页 textarea；落库复用 remark 列展示）。 */
    @Size(max = 500, message = "{heat.remark.size}")
    private String remark;

    /** 录入人 userId（snowflake string；mp EmployeePicker 选；空则后端回落登录态 LoginHelper.getUserId()）。 */
    private String operator;
}
