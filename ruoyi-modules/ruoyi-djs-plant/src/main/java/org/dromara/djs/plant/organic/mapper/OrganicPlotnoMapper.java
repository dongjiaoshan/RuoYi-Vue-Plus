package org.dromara.djs.plant.organic.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.organic.domain.OrganicPlotno;

/**
 * 证书-地块关联 Mapper（PLT-MD-003）。
 *
 * <p>无独立 VO（关联表纯连接表），直接复用 entity 作出参类型。</p>
 *
 * @author djs
 * @since PLT-MD-003
 */
public interface OrganicPlotnoMapper extends BaseMapperPlus<OrganicPlotno, OrganicPlotno> {
}
