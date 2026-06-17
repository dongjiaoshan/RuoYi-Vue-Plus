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
 * <p>mp 端：传 pigId + measureDate + backfatThickness（背膘厚度 mm，mp 主录字段，前端必填）。
 * admin 端：可加 weight + backHeight。weight / backfatThickness 后端均可选（各端按需前端校验），
 * 不同时强制（邓博 2026-06-17 #35：mp 生长记录改录背膘厚度而非体重）。</p>
 *
 * @author djs
 * @since BRD-EVENT-005
 */
@Data
public class GrowthBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 猪只 ID。 */
    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

    /** 测量日期（默认今日）。 */
    @NotNull(message = "growth.measure_date.required")
    private LocalDate measureDate;

    /** 体重 kg（可选，admin 端录；> 0 且 ≤ 999.99 kg）。 */
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
