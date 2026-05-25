package org.dromara.djs.breed.event.slaughter.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.slaughter.domain.bo.SlaughterBo;
import org.dromara.djs.breed.event.slaughter.domain.query.SlaughterQuery;
import org.dromara.djs.breed.event.slaughter.domain.vo.PigMarketingVo;

/**
 * 出栏事件 Service（BRD-EVENT-004 SLAUGHTER）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
public interface ISlaughterService {

    PigMarketingVo recordSlaughter(SlaughterBo bo);

    TableDataInfo<PigMarketingVo> queryPage(SlaughterQuery query, PageQuery pageQuery);
}
