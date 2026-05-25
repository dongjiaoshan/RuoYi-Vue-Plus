package org.dromara.djs.breed.event.growth.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.growth.domain.bo.GrowthBo;
import org.dromara.djs.breed.event.growth.domain.query.GrowthQuery;
import org.dromara.djs.breed.event.growth.domain.vo.PigGrowthVo;

import java.util.Collection;

/**
 * 生长记录 Service（BRD-EVENT-005 GROWTH）。
 *
 * <p>主入口：</p>
 * <ul>
 *   <li>{@link #addGrowthRecord} INSERT t_farm_pig_growth（不触发状态机）</li>
 *   <li>{@link #queryPage} 分页查询（admin 列表 / mp 历史）</li>
 *   <li>{@link #getById} 详情</li>
 *   <li>{@link #deleteByIds} 删除（3 天内可删，service 层强校验）</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-005
 */
public interface IPigGrowthService {

    PigGrowthVo addGrowthRecord(GrowthBo bo);

    TableDataInfo<PigGrowthVo> queryPage(GrowthQuery query, PageQuery pageQuery);

    PigGrowthVo getById(Long id);

    int deleteByIds(Collection<Long> ids);
}
