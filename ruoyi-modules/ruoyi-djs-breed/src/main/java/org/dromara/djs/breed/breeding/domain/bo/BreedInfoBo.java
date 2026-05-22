package org.dromara.djs.breed.breeding.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.breed.breeding.domain.BreedInfo;

/**
 * 育种信息（品种/品系）入参 BO（BRD-MD-001）。
 *
 * @author djs
 * @since BRD-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BreedInfo.class, reverseConvertGenerate = false)
public class BreedInfoBo extends BaseEntity {

    /**
     * 主键（编辑时必填）。
     */
    private Long id;

    /**
     * 类型（1=品种 / 2=品系）。
     */
    @NotNull(message = "类型不能为空")
    private Integer breedStrain;

    /**
     * 品种/品系编码（字母数字组合，最长 32）。
     */
    @NotBlank(message = "品种/品系编码不能为空")
    @Size(max = 32, message = "品种/品系编码长度不能超过 {max} 个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "品种/品系编码仅允许字母、数字、下划线、连字符")
    private String breedStrainCode;

    /**
     * 品种/品系名称。
     */
    @NotBlank(message = "品种/品系名称不能为空")
    @Size(max = 64, message = "品种/品系名称长度不能超过 {max} 个字符")
    private String breedStrainName;

    /**
     * 父级编码（品系归属品种时填）。
     */
    @Size(max = 32, message = "父级编码长度不能超过 {max} 个字符")
    private String parentCode;

    /**
     * 描述。
     */
    @Size(max = 255, message = "描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
