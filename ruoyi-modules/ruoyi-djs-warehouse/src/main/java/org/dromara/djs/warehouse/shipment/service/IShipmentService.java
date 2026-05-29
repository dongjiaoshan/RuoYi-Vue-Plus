package org.dromara.djs.warehouse.shipment.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.shipment.domain.bo.ShipmentCheckBo;
import org.dromara.djs.warehouse.shipment.domain.query.ShipmentQuery;
import org.dromara.djs.warehouse.shipment.domain.vo.AvailableProductionVo;
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
}
