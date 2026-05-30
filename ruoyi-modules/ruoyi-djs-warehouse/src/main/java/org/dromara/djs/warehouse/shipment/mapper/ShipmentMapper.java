package org.dromara.djs.warehouse.shipment.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.shipment.domain.Shipment;
import org.dromara.djs.warehouse.shipment.domain.vo.ShipmentVo;

/**
 * 发货流水 Mapper（WMS-SHIP-001）。
 *
 * <p>{@link BaseMapperPlus#selectVoPage(com.baomidou.mybatisplus.core.metadata.IPage,
 * com.baomidou.mybatisplus.core.conditions.Wrapper) selectVoPage} 自动按 AutoMapper 转
 * {@link ShipmentVo}。</p>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
public interface ShipmentMapper extends BaseMapperPlus<Shipment, ShipmentVo> {
}
