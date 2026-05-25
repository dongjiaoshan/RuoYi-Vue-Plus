package org.dromara.djs.breed.event.castrate.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阉割事件录入入参（BRD-EVENT-004 CASTRATE）。
 *
 * <p>service 层提前校验 pig_sex='M'（早于状态机），抛 castrate.male_only。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
public class CastrateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

    @NotNull(message = "castrate.date.required")
    private LocalDateTime castrateDate;

    @Size(max = 500, message = "castrate.remark.size")
    private String remark;
}
