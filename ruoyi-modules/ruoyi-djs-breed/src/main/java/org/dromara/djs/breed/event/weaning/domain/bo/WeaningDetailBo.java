package org.dromara.djs.breed.event.weaning.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 断奶逐头录重明细入参（BRD-FIX-MP-EVENT-BREED-IA-001）。
 *
 * <p>mp 端「逐头录重」子区每行一条：序号 + 体重（耳号可选）。
 * 主记录汇总 weanedWeight / avgWeanedWeight 由 FE 算后随 {@link WeaningBo} 一并下发。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-EVENT-BREED-IA-001
 */
@Data
public class WeaningDetailBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 仔猪序号（同一断奶记录内从 1 起；缺省由 service 按下发顺序补）。 */
    private Integer pigletSeq;

    /** 仔猪耳号（可选）。 */
    @Size(max = 32, message = "{weaning.detail.ear_no.size}")
    private String earNo;

    /** 断奶体重 kg（必填，> 0）。 */
    @NotNull(message = "{weaning.detail.weight.required}")
    @Positive(message = "{weaning.detail.weight.invalid}")
    private BigDecimal weight;
}
