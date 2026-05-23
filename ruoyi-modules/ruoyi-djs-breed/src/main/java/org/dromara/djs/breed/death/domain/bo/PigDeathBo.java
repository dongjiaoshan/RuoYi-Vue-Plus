package org.dromara.djs.breed.death.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 猪只死亡信息提交BO。
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
     * 猪只ID。
     */
    @NotNull(message = "猪只ID不能为空")
    private Long pigId;

    /**
     * 耳号（用于校验）。
     */
    @NotBlank(message = "耳号不能为空")
    private String earNo;

    /**
     * 死亡日期时间。
     */
    @NotNull(message = "死亡日期不能为空")
    private LocalDateTime deathDate;

    /**
     * 死亡猪只类型（字典 pig_type）。
     */
    @NotBlank(message = "死亡猪只类型不能为空")
    private String deathPigType;

    /**
     * 死亡分类（字典 death_type）。
     */
    @NotBlank(message = "死亡分类不能为空")
    private String deathKind;

    /**
     * 死亡原因（字典 death_reason）。
     */
    @NotBlank(message = "死亡原因不能为空")
    private String deathReason;

    /**
     * 死亡去向（字典 death_dest）。
     */
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
     * 栋舍名称。
     */
    private String barnName;

    /**
     * 栏位名称。
     */
    private String penName;

    /**
     * 备注。
     */
    private String remark;

}
