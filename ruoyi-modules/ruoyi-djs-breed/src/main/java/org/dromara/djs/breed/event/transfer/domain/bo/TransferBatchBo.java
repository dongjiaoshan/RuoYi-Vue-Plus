package org.dromara.djs.breed.event.transfer.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量转移事件录入入参（BRD-FIX-MP-EVENT-LEAVE-IA-001 转移多选批量）。
 *
 * <p>原型 98：4 类猪 segment + 多选批量 → 选完填同一目标栋舍/栏位。mp 端拿到的是
 * earNo string 列表（snowflake pigId 精度不下发数值），后端逐头 resolve pigId 后
 * 复用单只 {@code recordTransfer}，每只都触发完整 side effect（barn/pen 切换 +
 * piglet→fattening 的 pig_type 切换），整批同一事务原子提交（任一失败全回滚）。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-EVENT-LEAVE-IA-001
 */
@Data
public class TransferBatchBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待转移猪只耳号列表（mp 端多选拿到，至少 1 头；snowflake 精度走 string，service 入口逐头 resolve pigId）。 */
    @NotEmpty(message = "transfer.batch.empty")
    private List<String> earNos;

    @NotNull(message = "transfer.date.required")
    private LocalDateTime transferDate;

    /** 目标栋舍 ID（与 newBarnCode 二选一；admin 端可直传 id）。 */
    private Long newBarnId;

    /** 目标栋舍编码（mp 端工人选择；service 按 (tenant_id, barn_code) 查 barn.id）。 */
    private String newBarnCode;

    /** 目标栏位 ID（可空；与 newPenCode 二选一）。 */
    private Long newPenId;

    /** 目标栏位编码（mp 端工人选择；可选）。 */
    private String newPenCode;

    /** 转移原因（可选，整批共用）。 */
    @Size(max = 64, message = "transfer.reason.size")
    private String transferReason;

    @Size(max = 500, message = "transfer.remark.size")
    private String remark;
}
