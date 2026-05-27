package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 需求状态历史 timeline 条目（WMS-DEMAND-001）。
 *
 * <p>{@code DemandStatusService} 把 {@code audit_history} JSON 列解析为 list 返回给
 * admin 端 timeline 弹窗。每次状态转移 append 1 条。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Data
public class AuditHistoryEntryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 源状态。 */
    private String from;

    /** 目标状态。 */
    private String to;

    /** 触发事件。 */
    private String event;

    /** 操作人 user_id。 */
    private Long operator;

    /** 操作人姓名（写入时由 service 内查 sys_user）。 */
    private String operatorName;

    /** 操作时间。 */
    private LocalDateTime ts;

    /** 操作备注。 */
    private String remark;
}
