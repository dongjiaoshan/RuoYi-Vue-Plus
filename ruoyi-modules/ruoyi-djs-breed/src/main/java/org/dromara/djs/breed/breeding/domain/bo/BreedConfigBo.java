package org.dromara.djs.breed.breeding.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.breed.breeding.domain.BreedConfig;

/**
 * 育种配置（配种关系）入参 BO（BRD-MD-001）。
 *
 * @author djs
 * @since BRD-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BreedConfig.class, reverseConvertGenerate = false)
public class BreedConfigBo extends BaseEntity {

    /**
     * 主键（编辑时必填）。
     */
    private Long id;

    /**
     * 类型（1=品种配种 / 2=品系配种）。
     */
    @NotNull(message = "类型不能为空")
    private Integer breedStrain;

    /**
     * 母本编码（引用 t_farm_breed_info.breed_strain_code）。
     */
    @NotBlank(message = "母本编码不能为空")
    @Size(max = 32, message = "母本编码长度不能超过 {max} 个字符")
    private String motherCode;

    /**
     * 父本编码。
     */
    @NotBlank(message = "父本编码不能为空")
    @Size(max = 32, message = "父本编码长度不能超过 {max} 个字符")
    private String fatherCode;

    /**
     * 仔代编码（必须先在 t_farm_breed_info 建好）。
     */
    @NotBlank(message = "仔代编码不能为空")
    @Size(max = 32, message = "仔代编码长度不能超过 {max} 个字符")
    private String cubCode;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
