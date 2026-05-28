package org.dromara.djs.warehouse.flow.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.flow.domain.query.StockFlowQuery;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;

import java.util.List;

/**
 * 出入库流水查询 Service（WMS-MAT-001 → 预热 D11 WMS-FLOW-001）。
 *
 * <p>本 ticket 仅暴露查询接口（无写入入口；写入由 {@link IMatFlowService}
 * + {@code PigBurnRecordServiceImpl} + 后续 WMS-PIG-002 / WMS-VEG-001 等 service）。</p>
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

}
