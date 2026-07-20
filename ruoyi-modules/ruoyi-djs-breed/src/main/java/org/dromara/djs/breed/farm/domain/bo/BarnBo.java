package org.dromara.djs.breed.farm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.breed.farm.domain.Barn;

/**
 * 栋舍入参 BO（BRD-MD-002）。
 *
 * <p>{@code barnCode} 接受外部传入（编码由人工输入，不走 IBizCodeGenerator —— 客户希望
 * 栋舍编码与现实门牌一致，例 {@code B01-A}）；新增前由 Service 校验同 tenant 唯一。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = Barn.class, reverseConvertGenerate = false)
public class BarnBo extends BaseEntity {

    /**
     * 栋舍 ID（编辑时必填）。
     */
    private Long id;

    /**
     * 栋舍编码（{@code A-Z / 0-9 / _ - .} 32 字符内，编辑时不允许修改）。
     */
    @NotBlank(message = "栋舍编码不能为空")
    @Size(max = 32, message = "栋舍编码长度不能超过 {max} 个字符")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "栋舍编码仅允许字母、数字、下划线、连字符、点号")
    private String barnCode;

    /**
     * 栋舍名称。
     */
    @NotBlank(message = "栋舍名称不能为空")
    @Size(max = 64, message = "栋舍名称长度不能超过 {max} 个字符")
    private String barnName;

    /**
     * 栋舍类型（字典 {@code djs_barn_type}）。
     */
    @NotBlank(message = "栋舍类型不能为空")
    @Size(max = 16, message = "栋舍类型长度不能超过 {max} 个字符")
    private String barnType;

    /**
     * 容量（头位数，整数，>= 0）。
     */
    @Min(value = 0, message = "栋舍容量不能为负数")
    private Integer capacity;

    /**
     * 状态（{@code 1}=启用 / {@code 0}=停用）。
     */
    @NotNull(message = "栋舍状态不能为空")
    private Integer barnStatus;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
