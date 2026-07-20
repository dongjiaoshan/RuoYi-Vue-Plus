package org.dromara.djs.breed.event.transfer.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 转移事件录入入参（BRD-EVENT-004 TRANSFER）。
 *
 * <p>状态机：NO_CHANGE_EVENTS，状态保持；{@code applyEventSideEffects} 写
 * barn_id / pen_id，并根据 payload.newPigType 切换 pig_type（仔猪→育肥）。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
public class TransferBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

    @NotNull(message = "transfer.date.required")
    private LocalDateTime transferDate;

    /** 新栋舍 ID（与 newBarnCode 二选一；admin 端可直传 id，mp 端建议传 newBarnCode）。 */
    private Long newBarnId;

    /** 新栋舍编码（mp 端工人输入；service 入口按 (tenant_id, barn_code) 查 barn.id）。 */
    private String newBarnCode;

    /** 新栏位 ID（可空，未细分到栏位时仅切栋舍；与 newPenCode 二选一）。 */
    private Long newPenId;

    /** 新栏位编码（mp 端工人输入；service 入口按 (tenant_id, barn_id, pen_code) 查 pen.id）。 */
    private String newPenCode;

    /** 转移原因（可选）。 */
    @Size(max = 64, message = "transfer.reason.size")
    private String transferReason;

    /** 转移人员 userId（EmployeePicker 选中，snowflake；可空，空则回落登录用户）。 */
    private String operator;

    @Size(max = 500, message = "transfer.remark.size")
    private String remark;
}
