package org.dromara.djs.breed.event.intro.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单头引种入参（BRD-EVENT-001）。
 *
 * <p>校验流程（见 {@code PigIntroServiceImpl#introduce}）：</p>
 * <ol>
 *   <li>JSR-303 基础校验（required / size / pattern）</li>
 *   <li>外部引种额外校验：{@code supplierId + proofOssIds} 必填</li>
 *   <li>{@code IPigCoreService.createPig} 内部再校 earNo / pigSex / pigType</li>
 * </ol>
 *
 * @author djs
 * @since BRD-EVENT-001
 */
@Data
public class PigIntroBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 引种方式（字典 introduce_from：external / internal）。 */
    @NotBlank(message = "intro.type.required")
    @Pattern(regexp = "^(external|internal)$", message = "intro.type.invalid")
    private String introduceType;

    /** 引种日期。 */
    @NotNull(message = "intro.date.required")
    private LocalDate introduceDate;

    /**
     * 供应商 ID（外部引种必填；内部引种可空）。与 {@link #supplierCode} 二选一。
     * <p>admin 端（接 BRD-LIST-001 下拉后）传 id；mp 端 V1 用户手输 supplier_code，service 解析。</p>
     */
    private Long supplierId;

    /**
     * 供应商编码（mp 端用户可见的短码，如 "G0004"；service 层按 (tenant_id, supplier_code) 查 id）。
     * 与 {@link #supplierId} 二选一。
     */
    @Size(max = 32, message = "intro.supplier_code.size")
    private String supplierCode;

    /**
     * 凭证图 OSS ID 逗号分隔（外部引种必填）。
     * <p>由 mp {@code CameraUploadWithWatermark} 上传后得到 sysOssId，业务层用逗号串联。</p>
     */
    @Size(max = 1024, message = "intro.proof.size")
    private String proofOssIds;

    /** 性别（F 母 / M 公）。单头引种必填，由 service 透传给 createPig。 */
    @NotBlank(message = "pig.sex.required")
    @Pattern(regexp = "^[FM]$", message = "pig.sex.invalid")
    private String pigSex;

    /** 品种编码（关联 t_farm_breed_info）。 */
    private String pigBreedCode;

    /** 品系编码（关联 t_farm_breed_info）。 */
    private String pigStrainCode;

    /** 出生日期。 */
    private LocalDate birthDate;

    /**
     * 目标栋舍 ID。与 {@link #barnCode} 二选一必填（service 层先解析 code → id）。
     * <p>admin 端（接 BRD-LIST-001 下拉后）传 id；mp 端 V1 用户手输栋舍编码，service 解析。</p>
     */
    private Long barnId;

    /**
     * 目标栋舍编码（mp 端用户可见的短码，如 "1"；service 层按 (tenant_id, barn_code) 查 id）。
     * 与 {@link #barnId} 二选一必填。
     */
    @Size(max = 32, message = "intro.barn_code.size")
    private String barnCode;

    /**
     * 目标栏位 ID。与 {@link #penCode} 二选一必填。
     */
    private Long penId;

    /**
     * 目标栏位编码（在所属栋舍下 unique；需配合 barnId / barnCode 一起解析）。
     * 与 {@link #penId} 二选一必填。
     */
    @Size(max = 32, message = "intro.pen_code.size")
    private String penCode;

    /**
     * 引种人员（存 sys_user.user_id，与内部引种 {@link PigIntroInternalBo#getOperator()} 语义一致）。
     * <p>mp 端 SupplierPicker 同款 EmployeePicker 选人后回填 userId String；admin 端可空。</p>
     */
    @Size(max = 64, message = "intro.operator.size")
    private String operator;

    /** 引种体重 kg（外部引种登记，原型 86；mp 端 EntryForm input 录入，可空）。 */
    private BigDecimal introduceWeight;

    /** 备注。 */
    @Size(max = 500, message = "intro.remark.size")
    private String remark;
}
