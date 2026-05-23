package org.dromara.djs.breed.death.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.dromara.common.core.validate.enumd.EnumPattern;
import org.dromara.djs.breed.common.enums.DeathDestEnum;
import org.dromara.djs.breed.common.enums.DeathKindEnum;
import org.dromara.djs.breed.common.enums.DeathReasonEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 猪只死亡信息提交BO。
 *
 * <p>猪只基础信息（耳号、类型、栋舍等）通过 pigId 查询获取，无需前端上送。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
public class PigDeathBo {

    /**
     * 主键ID（编辑时使用）。
     */
    private Long id;

    /**
     * 猪只ID（必填，用于查询猪只基础信息）。
     */
    @NotNull(message = "猪只ID不能为空")
    private Long pigId;

    /**
     * 死亡日期时间。
     */
    @NotNull(message = "死亡日期不能为空")
    private LocalDateTime deathDate;

    /**
     * 死亡分类（字典 death_type）。
     */
    @NotBlank(message = "死亡分类不能为空")
    @EnumPattern(type = DeathKindEnum.class, fieldName = "code", message = "死亡分类值无效")
    private String deathKind;

    /**
     * 死亡原因（字典 death_reason）。
     */
    @NotBlank(message = "死亡原因不能为空")
    @EnumPattern(type = DeathReasonEnum.class, fieldName = "code", message = "死亡原因值无效")
    private String deathReason;

    /**
     * 死亡去向（字典 death_dest）。
     */
    @EnumPattern(type = DeathDestEnum.class, fieldName = "code", message = "死亡去向值无效")
    private String deathDest;

    /**
     * 死亡重量（KG）。
     */
    private BigDecimal deathWeight;

    /**
     * 照片OSS IDs列表。
     */
    private List<String> ossIds;

    /**
     * 操作人ID。
     */
    private Long operatorId;

    /**
     * 备注。
     */
    private String remark;

}
