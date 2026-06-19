package org.dromara.djs.breed.core.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 猪只信息实体（BRD-CORE-001 ★ 中心实体）。
 *
 * <p>对应表 {@code t_farm_pig_info}（SYS-INIT-001 V202605200901 L161-203 建表）。
 * 状态机推进通过 {@code PigCoreService#fireEvent} 触发；本实体只承载字段，
 * 业务规则全部下沉到 {@code PigStateMachine}。</p>
 *
 * <p><b>关键字段</b>：</p>
 * <ul>
 *   <li>{@link #currentStatus} 当前 lifecycle（{@code djs_pig_lifecycle} 10 枚举）；</li>
 *   <li>{@link #statusStartedAt} 进入当前状态时间，小程序"按 X 天提醒"基准；</li>
 *   <li>{@link #endReason} 仅 currentStatus=END 时填 DEAD/CULL/MARKET；</li>
 *   <li>{@link #version} MyBatis-Plus 乐观锁；</li>
 *   <li>{@link #delUnique} 软删 token，对齐 UNIQUE(tenant_id, ear_no, lifecycle_id, del_unique)。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_info")
public class Pig extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 耳号全版 {breed}-{strain}-{sex}-{yyMMdd}-{seq:03d}。 */
    private String earTag;

    /** 耳号简版 {yyMMdd}-{seq:03d}（业务键）。 */
    private String earNo;

    /** 生命周期 id（耳号复用次数 +1，SYS-INFRA-004）。 */
    private Integer lifecycleId;

    /** 是否可回收复用（1=是 0=否）。 */
    private Integer recyclable;

    /** 性别（F=母 M=公，字典 sys_user_sex 一致）。 */
    private String pigSex;

    /** 猪只类型（字典 pig_type：sow/boar/piglet/fattening）。 */
    private String pigType;

    /** 是否阉割（1=否 2=是）：阉割选猪 picker 仅列 is_castrated=1（否）；recordCastrate 成功后置 2。 */
    private Integer isCastrated;

    /** 品种编码（引用 t_farm_breed_info）。 */
    private String pigBreedCode;

    /** 品系编码（引用 t_farm_breed_info）。 */
    private String pigStrainCode;

    /**
     * 当前状态（字典 {@code djs_pig_lifecycle}：HB/PZ/FM/DN/LC/KH/FQ/END）；仅种母猪走繁殖态，非种母猪为空（{@code ''}）。
     * 由状态机维护，业务代码请通过 {@code fireEvent} 推进而非直接写。
     */
    private String currentStatus;

    /** 进入当前状态的时间（小程序"按 X 天提醒"基准）。 */
    private LocalDateTime statusStartedAt;

    /** 终止原因（END 状态时填，{@link org.dromara.djs.breed.core.enums.PigEndReason}）。 */
    private String endReason;

    /** 父猪耳号（仔猪用）。 */
    private String fatherEar;

    /** 母猪耳号（仔猪用）。 */
    private String motherEar;

    /** 出生日期。 */
    private LocalDate birthDate;

    /** 出生重 kg（仔猪耳标登记时按头录入回写）。 */
    private BigDecimal birthWeight;

    /** 引种日期。 */
    private LocalDate introduceDate;

    /** 引种方式（字典 introduce_from：internal/external）。 */
    private String introduceType;

    /** 供应商 ID（外部引种用）。 */
    private Long supplierId;

    /** 胎次（母猪 FARROW 时自动 +1）。 */
    private Integer parity;

    /**
     * 累计配种次数（每次 BREED 事件 +1，由 {@code applyEventSideEffects} wrapper-only update 维护防 race）。
     * 用于异常母猪识别（如"配种 ≥ 3 次仍未孕"）。
     */
    private Integer matingCount;

    /**
     * 最近一次配种日期（BREED 事件时由 service 写入 payload.breedingDate.toLocalDate()）。
     * 用于计算妊娠天数 vs production_cycle_config.pregnancy_days。
     */
    private LocalDate lastMatingDate;

    /** 当前栋舍 ID。 */
    private Long barnId;

    /** 当前栏位 ID。 */
    private Long penId;

    /** 最近一次配种记录 ID（BREED 时由状态机回写）。 */
    private Long matingId;

    /** 是否被预约出栏（1=是 0=否）。 */
    private Integer isAppointed;

    /** 预约门店 ID。 */
    private Long storeId;

    /** 备注。 */
    private String remark;

    /** 软删标记（'0' 未删 / '1' 已删）。 */
    @TableLogic
    private String delFlag;

    /** 软删唯一性辅助列。 */
    private Long delUnique;

    /** MP 乐观锁版本（强制：状态机 update 必走此字段）。 */
    @Version
    private Integer version;
}
