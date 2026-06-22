package org.dromara.djs.warehouse.purchase.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.purchase.domain.bo.PurchaseInBo;
import org.dromara.djs.warehouse.purchase.domain.query.PurchaseInProductQuery;
import org.dromara.djs.warehouse.purchase.domain.query.PurchaseInQuery;
import org.dromara.djs.warehouse.purchase.domain.vo.PurchaseInProductVo;
import org.dromara.djs.warehouse.purchase.domain.vo.PurchaseInRecordVo;

import java.math.BigDecimal;
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
     * 通用入库（并发安全 UPSERT location_stock + INSERT stock_flow），供跨模块联动复用。
     *
     * <p>{@link #submit(PurchaseInBo)} 即以 {@code flowType='purchase_in'} 委托本方法；门店退回联动
     * （STR-RETURN-REBUILD-001，K4「退回→外购入库」）以 {@code flowType='return_in'} 调用，
     * 不污染采购入库记录列表。操作人取 {@code LoginHelper.getUserId()}。</p>
     *
     * @param productId 产品 ID（FK → t_warehouse_product_info.id），必填且须存在
     * @param locationId 库位 ID（FK → t_warehouse_location_info.id），必填且须存在
     * @param quantity  入库数量，必须 &gt; 0
     * @param flowType  stock_flow.flow_type 字典 value（如 purchase_in / return_in）
     * @param remark    备注（可空）
     * @return 写入的 stock_flow id
     */
    Long inbound(Long productId, Long locationId, BigDecimal quantity, String flowType, String remark);

    /**
     * 分页查询采购入库记录（仅 stock_flow.flow_type='purchase_in'）。
     */
    TableDataInfo<PurchaseInRecordVo> queryPageList(PurchaseInQuery query, PageQuery pageQuery);

    /**
     * 不分页查询（导出用）。
     */
    List<PurchaseInRecordVo> queryList(PurchaseInQuery query);

    /**
     * 采购入库「商品维度」分页查询（WMS-OUTSOURCE-001 需求1）。
     *
     * <p>以外购商品（{@code product_type=2}）为粒度，聚合 currentStock / lastInTime / lastPurchaserId /
     * monthInTotal，并 service 层批量回填 storeLocationName + imageUrl（禁 N+1）。lastPurchaserName 走
     * VO 上 {@code @Translation(USER_ID_TO_NICKNAME)} 序列化时翻译。</p>
     */
    TableDataInfo<PurchaseInProductVo> queryProductPageList(PurchaseInProductQuery query, PageQuery pageQuery);

}
