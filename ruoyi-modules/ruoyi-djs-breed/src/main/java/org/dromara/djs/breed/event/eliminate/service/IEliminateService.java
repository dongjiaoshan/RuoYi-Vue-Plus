package org.dromara.djs.breed.event.eliminate.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.eliminate.domain.bo.EliminateBo;
import org.dromara.djs.breed.event.eliminate.domain.query.EliminateQuery;
import org.dromara.djs.breed.event.eliminate.domain.vo.PigCullingVo;

/**
 * 淘汰事件 Service（BRD-EVENT-004 ELIMINATE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
public interface IEliminateService {

    PigCullingVo recordEliminate(EliminateBo bo);

    TableDataInfo<PigCullingVo> queryPage(EliminateQuery query, PageQuery pageQuery);
}
