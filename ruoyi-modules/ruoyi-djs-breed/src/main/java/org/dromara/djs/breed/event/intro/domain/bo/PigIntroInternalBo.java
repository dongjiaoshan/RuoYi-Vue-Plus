package org.dromara.djs.breed.event.intro.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 内部引种入参（BRD-FIX-MP-INTRO-001）。
 *
 * <p>语义（Kevin 2026-05-29 旁路 #4 拍板）：内部引种 = 登记一头**已存在**的猪进引种台账，
 * **不新建猪**。只写 {@code t_farm_pig_introduce}（introduce_type=internal + pig_id 关联），
 * 不调 {@code createPig}、不触发状态机。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-INTRO-001
 */
@Data
public class PigIntroInternalBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 已存在猪只 ID（mp 端查耳号 auto-fill 后回填，snowflake string；后端 Long）。 */
    @NotNull(message = "intro.internal.pig_required")
    private Long pigId;

    /** 引种日期。 */
    @NotNull(message = "intro.date.required")
    private LocalDate introduceDate;

    /** 引种体重 kg（可选，原型 86「引种猪只体重」）。 */
    private BigDecimal introduceWeight;

    /** 引种人员（V1 自由文本，原型 86「引种人员」）。 */
    private String operator;
}
