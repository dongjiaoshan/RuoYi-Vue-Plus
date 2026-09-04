package org.dromara.djs.plant.farmmap.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.farmmap.domain.FarmMapRegion;

/**
 * 农场地图绑定 Mapper（PLT-FARMMAP-001）。
 *
 * <p>VO 类型直接用实体：本表只有 regionKey + plotId 两个业务列，没有需要裁剪的重字段，
 * 对外的 {@code FarmMapRegionVo} 是 service 层 join 地块后现拼的，不由 mapper 直出。</p>
 *
 * @author djs
 * @since PLT-FARMMAP-001
 */
public interface FarmMapRegionMapper extends BaseMapperPlus<FarmMapRegion, FarmMapRegion> {
}
