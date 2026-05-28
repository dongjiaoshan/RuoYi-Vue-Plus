package org.dromara.djs.plant.organic.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.plant.organic.domain.CropOrganic;

import java.time.LocalDate;

/**
 * 果蔬有机证书入参 BO（PLT-MD-003）。
 *
 * @author djs
 * @since PLT-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = CropOrganic.class, reverseConvertGenerate = false)
public class CropOrganicBo extends BaseEntity {

    /** 证书 ID（编辑时必填）。 */
    private Long id;

    /** 证书编号（新增时必填，编辑时后端强制锁回旧值）。 */
    @NotBlank(message = "{plant.organic.crop_no.required}", groups = OnCreate.class)
    @Size(max = 64, message = "{plant.organic.crop_no.size}")
    private String cropCertNo;

    /** 颁发单位。 */
    @NotBlank(message = "{plant.organic.company.required}")
    @Size(max = 128, message = "{plant.organic.company.size}")
    private String cropCertCompany;

    /** 证书有效期到期日。 */
    @NotNull(message = "{plant.organic.valid.required}")
    private LocalDate cropCertValid;

    /** 关联作物 ID。 */
    @NotNull(message = "{plant.organic.crop_required}")
    private Long cropId;

    /** 缩略图 OSS ossId。 */
    @Size(max = 512, message = "{plant.organic.image.size}")
    private String cropImagePreview;

    /** 原图 OSS ossIds 逗号分隔。 */
    @Size(max = 2048, message = "{plant.organic.image.size}")
    private String cropImageUrl;

    public interface OnCreate {
    }
}
