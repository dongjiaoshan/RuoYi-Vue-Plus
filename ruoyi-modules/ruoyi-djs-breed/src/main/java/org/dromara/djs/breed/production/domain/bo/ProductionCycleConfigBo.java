package org.dromara.djs.breed.production.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.breed.production.domain.ProductionCycleConfig;

/**
 * 生产周期配置入参 BO（BRD-MD-003 Tab1）。
 *
 * <p>注意：新增 / 编辑只允许改 {@code customValue}（让 admin 微调），
 * {@code configKey} / {@code defaultValue} 由 seed 灌入后只读。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ProductionCycleConfig.class, reverseConvertGenerate = false)
public class ProductionCycleConfigBo extends BaseEntity {

    /**
     * 主键（编辑时必填；新增不传）。
     */
    private Long id;

    /**
     * 业务键（admin 编辑时不可改；新增时按约定 key 写入）。
     */
    @NotBlank(message = "业务键不能为空")
    @Size(max = 64, message = "业务键长度不能超过 {max} 个字符")
    private String configKey;

    /**
     * 业内默认值（天，admin 编辑时不可改）。
     */
    @NotNull(message = "默认值不能为空")
    @Min(value = 0, message = "默认值不能为负")
    private Integer defaultValue;

    /**
     * 客户自定义值（天，admin 可改；null 表示沿用 default）。
     */
    @Min(value = 0, message = "自定义值不能为负")
    private Integer customValue;

    /**
     * 单位（默认"天"）。
     */
    @Size(max = 16, message = "单位长度不能超过 {max} 个字符")
    private String unit;

    /**
     * 业务含义说明。
     */
    @Size(max = 255, message = "说明长度不能超过 {max} 个字符")
    private String description;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
