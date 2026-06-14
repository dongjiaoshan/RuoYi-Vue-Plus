package org.dromara.djs.breed.core.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 猪只状态变更流水查询入参（admin 端"事件台账"页用）。
 *
 * <p>替代 D6 早期方案 — 不再为每个事件做独立列表页（10+ 菜单），
 * 而是在 admin 端用一个统一台账查询所有事件流水（{@code t_farm_status_record}）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PigStatusRecordQuery extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 猪只 ID（精确）。 */
    private Long pigId;

    /** 耳号简版（精确）。 */
    private String earNo;

    /** 事件类型（INTRO / BREED / FARROW / WEAN / OESTRUS / NULL_RETURN / DIE / ELIMINATE / CASTRATE / TRANSFER / SLAUGHTER）。 */
    private String eventType;

    /** 新状态（10 lifecycle 之一，精确）。 */
    private String newStatus;

    /** 变更人用户名（按 sys_user.nick_name 模糊；service 端反查 create_by IN 命中 user_id）。 */
    private String createByName;

    /** 变更时间 ≥ */
    private LocalDateTime changeTimeStart;

    /** 变更时间 ≤ */
    private LocalDateTime changeTimeEnd;
}
