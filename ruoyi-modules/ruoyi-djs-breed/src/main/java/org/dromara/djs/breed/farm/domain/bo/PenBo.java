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
import org.dromara.djs.breed.farm.domain.Pen;

/**
 * 栏位入参 BO（BRD-MD-002）。
 *
 * @author djs
 * @since BRD-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = Pen.class, reverseConvertGenerate = false)
public class PenBo extends BaseEntity {

    /**
     * 栏位 ID（编辑时必填）。
     */
    private Long id;

    /**
     * 所属栋舍 ID（新增必填；编辑时不允许变更，由 Service 强制覆盖为 DB 原值）。
     */
    @NotNull(message = "所属栋舍不能为空")
    private Long barnId;

    /**
     * 栏位编码（{@code A-Z / 0-9 / _ - .} 32 字符内；编辑时不允许修改）。
     */
    @NotBlank(message = "栏位编码不能为空")
    @Size(max = 32, message = "栏位编码长度不能超过 {max} 个字符")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "栏位编码仅允许字母、数字、下划线、连字符、点号")
    private String penCode;

    /**
     * 栏位名称。
     */
    @NotBlank(message = "栏位名称不能为空")
    @Size(max = 64, message = "栏位名称长度不能超过 {max} 个字符")
    private String penName;

    /**
     * 栏位类型（字典 {@code djs_pen_type}）。
     */
    @NotBlank(message = "栏位类型不能为空")
    @Size(max = 16, message = "栏位类型长度不能超过 {max} 个字符")
    private String penType;

    /**
     * 容量。
     */
    @Min(value = 0, message = "栏位容量不能为负数")
    private Integer capacity;

    /**
     * 状态（{@code 1}=启用 / {@code 0}=停用）。
     */
    @NotNull(message = "栏位状态不能为空")
    private Integer penStatus;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
