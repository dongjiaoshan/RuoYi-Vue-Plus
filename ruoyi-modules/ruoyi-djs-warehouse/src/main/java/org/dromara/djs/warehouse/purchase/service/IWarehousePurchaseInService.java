package org.dromara.djs.warehouse.purchase.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.purchase.domain.bo.PurchaseInBo;
import org.dromara.djs.warehouse.purchase.domain.query.PurchaseInQuery;
import org.dromara.djs.warehouse.purchase.domain.vo.PurchaseInRecordVo;

import java.util.List;

/**
 * 采购入库简化版 Service（D9 hotfix）。
 *
 * <p>D9 testing-human §5 暴露包材领用前置库存缺失 — V1 真采购流程延后 D11+，本 hotfix 给 admin 一个
 * 最简入库通道：UPSERT location_stock + INSERT stock_flow（flow_type='purchase_in', inout_type='IN'）。</p>
 *
 * @author djs
 * @since D9 hotfix purchase-in
 */
public interface IWarehousePurchaseInService {

    /**
     * 提交一笔采购入库。
     *
     * @param bo 入参
     * @return 写入的 stock_flow id
     */
    Long submit(PurchaseInBo bo);

    /**
     * 分页查询采购入库记录（仅 stock_flow.flow_type='purchase_in'）。
     */
    TableDataInfo<PurchaseInRecordVo> queryPageList(PurchaseInQuery query, PageQuery pageQuery);

    /**
     * 不分页查询（导出用）。
     */
    List<PurchaseInRecordVo> queryList(PurchaseInQuery query);

}
