package org.dromara.djs.breed.event.breeding.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 配种事件录入入参（BRD-EVENT-002 BREED）。
 *
 * <p>状态机 transition：HB / DN / LC / KH / FQ → PZ；非这些状态的母猪由
 * {@code PigStateMachine} 直接抛 {@code invalid_transition}。本 BO 不做源态校验。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
public class BreedingBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母猪 ID。 */
    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

    /** 配种日期 + 时间。 */
    @NotNull(message = "breeding.date.required")
    private LocalDateTime breedingDate;

    /**
     * 配种方式（字典 djs_mating_method）。
     * 当前字典值：1 / 2 等单字符或 AI / LQ / RJ 等短字母码均允许（≤ 16）。
     */
    @NotBlank(message = "breeding.type.required")
    @Pattern(regexp = "^[A-Za-z0-9]{1,16}$", message = "breeding.type.invalid")
    private String breedingType;

    /** 公猪耳号（breedingType=1 本场公猪时必填；2 精液产品时可空）。 */
    @Size(max = 32, message = "breeding.boar_ear.size")
    private String boarEarNo;

    /** 精液供应商（breedingType=2 时填）。 */
    @Size(max = 64, message = "breeding.supplier.size")
    private String semenSupplier;

    /** 精液批号（breedingType=2 时填）。 */
    @Size(max = 32, message = "breeding.batch.size")
    private String semenBatchNo;

    /** 凭证图片 OSS IDs 逗号分隔（mp 端配种现场照片，与 PigIntroBo 对齐）。 */
    @Size(max = 1024, message = "breeding.proof.size")
    private String proofOssIds;

    /** 备注。 */
    @Size(max = 500, message = "breeding.remark.size")
    private String remark;
}
