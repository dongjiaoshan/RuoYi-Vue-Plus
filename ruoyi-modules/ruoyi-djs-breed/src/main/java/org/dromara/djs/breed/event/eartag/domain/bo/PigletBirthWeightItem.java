package org.dromara.djs.breed.event.eartag.domain.bo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 出生重订正 - 单头入参（V6 行243）。
 *
 * <p>按<b>仔猪耳号</b>定位已建档的那头仔猪，只改出生重；耳号本身不可改、不新建号。</p>
 *
 * @author djs
 * @since V6-R243
 */
@Data
public class PigletBirthWeightItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 仔猪耳号（必须属于本窝，否则整单拒绝）。 */
    @NotBlank(message = "{pigletno.ear_no.required}")
    private String pigletEarNo;

    /**
     * 出生重 kg（必填、正数，上限 9999.99）。
     *
     * <p>上限必须卡在这一层：落库列是 {@code DECIMAL(6,2)}，MySQL 又是 STRICT_TRANS_TABLES，
     * 超范围会在 INSERT 时抛出去变成「发生未知异常，请联系管理员」——
     * 负数和 0 有正经提示、写大了却只给一句看不懂的话，是最难排查的那种。</p>
     */
    @NotNull(message = "{pigletno.birth_weight.required}")
    @DecimalMin(value = "0.01", message = "{pigletno.birth_weight.invalid}")
    @DecimalMax(value = "9999.99", message = "{pigletno.birth_weight.too_large}")
    private BigDecimal birthWeight;
}
