package org.dromara.djs.breed.event.intro.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 批量引种入参（BRD-EVENT-001）。
 *
 * <p>批量引种约束：{@code pigSex} 统一（同一批同性别）；{@code pigCount} 决定生成 N 个连续耳号 +
 * N 头猪只。整批一个事务，任一头失败全回滚。</p>
 *
 * @author djs
 * @since BRD-EVENT-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PigIntroBatchBo extends PigIntroBo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批量引种头数（≥ 2；为 1 则应用 {@code POST /intro} 单头端点）。
     * 上限 200 是为了避免单事务过大触发 MySQL row lock / undo log 压力。
     */
    @NotNull(message = "intro.count.required")
    @Min(value = 2, message = "intro.count.batch_min")
    @Max(value = 200, message = "intro.count.batch_max")
    private Integer pigCount;

    /** 起始耳号（业务参考；编码生成器实际给每头分配，本字段仅写到引种业务表）。 */
    @NotBlank(message = "intro.start_ear_no.required")
    @Pattern(regexp = "^[A-Za-z0-9\\-]+$", message = "intro.start_ear_no.pattern")
    @Size(max = 32, message = "intro.start_ear_no.size")
    private String startEarNo;
}
