package org.dromara.djs.breed.event.weaning.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 断奶事件入参（BRD-EVENT-002 WEAN）。状态机 FM → DN。
 *
 * <p>OQ-11 fallback：V1 录入"母猪汇总断奶数 + 平均重"，不强制逐头称重。
 * 后续若客户要求逐头，单独走 BRD-EVENT-002.5 加 {@code t_farm_wean_weight} 明细表。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
public class WeaningBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "weaning.pig_id.required")
    private Long pigId;

    /** 关联分娩 ID（mp 端从近期分娩 picker 选）。 */
    @NotNull(message = "weaning.farrow_id.required")
    private Long farrowId;

    @NotNull(message = "weaning.date.required")
    private LocalDateTime weaningDate;

    /** 断奶时仔猪数（实际活仔数）。 */
    @NotNull(message = "weaning.count.required")
    @Min(value = 0, message = "weaning.count.invalid")
    private Integer weanedCount;

    /** 断奶总重 kg（OQ-11 fallback：母猪汇总，不逐头）。 */
    private BigDecimal weanedWeight;

    /** 断奶平均重 kg（若 weanedWeight + count 都给则 service 自动算）。 */
    private BigDecimal avgWeanedWeight;

    @Size(max = 500, message = "weaning.remark.size")
    private String remark;
}
