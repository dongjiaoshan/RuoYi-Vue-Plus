package org.dromara.djs.breed.core.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 猪只状态变更历史记录实体（BRD-CORE-001）。
 *
 * <p>对应表 {@code t_farm_status_record}（SYS-INIT-001 V202605200901 L211-229 建表）。
 * 每次状态机触发的 transition 都会写一行，作为业务追溯主链。</p>
 *
 * <p>{@code event_type} 存 {@code djs_pig_status_event} 字典 11 个 value
 * （INTRO/BREED/FARROW/WEAN/OESTRUS/NULL_RETURN/DIE/ELIMINATE/CASTRATE/TRANSFER/SLAUGHTER）。
 * DDL 注释里的 MATING/CONFIRM_PREG/... 旧词汇由本 ticket 的
 * {@code V202605260901__BRD-CORE-001-realign-status-record-comment.sql} 修正。</p>
 *
 * <p>流水表：保留 {@code del_flag} / {@code update_by} / {@code update_time}（与 DDL 一致），
 * 但 UNIQUE 索引不存在，故不需要 {@code del_unique}。</p>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_status_record")
public class PigStatusRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 猪只 ID（引用 t_farm_pig_info.id）。 */
    private Long pigId;

    /** 耳号（冗余便于查询）。 */
    private String earNo;

    /**
     * 原状态（{@link org.dromara.djs.breed.core.enums.PigLifecycle}）。
     * INTRO 首次写入时为 null（pig 创建态）。
     */
    private String oldStatus;

    /** 新状态（{@link org.dromara.djs.breed.core.enums.PigLifecycle}）。 */
    private String newStatus;

    /** 触发事件（{@link org.dromara.djs.breed.core.enums.PigStatusEvent}，字典 djs_pig_status_event）。 */
    private String eventType;

    /** 关联业务事件 ID（如 t_farm_pig_breeding.id / t_farm_pig_farrow.id）。 */
    private Long relatedEventId;

    /** 在原状态停留天数（业务层计算 = NOW - old_status_started_at）。 */
    private Integer durationDays;

    /** 状态变更时间（业务发生时间，不一定等于 create_time）。 */
    private LocalDateTime changeTime;
}
