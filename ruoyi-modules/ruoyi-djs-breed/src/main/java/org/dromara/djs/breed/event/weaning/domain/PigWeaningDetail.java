package org.dromara.djs.breed.event.weaning.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 母猪断奶逐头录重明细实体（BRD-FIX-MP-EVENT-BREED-IA-001）。
 *
 * <p>对应表 {@code t_farm_pig_weaning_detail}。每行一头断奶仔猪的体重，
 * 由 {@code WeaningServiceImpl} 在断奶事件录入时与主记录同事务批量 INSERT。
 * 主记录 {@code t_farm_pig_weaning} 的 weaned_weight / avg_weaned_weight 由 FE 汇总后下发，
 * 明细表是逐头粒度的留底（原型 91 逐头录重）。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-EVENT-BREED-IA-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_weaning_detail")
public class PigWeaningDetail extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** FK → t_farm_pig_weaning.id（关联断奶主记录）。 */
    private Long weaningId;

    /** 仔猪序号（同一断奶记录内从 1 起递增）。 */
    private Integer pigletSeq;

    /** 仔猪耳号（可空，原型逐头行允许无耳号只录重）。 */
    private String earNo;

    /** 断奶体重 kg。 */
    private BigDecimal weight;

    @TableLogic
    private String delFlag;

    private String remark;
}
