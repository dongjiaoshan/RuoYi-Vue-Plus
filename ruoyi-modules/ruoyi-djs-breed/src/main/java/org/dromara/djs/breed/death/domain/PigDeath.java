package org.dromara.djs.breed.death.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 猪只死亡记录实体。
 *
 * <p>对应表 {@code t_farm_pig_death}。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_death")
public class PigDeath extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 猪只 ID（引用 t_farm_pig_info.id）。
     */
    private Long pigId;

    /**
     * 猪只耳号（冗余）。
     */
    private String earNo;

    /**
     * 死亡日期时间。
     */
    private LocalDateTime deathDate;

    /**
     * 死亡猪只类型（字典 pig_type：sow/boar/piglet/fattening）。
     */
    private String deathPigType;

    /**
     * 死亡分类（字典 death_type：normal/abnormal）。
     */
    private String deathKind;

    /**
     * 死亡原因（字典 death_reason）。
     */
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
     * 照片 OSS IDs（多图逗号分隔）。
     */
    private String ossIds;

    /**
     * 操作人 ID。
     */
    private Long operatorId;

    /**
     * 栋舍名称（冗余）。
     */
    private String barnName;

    /**
     * 栏位名称（冗余）。
     */
    private String penName;

    /**
     * 录入人（引用 sys_user.user_id）。
     */
    private Long createBy;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

}
