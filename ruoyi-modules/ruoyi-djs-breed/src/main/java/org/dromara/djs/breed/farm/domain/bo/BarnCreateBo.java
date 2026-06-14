package org.dromara.djs.breed.farm.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 新建栋舍入参 BO（对齐原型「新建栋舍」弹窗）。
 *
 * <p>原型弹窗只填：栋舍名称 / 栋舍类型 / 大栏数量 / 限位栏数量 / 产床数量。
 * 栋舍编码由后端自动生成（不暴露给用户）；填三类栏位数量后系统在同一事务里批量生成对应栏位
 * （pen_type = big / stall / farrow，编号连号「1栏…N栏」）。</p>
 *
 * @author djs
 * @since BRD-AD-PROTO-ALIGN
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BarnCreateBo extends BaseEntity {

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
     * 大栏数量（生成 pen_type=big 的栏位数；0 表示不建大栏）。
     */
    @NotNull(message = "大栏数量不能为空")
    @PositiveOrZero(message = "大栏数量不能为负数")
    @Max(value = 999, message = "大栏数量不能超过 {value}")
    private Integer bigPenCount;

    /**
     * 限位栏数量（生成 pen_type=stall 的栏位数；0 表示不建限位栏）。
     */
    @NotNull(message = "限位栏数量不能为空")
    @PositiveOrZero(message = "限位栏数量不能为负数")
    @Max(value = 999, message = "限位栏数量不能超过 {value}")
    private Integer limitPenCount;

    /**
     * 产床数量（生成 pen_type=farrow 的栏位数；0 表示不建产床）。
     */
    @NotNull(message = "产床数量不能为空")
    @PositiveOrZero(message = "产床数量不能为负数")
    @Max(value = 999, message = "产床数量不能超过 {value}")
    private Integer bedCount;

    /**
     * 散栏数量（生成 pen_type=scatter 的栏位数；0 表示不建散栏）。
     */
    @NotNull(message = "散栏数量不能为空")
    @PositiveOrZero(message = "散栏数量不能为负数")
    @Max(value = 999, message = "散栏数量不能超过 {value}")
    private Integer scatterPenCount;

    /**
     * 保育栏数量（生成 pen_type=nursery_pen 的栏位数；0 表示不建保育栏）。
     */
    @NotNull(message = "保育栏数量不能为空")
    @PositiveOrZero(message = "保育栏数量不能为负数")
    @Max(value = 999, message = "保育栏数量不能超过 {value}")
    private Integer nurseryPenCount;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
