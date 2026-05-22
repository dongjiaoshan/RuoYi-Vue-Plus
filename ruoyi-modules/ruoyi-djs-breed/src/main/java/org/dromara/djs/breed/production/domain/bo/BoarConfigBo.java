package org.dromara.djs.breed.production.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.breed.production.domain.BoarConfig;

import java.math.BigDecimal;

/**
 * 精液 / 公猪配置入参 BO（BRD-MD-003 Tab2）。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BoarConfig.class, reverseConvertGenerate = false)
public class BoarConfigBo extends BaseEntity {

    /**
     * 主键（编辑时必填）。
     */
    private Long id;

    /**
     * 关联公猪 ID（V1 null 表示通用配置）。
     */
    private Long boarId;

    /**
     * 精液密度阈值（亿/mL）。
     */
    @NotNull(message = "精液密度阈值不能为空")
    @DecimalMin(value = "0.00", message = "精液密度阈值不能为负")
    private BigDecimal spermQualityThreshold;

    /**
     * 同一公猪两次采精的最小间隔天数。
     */
    @NotNull(message = "采精间隔天数不能为空")
    @Min(value = 0, message = "采精间隔天数不能为负")
    private Integer breedingIntervalDays;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
