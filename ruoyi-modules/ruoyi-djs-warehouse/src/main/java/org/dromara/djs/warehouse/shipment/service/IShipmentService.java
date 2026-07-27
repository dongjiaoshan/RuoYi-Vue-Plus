package org.dromara.djs.warehouse.shipment.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.shipment.domain.bo.ShipmentCheckBo;
import org.dromara.djs.warehouse.shipment.domain.query.ShipmentQuery;
import org.dromara.djs.warehouse.shipment.domain.vo.AvailableProductionVo;
import org.dromara.djs.warehouse.shipment.domain.vo.ShipDemandVo;
import org.dromara.djs.warehouse.shipment.domain.vo.ShipStoreVo;
import org.dromara.djs.warehouse.shipment.domain.vo.ShipmentVo;

import java.util.List;

/**
 * 发货流水 Service（WMS-SHIP-001）。
 *
 * <p>3 表事务：UPDATE product_production + INSERT shipment + INSERT stock_flow + publishEvent</p>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
public interface IShipmentService {

    /**
     * mp 工人清点确认（核心写入路径）。
     *
     * <p>事务步骤（{@code @Transactional(rollbackFor = Exception.class)}）：</p>
     * <ol>
     *   <li>校验 demand 状态 ∈ {CONFIRMED, IN_PRODUCTION, PARTIAL_SHIPPED}</li>
     *   <li>校验 product_production 存在 + is_delivery_check=0（乐观锁，affectedRows 校验）</li>
     *   <li>UPDATE product_production SET is_delivery_check=1, delivery_check_time=NOW</li>
     *   <li>INSERT shipment 主表（status=shipped，业务码走 BizCodeGenerator.SHIP_NO）</li>
     *   <li>INSERT stock_flow (flow_type='ship_out', inout_type='OT', change_quantity=-X, demand_id 关联)</li>
     *   <li>publishEvent(ShipmentConfirmedEvent) — D14 listener 消费触发 demand.shipped_count + transition</li>
     * </ol>
     *
     * @return 新建 shipment.id
     */
    Long confirmCheck(ShipmentCheckBo bo);

    /**
     * admin 列表（分页 + 多维度筛选）。
     */
    TableDataInfo<ShipmentVo> queryPageList(ShipmentQuery query, PageQuery pageQuery);

    /**
     * 不分页（导出用）。
     */
    List<ShipmentVo> queryList(ShipmentQuery query);

    /**
     * 详情。
     */
    ShipmentVo queryById(Long id);

    /**
     * mp 工人查询某需求下"待清点"产品列表（{@code is_delivery_check=0}）。
     *
     * @param demandId 需求 ID
     * @return 按 produce_date 倒序的轻量 VO
     */
    List<AvailableProductionVo> listAvailableProductions(Long demandId);

    /**
     * 某需求当前"可清点"（待分配）产品数（不建 VO 仅计数，listForDispatch picker 用）。
     * 口径同 {@link #listAvailableProductions} 匹配逻辑。
     *
     * @param demandId 需求 ID（null / 需求不存在 → 0）
     * @return 可清点产品条数
     */
    int countAvailableProductionsForDemand(Long demandId);

    /**
     * 门店维度待发货聚合（发货月台 IA 进页 = 门店列表，D12X-MP-SHIPDOCK-IA-001）。
     *
     * <p>扫所有 SHIPPABLE 状态的 demand（CONFIRMED / PARTIAL_SHIPPED；IN_PRODUCTION 存量兼容）
     * {@code group by store_id}，每门店算待发需求数 + 待发产品种类数（distinct product_id）+ 总量。
     * 门店名走 {@code StoreMapper} 批量填充（无 N+1）。只列还有待发 demand 的门店。</p>
     *
     * <p>仅含「当天有需求」的门店：按业务日期 {@code demand_date = 今天}（Asia/Shanghai）过滤，
     * 非 create_time（D-FIX-24 决策 #6a）。</p>
     *
     * @return 按 storeName 稳定排序的门店列表
     */
    List<ShipStoreVo> listPendingStores();

    /**
     * 单门店当天发货聚合（门店货物页头用，与 {@link #listPendingStores} 的门店卡同一个算法）。
     *
     * <p>返回该门店当天的产品种类数 + 需求满足率等聚合指标：满足率 = 每个产品种类
     * {@code min(1, 生产量 ÷ 需求量)} 的平均 × 100。列表卡与详情页头读同一份后端结果，
     * 避免两端各算一份导致数字对不上。</p>
     *
     * @param storeId 门店 ID（必填）
     * @return 该门店当天聚合；当天无需求时返回种类 0 / 满足率 0.00 的空壳 VO
     */
    ShipStoreVo queryStoreSummary(Long storeId);

    /**
     * 某门店的待发需求单列表 + 各需求可发产品清单（门店发货子页，D12X-MP-SHIPDOCK-IA-001）。
     *
     * <p>返回该门店所有 SHIPPABLE demand，每个 demand 内嵌按业态 + store_id 匹配出的可发 production
     * 清单（复用 {@link #listAvailableProductions} 同一匹配逻辑）。mp 子页按需求单分组渲染，
     * 每需求一个"出车发货"按钮调 {@link #confirmCheck}（单 demand 单事务）。</p>
     *
     * @param storeId 门店 ID
     * @return 该门店待发需求单列表（demand 维度，含可发产品明细）
     */
    List<ShipDemandVo> listStorePendingDemands(Long storeId);
}
