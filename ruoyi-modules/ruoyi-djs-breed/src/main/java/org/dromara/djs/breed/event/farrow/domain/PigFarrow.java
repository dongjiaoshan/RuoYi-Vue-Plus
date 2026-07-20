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

    /** 操作当时猪只日龄(事件日-出生日/引种日,落库冻结快照,ADR-0017) */
    private Integer ageDays;

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

    /** 弱仔数（汇总）。 */
    private Integer weakBorn;

    /** 仔猪公数（汇总）。 */
    private Integer maleCount;

    /** 仔猪母数（汇总）。 */
    private Integer femaleCount;

    /** 健仔公猪数（原型 93 健仔数·公猪数）。 */
    private Integer healthyMale;

    /** 健仔母猪数（原型 93 健仔数·母猪数）。 */
    private Integer healthyFemale;

    /** 弱仔留养公猪数（原型 93 弱仔数(饲养)·公猪数）。 */
    private Integer weakRaisedMale;

    /** 弱仔留养母猪数（原型 93 弱仔数(饲养)·母猪数）。 */
    private Integer weakRaisedFemale;

    /** 弱仔处死数（原型 93 弱仔数(处死)）。 */
    private Integer weakCulled;

    /** 畸形数（原型 93 畸形数）。 */
    private Integer deformedBorn;

    /** 压死数（原型 93 产仔性能·压死系列，DJS-FIX-6-14-FARROW-CRUSHED-COL-001）。 */
    private Integer crushedBorn;

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

    /** 凭证图片 OSS IDs 逗号分隔（分娩现场照片，与 PigIntroduce.proofOssIds 对齐）。 */
    private String proofOssIds;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 备注。 */
    private String remark;
}
