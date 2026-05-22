package org.dromara.djs.breed.event.intro.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
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

    /** 供应商 ID（外部引种必填；内部引种可空）。 */
    private Long supplierId;

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

    /** 目标栋舍 ID。 */
    @NotNull(message = "intro.barn.required")
    private Long barnId;

    /** 目标栏位 ID。 */
    @NotNull(message = "intro.pen.required")
    private Long penId;

    /** 备注。 */
    @Size(max = 500, message = "intro.remark.size")
    private String remark;
}
