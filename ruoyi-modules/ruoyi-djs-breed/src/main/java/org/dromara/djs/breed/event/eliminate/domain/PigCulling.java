package org.dromara.djs.breed.event.eliminate.domain;

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
 * 猪只淘汰记录实体（BRD-EVENT-004 ELIMINATE）。
 *
 * <p>对应表 {@code t_farm_pig_culling}（SYS-INIT-001 DDL #18）。
 * 由 {@code EliminateServiceImpl} 在淘汰事件录入时 INSERT；触发状态机
 * {@code (非 END) → END} 并由 {@code applyEventSideEffects} 写 end_reason=CULL。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_culling")
public class PigCulling extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 猪只 pig_info.id。 */
    private Long pigId;

    /** 猪只耳号。 */
    private String earNo;

    /** 操作当时猪只日龄(事件日-出生日/引种日,落库冻结快照,ADR-0017) */
    private Integer ageDays;

    /** 淘汰日期。 */
    private LocalDateTime cullingDate;

    /** 淘汰原因（字典 eliminate_reason）。 */
    private String cullingReason;

    /** 淘汰去向（字典 eliminate_dest）。 */
    private String cullingDest;

    /** 淘汰重量 kg。 */
    private BigDecimal cullingWeight;

    /** 多角度照片 OSS IDs（强制至少 1 张）。 */
    private String ossIds;

    /** 操作人。 */
    private Long operatorId;

    /** 淘汰记录人 sys_user.user_id（mp EmployeePicker 所选）。 */
    private Long cullingRecorderId;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 备注。 */
    private String remark;
}
