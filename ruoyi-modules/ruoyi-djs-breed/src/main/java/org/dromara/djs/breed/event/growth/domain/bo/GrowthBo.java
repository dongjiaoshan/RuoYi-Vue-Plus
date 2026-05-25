package org.dromara.djs.breed.event.growth.domain.bo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生长记录录入入参（BRD-EVENT-005 GROWTH）。
 *
 * <p>mp 端：仅传 pigId + measureDate + weight（+ 可选 photoOssIds / remark）。
 * admin 端：可加 backfatThickness + backHeight。</p>
 *
 * @author djs
 * @since BRD-EVENT-005
 */
@Data
public class GrowthBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 猪只 ID。 */
    @NotNull(message = "growth.pig_id.required")
    private Long pigId;

    /** 测量日期（默认今日）。 */
    @NotNull(message = "growth.measure_date.required")
    private LocalDate measureDate;

    /** 体重 kg（必填，> 0 且 ≤ 999.99 kg）。 */
    @NotNull(message = "growth.weight.required")
    @DecimalMin(value = "0.01", message = "growth.weight.min")
    @DecimalMax(value = "999.99", message = "growth.weight.max")
    private BigDecimal weight;

    /** 背膘厚 mm（可选，admin 端录）。 */
    @DecimalMin(value = "0.00", message = "growth.backfat.min")
    @DecimalMax(value = "999.99", message = "growth.backfat.max")
    private BigDecimal backfatThickness;

    /** 背高 cm（可选，admin 端录）。 */
    @DecimalMin(value = "0.00", message = "growth.back_height.min")
    @DecimalMax(value = "999.99", message = "growth.back_height.max")
    private BigDecimal backHeight;

    /** 测量照片 OSS IDs（逗号分隔；bizType=grow_photo）。 */
    @Size(max = 500, message = "growth.photo.size")
    private String photoOssIds;

    /** 备注。 */
    @Size(max = 500, message = "growth.remark.size")
    private String remark;
}
