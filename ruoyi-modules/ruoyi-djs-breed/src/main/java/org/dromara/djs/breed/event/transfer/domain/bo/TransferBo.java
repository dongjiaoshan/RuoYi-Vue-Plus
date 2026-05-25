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

    @NotNull(message = "transfer.pig_id.required")
    private Long pigId;

    @NotNull(message = "transfer.date.required")
    private LocalDateTime transferDate;

    /** 新栋舍 ID。 */
    @NotNull(message = "transfer.new_barn.required")
    private Long newBarnId;

    /** 新栏位 ID（可空，未细分到栏位时仅切栋舍）。 */
    private Long newPenId;

    /** 转移原因（可选）。 */
    @Size(max = 64, message = "transfer.reason.size")
    private String transferReason;

    @Size(max = 500, message = "transfer.remark.size")
    private String remark;
}
