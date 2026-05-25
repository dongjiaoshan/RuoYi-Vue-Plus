package org.dromara.djs.breed.event.nullreturn.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 母猪返空流记录实体（BRD-EVENT-002 NULL_RETURN）。
 *
 * <p>对应表 {@code t_farm_pig_abnormal}（SYS-INIT-001 DDL）。状态机源态 PH，按 abnormal_type 分流：</p>
 * <ul>
 *   <li>{@code R} (返情) → FQ；</li>
 *   <li>{@code A} (流产) → LC；</li>
 *   <li>{@code N} (空怀) → KH。</li>
 * </ul>
 *
 * <p>DB 字段值按 CR-20260524-07 用 xlsx 单字符码（R/A/N），service 层向状态机传 abort/return/idle 长字符串。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_abnormal")
public class PigAbnormal extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private Long pigId;
    private String earNo;
    private LocalDateTime abnormalDate;

    /** xlsx 字典码：R=返情 / A=流产 / N=空怀（VARCHAR(16)，留 schema 余量）。 */
    private String abnormalType;

    /** 关联本次配种 ID（用于追溯本次返空对应的配种）。 */
    private Long relatedBreedingId;

    /** 异常原因（可选自由文本）。 */
    private String abnormalReason;

    private Long operatorId;

    @TableLogic
    private String delFlag;

    private String remark;
}
