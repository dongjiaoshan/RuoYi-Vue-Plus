package org.dromara.djs.breed.event.farrow.domain;

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
 * 母猪分娩记录实体（BRD-EVENT-002）。
 *
 * <p>对应表 {@code t_farm_pig_farrow}（SYS-INIT-001 DDL，字段名按客户 xlsx 落地）。
 * 由 {@code FarrowServiceImpl} 在分娩事件录入时 INSERT，{@code PigEarTagServiceImpl}（BRD-EVENT-003）
 * 通过 {@link org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper} 查询已分娩母猪做仔猪耳标。</p>
 *
 * <p>关键字段：</p>
 * <ul>
 *   <li>{@link #breedingId} FK → {@code t_farm_pig_breeding.id}，追溯到本次配种；</li>
 *   <li>{@link #liveBorn} 活产仔数 = 仔猪耳标贴标上限；</li>
 *   <li>{@link #parity} 当时胎次（与 {@code Pig.parity} 同步，由 {@code applyEventSideEffects} 维护）。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_farrow")
public class PigFarrow extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 母猪 pig_info.id。 */
    private Long pigId;

    /** 母猪耳号。 */
    private String earNo;

    /** 关联配种记录 ID（FK → t_farm_pig_breeding.id）。 */
    private Long breedingId;

    /** 分娩日期。 */
    private LocalDateTime farrowDate;

    /** 总仔猪数。 */
    private Integer totalBorn;

    /** 活产仔数（仔猪耳标贴标上限）。 */
    private Integer liveBorn;

    /** 死胎数。 */
    private Integer deadBorn;

    /** 木乃伊数。 */
    private Integer mummyBorn;

    /** 弱仔数。 */
    private Integer weakBorn;

    /** 仔猪公数。 */
    private Integer maleCount;

    /** 仔猪母数。 */
    private Integer femaleCount;

    /** 总重 kg。 */
    private BigDecimal totalWeight;

    /** 平均重 kg。 */
    private BigDecimal avgWeight;

    /** 母猪本次胎次。 */
    private Integer parity;

    /** 录入人员 user_id。 */
    private Long operatorId;

    /** 栋舍名称（冗余便于查询展示）。 */
    private String barnName;

    /** 栏位名称（冗余）。 */
    private String penName;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 备注。 */
    private String remark;
}
