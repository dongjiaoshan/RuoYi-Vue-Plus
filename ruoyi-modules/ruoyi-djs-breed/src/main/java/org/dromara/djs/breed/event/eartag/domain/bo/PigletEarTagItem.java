package org.dromara.djs.breed.event.eartag.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 批量贴耳标 - 单头入参（BRD-EVENT-003）。
 *
 * <p>性别用 {@code F=母 / M=公}，对齐 DDL {@code piglet_sex CHAR(1)} +
 * 字典 {@code sys_user_sex} 命名。</p>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
@Data
public class PigletEarTagItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 仔猪性别（F=母 / M=公）。 */
    @NotBlank(message = "{pigletno.sex.required}")
    @Pattern(regexp = "F|M", message = "{pigletno.sex.invalid}")
    private String pigletSex;

    /** 出生重 kg（可选，> 0 时校验）。 */
    private BigDecimal birthWeight;

    /** 单头备注（可选）。 */
    private String remark;
}
