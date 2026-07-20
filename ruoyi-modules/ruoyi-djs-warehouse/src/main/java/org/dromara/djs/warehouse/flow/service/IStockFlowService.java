package org.dromara.djs.warehouse.flow.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.flow.domain.query.StockFlowQuery;
import org.dromara.djs.warehouse.flow.domain.vo.PackingHomeVo;
import org.dromara.djs.warehouse.flow.domain.vo.PackingItemVo;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;

import java.util.List;

/**
 * 出入库流水查询 Service（WMS-MAT-001 + D11 WMS-FLOW-001）。
 *
 * <p>查询接口（无写入入口；写入由 {@link IMatFlowService}
 * + {@code PigBurnRecordServiceImpl} + WMS-PIG-002 / WMS-VEG-001 等 service）：</p>
 * <ul>
 *   <li>{@code queryPageList / queryList / queryById} 通用流水（WMS-MAT-001）</li>
 *   <li>{@code queryInList / queryOutList} admin 入库 / 出库记录两页（D11 WMS-FLOW-001，
 *       强制 {@code inout_type=IN/OT}）</li>
 *   <li>{@code queryPackingHome / queryPackingList / queryPackingDetail} mp 包材库管理
 *       （belong_type='package'，D11 WMS-FLOW-001）</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MAT-001
 */
public interface IStockFlowService {

    /**
     * 分页查询流水（多维度筛选）。
     */
    TableDataInfo<StockFlowVo> queryPageList(StockFlowQuery query, PageQuery pageQuery);

    /**
     * 不分页查询（导出 / 报表用）。
     */
    List<StockFlowVo> queryList(StockFlowQuery query);

    /**
     * 详情。
     */
    StockFlowVo queryById(Long id);

    /**
     * admin 入库记录分页（强制 {@code inout_type=IN}）。
     *
     * <p>D11 WMS-FLOW-001：在 {@link #queryPageList} 基础上锁定入库方向。前端传入的
     * {@code inoutType} 被本方法覆盖为 {@code IN}，防止越界查到出库流水。</p>
     */
    TableDataInfo<StockFlowVo> queryInList(StockFlowQuery query, PageQuery pageQuery);

    /**
     * admin 出库记录分页（强制 {@code inout_type=OT}）。
     */
    TableDataInfo<StockFlowVo> queryOutList(StockFlowQuery query, PageQuery pageQuery);

    /**
     * admin 入库记录导出（强制 {@code inout_type=IN}）。
     */
    List<StockFlowVo> queryInExport(StockFlowQuery query);

    /**
     * admin 出库记录导出（强制 {@code inout_type=OT}）。
     */
    List<StockFlowVo> queryOutExport(StockFlowQuery query);

    /**
     * mp 包材库今日总览 KPI（belong_type='package'）。
     */
    PackingHomeVo queryPackingHome();

    /**
     * mp 包材库列表（belong_type='package'，后端强制 eq；按库存升序或名称排序）。
     *
     * @param sortBy {@code stock} 库存升序 / 其余按名称
     */
    List<PackingItemVo> queryPackingList(String sortBy);

    /**
     * mp 包材详情：该产品流水分页（按 product_id 过滤）。
     */
    TableDataInfo<StockFlowVo> queryPackingDetail(Long productId, PageQuery pageQuery);

}
