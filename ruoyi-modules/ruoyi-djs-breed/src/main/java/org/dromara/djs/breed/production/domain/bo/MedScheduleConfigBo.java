package org.dromara.djs.breed.production.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.breed.production.domain.MedScheduleConfig;

/**
 * 药品 / 疫苗周期配置入参 BO（BRD-MD-003 Tab3）。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = MedScheduleConfig.class, reverseConvertGenerate = false)
public class MedScheduleConfigBo extends BaseEntity {

    /**
     * 主键（编辑时必填）。
     */
    private Long id;

    /**
     * 药品类型（字典 {@code djs_med_type}）。
     */
    @NotBlank(message = "药品类型不能为空")
    @Size(max = 32, message = "药品类型长度不能超过 {max} 个字符")
    private String medType;

    /**
     * 触发时机（业务事件枚举）。
     */
    @NotBlank(message = "触发时机不能为空")
    @Size(max = 64, message = "触发时机长度不能超过 {max} 个字符")
    private String eventTrigger;

    /**
     * 触发时机 +/- N 天偏移（允许负数）。
     */
    @NotNull(message = "天数偏移不能为空")
    private Integer daysOffset;

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
