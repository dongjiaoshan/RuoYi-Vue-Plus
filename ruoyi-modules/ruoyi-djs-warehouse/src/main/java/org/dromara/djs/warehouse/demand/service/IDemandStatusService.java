package org.dromara.djs.warehouse.demand.service;

import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;

/**
 * 需求状态推进服务（WMS-DEMAND-001）。
 *
 * <p>分离 transition 接口与 CRUD（{@link IDemandManageService}）的原因：</p>
 * <ul>
 *   <li>transition 需要 Redisson 分布式锁 + 业务前置校验 + audit_history append，逻辑独立</li>
 *   <li>CROSS-FLOW-003（D14 listener）调本接口走 PARTIAL_SHIP / COMPLETE 事件，不应通过 CRUD 服务</li>
 * </ul>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
public interface IDemandStatusService {

    /**
     * 状态转移（外部唯一推进入口）。
     *
     * <p>典型场景：admin 端按钮 / mp 端调度页 / CROSS-FLOW-003 listener。</p>
     *
     * @param demandId 需求 ID
     * @param event    触发事件
     * @param operator 操作人 user_id（null 时由 service 内 LoginHelper 兜底）
     * @param remark   备注（可选，写入 audit_history）
     */
    void transition(Long demandId, DemandEvent event, Long operator, String remark);
}
