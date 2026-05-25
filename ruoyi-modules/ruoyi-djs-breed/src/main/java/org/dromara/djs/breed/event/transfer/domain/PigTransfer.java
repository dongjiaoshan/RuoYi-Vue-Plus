package org.dromara.djs.breed.event.transfer.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 猪只转移记录实体（BRD-EVENT-004 TRANSFER）。
 *
 * <p>对应表 {@code t_farm_pig_transfer}（SYS-INIT-001 DDL #20）。
 * 由 {@code TransferServiceImpl} 在转移事件录入时 INSERT；触发状态机
 * NO_CHANGE，状态保持。{@code applyEventSideEffects} 切换 barn_id / pen_id
 * 并根据 payload.newPigType 切换 pig_type（piglet → fattening，audit a-2）。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_transfer")
public class PigTransfer extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private Long pigId;

    private String earNo;

    private LocalDateTime transferDate;

    private Long oldBarnId;
    private Long oldPenId;
    private String oldBarnName;
    private String oldPenName;

    private Long newBarnId;
    private Long newPenId;
    private String newBarnName;
    private String newPenName;

    /** 转移原因（可选）。 */
    private String transferReason;

    private Long operatorId;

    @TableLogic
    private String delFlag;

    private String remark;
}
