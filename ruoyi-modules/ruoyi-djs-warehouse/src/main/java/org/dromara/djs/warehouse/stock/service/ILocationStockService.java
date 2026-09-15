package org.dromara.djs.warehouse.stock.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.stock.domain.bo.LocationStockBo;
import org.dromara.djs.warehouse.stock.domain.bo.StockOutBo;
import org.dromara.djs.warehouse.stock.domain.bo.StockTransferBo;
import org.dromara.djs.warehouse.stock.domain.query.LocationStockQuery;
import org.dromara.djs.warehouse.stock.domain.vo.LocationStockVo;
import org.dromara.djs.warehouse.stock.domain.vo.StockBasketVo;

import java.util.Collection;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
     * <p>按 {@link StockOutBo#getStockIds()} 跨篮先进先出扣减，逐篮取 {@code locationId + productId}，同一 {@code @Transactional}：
     * INSERT 出库流水（{@code inout_type='OT'} / {@code flow_type='backstage_out'}）+ 原子扣减 location_stock；
     * 库存不足 / 库位被盘点锁定 → 抛 ServiceException 回滚。</p>
     *
     * @return 新增流水行主键
     */
    List<Long> productOut(StockOutBo bo);

    /**
     * 计数类单位出库量必须是整数（V6 row143）。
     *
     * <p><b>只给 admin HTTP 入口调用，故意不放进 {@link #productOut}</b>：毛菜间出库
     * （{@code VegOutServiceImpl}）是跨 bean 直接调 {@code productOut} 的内部路径，
     * 它的量由上游工序算出来，不该被这道「人工录入」的闸拦。放在入口层 = 只约束人手填的那条路。</p>
     *
     * <p>单位口径见 {@link org.dromara.djs.warehouse.common.QuantityUnitRule}
     * （与前端 {@code utils/weight.ts#isCountingUnit} 同一份名单）。</p>
     *
     * @throws org.dromara.common.core.exception.ServiceException 单位是计数类且数量带小数
     */
    void assertManualOutQuantity(StockOutBo bo);

    /**
     * 计数类单位转移量必须是整数（V6 row143）——「猪肉库位转移」入口闸，口径同
     * {@link #assertManualOutQuantity}。前端 {@code PigTransferDialog.vue} 已按同一规则拦，
     * 这里补上后端，避免「前端拦、接口不拦」（那正是本条要消灭的形态）。
     *
     * @throws org.dromara.common.core.exception.ServiceException 单位是计数类且数量带小数
     */
    void assertManualTransferQuantity(StockTransferBo bo);

    /**
     * 库存查询行「猪肉转移」：猪肉鲜品库 → 冻品库（WS13 / row143）。
     *
     * <p>按 {@link StockTransferBo#getStockIds()} 跨篮先进先出取源库存行的 {@code locationId + productId + 当前库存}，
     * 校验源库位为「猪肉鲜品库」、产品业态为 pork、转移量 ≤ 当前库存；同一 {@code @Transactional}：</p>
     * <ol>
     *   <li>源侧（猪肉鲜品库）：按行 id 原子扣减 + INSERT 转移出库流水（{@code flow_type=transfer_out}）；</li>
     *   <li>目标侧（冻品库）：同产品 UPSERT 加库存 + INSERT 转移入库流水（{@code flow_type=transfer_in}）。</li>
     * </ol>
     *
     * <p>库存不足 / 库位被盘点锁定 / 目标冻品库未配置 → 抛 ServiceException 回滚。</p>
     *
     * @return 转移出库流水行主键
     */
    List<Long> pigTransfer(StockTransferBo bo);

    /**
     * 把一次出库 / 转移的总量按<b>先进先出</b>摊到一组库存篮上（V6 row223/row224 / D-0068）。
     *
     * <p>入参顺序即先进先出顺序（列表接口按建篮时间升序给出）。空篮跳过、总量不足直接
     * fail-fast，不会返回一份「扣一半」的计划。</p>
     *
     * @param stockIds 库存篮 id 组，先进先出序
     * @param quantity 总量
     * @return 篮 id → 该篮应扣数量，保序；不含分配到 0 的篮
     */
    Map<Long, BigDecimal> allocateFifo(List<Long> stockIds, BigDecimal quantity);

    /**
     * 合并行背后的各篮明细（V6 row223 / D-0068「各篮明细（入库时间+重量）下沉到详情里看」）。
     *
     * <p>按列表那一行给出的 {@code stockIds} 原样取，先进先出序（与出库扣减顺序一致，
     * 工人看到的第一篮就是下次会先被扣的那篮）。</p>
     *
     * @param stockIds 库存篮 id 组
     * @return 各篮的建篮时间 / 库存量 / 最近盘点 / 备注
     */
    List<StockBasketVo> listBaskets(List<Long> stockIds);

    /**
     * 查询当前库存中实际存在的猪只耳号（去重，供库存查询页耳号下拉用，row152-2）。
     *
     * <p>取 {@code t_warehouse_location_stock} 中 {@code ear_no} 非空的去重列表；
     * {@code locationId} 非空时按库位过滤。</p>
     *
     * @param locationId 库位 ID（可空，不传则取全部库存）
     * @return 去重后的耳号列表
     */
    List<String> listStockEarNos(Long locationId);

}
