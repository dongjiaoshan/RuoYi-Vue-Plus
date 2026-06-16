package org.dromara.djs.store.demand.service;

import org.dromara.djs.store.demand.domain.bo.StoreDemandBatchBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;

/**
 * 门店端需求服务（STR-DEMAND-001）。
 *
 * <p><b>薄封装层</b>：门店端发起需求 100% 复用 WMS 的
 * {@link org.dromara.djs.warehouse.demand.service.IDemandManageService}（CRUD + 编码 + 校验）。
 * 本接口只负责门店域专属的「创建即提交」语义——门店发起的需求跳过 DRAFT，由 warehouse
 * {@code insertByBo} 直接落 {@code SUBMITTED}（doc/10 §4.F-STR-01 业务规则）。</p>
 *
 * <p>列表 / 详情 / 取消 / 指定猪只直接由 controller delegate warehouse service，不在本接口重复声明。</p>
 *
 * @author djs
 * @since STR-DEMAND-001
 */
public interface IStoreDemandService {

    /**
     * 门店发起需求：新增即提交。
     *
     * <p>流程：warehouse {@code insertByBo}（直接落 {@code SUBMITTED} + 自动生成 demand_no + 业态校验）。
     * insertByBo 已把门店/购物车/仓库三端创建统一收口为「提交需求给仓库」语义，无独立存草稿环节，
     * 故不再二次 {@code transition(SUBMIT)}。</p>
     *
     * @param bo 需求录入 BO（复用 warehouse {@link DemandManageBo}；storeId 由门店端 UI 显式传）
     * @return 新建记录 ID
     */
    Long createStoreDemand(DemandManageBo bo);

    /**
     * 门店购物车整单发起需求（STORE-DEMAND-REALIGN-001，对齐原型「新增需求」多产品整页）。
     *
     * <p>一次提交多产品：逐项查产品主数据冗余 productName/productSpec/productUnit，循环走
     * {@code createStoreDemand}（每条直接落 SUBMITTED）。整批在同一事务，任一项失败全回滚。
     * {@code mailing} 标记落 {@code demand_type}（store / mailing）。</p>
     *
     * @param bo 购物车整单 BO（storeId + items[] + demandRemark + 日期）
     * @return 成功创建的需求条数（= items.size()）
     */
    int batchCreate(StoreDemandBatchBo bo);

    /**
     * 门店收货确认（STORE-DEMAND-REALIGN-001，对齐原型列表「确认收货」）。
     *
     * <p>门店侧确认实物到店，patch {@code received_time} + {@code received_by}。
     * 仅 CONFIRMED 态可确认收货（原型「已确认」行才显按钮）；重复确认拒绝。
     * 不触碰仓库域状态机（薄封装铁律），状态仍由仓库主导推进。</p>
     *
     * @param id 需求 ID
     */
    void receive(Long id);
}
