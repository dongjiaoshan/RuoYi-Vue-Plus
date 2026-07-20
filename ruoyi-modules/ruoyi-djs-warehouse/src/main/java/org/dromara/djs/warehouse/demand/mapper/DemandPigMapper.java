package org.dromara.djs.warehouse.demand.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.demand.domain.DemandPig;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPigVo;

/**
 * 需求↔猪只关联 Mapper（WMS-DEMAND-001，白条业态用）。
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
public interface DemandPigMapper extends BaseMapperPlus<DemandPig, DemandPigVo> {
}
