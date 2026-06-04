package org.dromara.djs.store.demand.service;

import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;

/**
 * 门店端需求服务（STR-DEMAND-001）。
 *
 * <p><b>薄封装层</b>：门店端发起需求 100% 复用 WMS 的
 * {@link org.dromara.djs.warehouse.demand.service.IDemandManageService}（CRUD + 编码 + 校验）
 * 与 {@link org.dromara.djs.warehouse.demand.service.IDemandStatusService}（状态机）。
 * 本接口只负责门店域专属的「创建即提交」语义——门店发起的需求跳过 DRAFT，落库后立即推到
 * {@code SUBMITTED}（doc/10 §4.F-STR-01 业务规则）。</p>
 *
 * <p>列表 / 详情 / 取消 / 指定猪只直接由 controller delegate warehouse service，不在本接口重复声明。</p>
 *
 * @author djs
 * @since STR-DEMAND-001
 */
public interface IStoreDemandService {

    /**
     * 门店发起需求：新增 + 立即提交（两步原子）。
     *
     * <p>流程：warehouse {@code insertByBo}（初始 DRAFT + 自动生成 demand_no）
     * → warehouse {@code transition(id, SUBMIT, ...)} 推到 SUBMITTED。两步在同一事务内。</p>
     *
     * @param bo 需求录入 BO（复用 warehouse {@link DemandManageBo}；storeId 由门店端 UI 显式传）
     * @return 新建记录 ID
     */
    Long createStoreDemand(DemandManageBo bo);
}
