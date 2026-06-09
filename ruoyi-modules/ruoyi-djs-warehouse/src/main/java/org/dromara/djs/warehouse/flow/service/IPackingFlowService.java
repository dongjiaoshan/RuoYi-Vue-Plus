package org.dromara.djs.warehouse.flow.service;

import org.dromara.djs.warehouse.flow.domain.bo.PackCheckBo;
import org.dromara.djs.warehouse.flow.domain.bo.PackInBo;
import org.dromara.djs.warehouse.flow.domain.bo.PackOutBo;

/**
 * 包材库 per 项写操作 Service（FIX-WMS-MP-MATISSUE-001，原型图54 per 项 4 动作的写端点）。
 *
 * <p>承接 mp 包材详情页 3 个写动作（产品入库 / 产品出库 / 库存盘点），第 4 个「进出库记录」
 * 是查询走 {@link IStockFlowService#queryPackingDetail}（按 productId 分页）。</p>
 *
 * <p>每个方法同事务跨表（{@code stock_flow} + {@code location_stock}），写流水 + 改库存一致，
 * 范式沿用 {@code MatFlowServiceImpl#pick/returnBack} + {@code StockCheckServiceImpl#completeCheck}
 * 单 line 逻辑（只复用其 mapper 方法，不改其代码）。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-MATISSUE-001
 */
public interface IPackingFlowService {

    /**
     * 包材产品入库（同事务：INSERT stock_flow purchase_in/IN + 加库存，无库存行则建账）。
     *
     * @return 新增 stock_flow 主键 id
     */
    Long packIn(PackInBo bo);

    /**
     * 包材产品出库（同事务：INSERT stock_flow pick_out/OT + 行锁扣减，库存不足回滚）。
     *
     * @return 新增 stock_flow 主键 id
     */
    Long packOut(PackOutBo bo);

    /**
     * 包材库存盘点（同事务：差异写 check_in/check_out 流水 + 回写库存至实盘绝对值，无库存行则建账）。
     *
     * @return 新增差异 stock_flow 主键 id；差异为 0（无流水）时返 null
     */
    Long packCheck(PackCheckBo bo);

}
