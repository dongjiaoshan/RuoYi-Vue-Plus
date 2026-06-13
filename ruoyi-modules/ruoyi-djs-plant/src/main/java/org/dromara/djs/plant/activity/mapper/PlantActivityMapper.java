package org.dromara.djs.plant.activity.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.activity.domain.PlantActivity;

/**
 * 采摘活动 Mapper（FIX-PLT-HARVEST-ACTIVITY-001）。
 *
 * <p>主体走 {@link BaseMapperPlus} 通用 CRUD；
 * 列表 {@code cropName} / {@code teamName} 由 service 层批量 enrich。</p>
 *
 * @author djs
 * @since FIX-PLT-HARVEST-ACTIVITY-001
 */
public interface PlantActivityMapper extends BaseMapperPlus<PlantActivity, PlantActivity> {
}
