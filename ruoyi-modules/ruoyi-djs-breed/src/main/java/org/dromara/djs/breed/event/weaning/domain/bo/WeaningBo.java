package org.dromara.djs.breed.event.weaning.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 断奶事件入参（BRD-EVENT-002 WEAN）。状态机 FM → DN。
 *
 * <p>BRD-FIX-MP-EVENT-BREED-IA-001（Kevin 2026-05-29 拍板）：原型 91 逐头录重——
 * mp 端断奶仔猪逐头录体重，FE 汇总 weanedWeight + avgWeanedWeight，并把每头明细放
 * {@link #details}。service 主记录 + 明细同事务批量 INSERT。details 缺省（admin 端 / 老调用方）
 * 时退化为仅汇总录入，向后兼容。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
public class WeaningBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

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

    /**
     * 逐头录重明细（原型 91；mp 端每头一行序号 + 体重）。
     * 缺省 / 空 → 仅汇总录入（向后兼容）；非空时 service 同事务批量 INSERT t_farm_pig_weaning_detail。
     */
    @Valid
    private List<WeaningDetailBo> details;

    @Size(max = 500, message = "weaning.remark.size")
    private String remark;
}
