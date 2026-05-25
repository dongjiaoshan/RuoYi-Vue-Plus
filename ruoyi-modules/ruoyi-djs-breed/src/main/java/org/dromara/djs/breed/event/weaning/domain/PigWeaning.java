package org.dromara.djs.breed.event.weaning.domain;

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
 * 母猪断奶记录实体（BRD-EVENT-002 WEAN）。
 *
 * <p>对应表 {@code t_farm_pig_weaning}（SYS-INIT-001 DDL）。状态机推进 FM → DN。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_weaning")
public class PigWeaning extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private Long pigId;
    private String earNo;

    /** FK → t_farm_pig_farrow.id（关联分娩）。 */
    private Long farrowId;

    /** FK → t_farm_pig_breeding.id（关联配种，可由分娩反查）。 */
    private Long breedingId;

    private LocalDateTime weaningDate;

    /** 当前活仔数（断奶时统计）。 */
    private Integer weanedCount;

    /** 断奶仔猪总重 kg。 */
    private BigDecimal weanedWeight;

    /** 断奶仔猪平均重 kg。 */
    private BigDecimal avgWeanedWeight;

    private Long operatorId;

    @TableLogic
    private String delFlag;

    private String remark;
}
