package org.dromara.djs.breed.event.die.domain;

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
 * 猪只死亡记录实体（BRD-EVENT-004 DIE）。
 *
 * <p>对应表 {@code t_farm_pig_death}（SYS-INIT-001 DDL #17）。
 * 由 {@code DieServiceImpl} 在死亡事件录入时 INSERT；触发状态机
 * {@code (非 END) → END} 并由 {@code applyEventSideEffects} 写 end_reason=DEAD。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_death")
public class PigDeath extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 猪只 pig_info.id。 */
    private Long pigId;

    /** 猪只耳号。 */
    private String earNo;

    /** 死亡日期。 */
    private LocalDateTime deathDate;

    /** 死亡猪只类型（字典 pig_type：sow/boar/piglet/fattening）。 */
    private String deathPigType;

    /** 死亡分类（字典 death_type：normal/abnormal）。 */
    private String deathKind;

    /** 死亡原因（字典 death_reason）。 */
    private String deathReason;

    /** 死亡去向（字典 death_dest）。 */
    private String deathDest;

    /** 死亡重量 kg。 */
    private BigDecimal deathWeight;

    /** 多角度照片 OSS IDs（逗号分隔；强制至少 1 张，prompt §1）。 */
    private String ossIds;

    /** 操作人。 */
    private Long operatorId;

    /** 栋舍名称冗余。 */
    private String barnName;

    /** 栏位名称冗余。 */
    private String penName;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 备注。 */
    private String remark;
}
