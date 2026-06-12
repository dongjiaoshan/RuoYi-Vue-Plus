package org.dromara.djs.warehouse.stock.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.stock.domain.bo.LocationStockBo;
import org.dromara.djs.warehouse.stock.domain.bo.StockOutBo;
import org.dromara.djs.warehouse.stock.domain.query.LocationStockQuery;
import org.dromara.djs.warehouse.stock.domain.vo.LocationStockVo;

import java.util.Collection;
import java.util.List;

/**
 * 库存明细 Service（WMS-MD-001）。
 *
 * <p>本 ticket 仅暴露查询能力（admin 端 BizTable 只读列表）；新增 / 编辑入口由 WMS-DEMAND-001 /
 * WMS-STOCK-001 D8-D11 后续 ticket 通过出入库流水触发写入。</p>
 *
 * @author djs
 * @since WMS-MD-001
 */
public interface ILocationStockService {

    /**
     * 分页查询库存明细列表。
     */
    TableDataInfo<LocationStockVo> queryPageList(LocationStockQuery query, PageQuery pageQuery);

    /**
     * 查询库存明细列表（不分页，给导出 / 下游下拉用）。
     */
    List<LocationStockVo> queryList(LocationStockQuery query);

    /**
     * 根据 ID 查询单条。
     */
    LocationStockVo queryById(Long id);

    /**
     * 新增库存明细（保留供后续 ticket / 单测使用）。
     *
     * <p>3 维互斥校验（{@code productId} / {@code earNo} / {@code plotId} 三选一），
     * {@code operatorId} 走 {@link org.dromara.common.satoken.utils.LoginHelper#getUserId()} 注入（ADR-0007）。</p>
     *
     * @return 受影响行数
     */
    int insertByBo(LocationStockBo bo);

    /**
     * 软删除库存明细（支持批量）。
     */
    int deleteWithValidByIds(Collection<Long> ids);

    /**
     * 库存查询行「产品出库」（DJS-FIX-WMS-RALN-B）。
     *
     * <p>按 {@link StockOutBo#getId()} 取库存行的 {@code locationId + productId}，同一 {@code @Transactional}：
     * INSERT 出库流水（{@code inout_type='OT'} / {@code flow_type='other'}）+ 原子扣减 location_stock；
     * 库存不足 / 库位被盘点锁定 → 抛 ServiceException 回滚。</p>
     *
     * @return 新增流水行主键
     */
    Long productOut(StockOutBo bo);

}
