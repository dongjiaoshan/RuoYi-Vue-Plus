package org.dromara.djs.plant.organic.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.organic.domain.OrganicCropno;

/**
 * 果蔬证书-作物关联 Mapper（FIX-PLT-AD-INFO-LIST-001）。
 *
 * <p>无独立 VO（关联表纯连接表），直接复用 entity 作出参类型。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-INFO-LIST-001
 */
public interface OrganicCropnoMapper extends BaseMapperPlus<OrganicCropno, OrganicCropno> {
}
