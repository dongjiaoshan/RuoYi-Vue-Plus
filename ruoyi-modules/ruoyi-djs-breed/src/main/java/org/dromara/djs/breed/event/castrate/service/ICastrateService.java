package org.dromara.djs.breed.event.castrate.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.castrate.domain.bo.CastrateBo;
import org.dromara.djs.breed.event.castrate.domain.query.CastrateQuery;
import org.dromara.djs.breed.event.castrate.domain.vo.CastrateRecordVo;

/**
 * 阉割事件 Service（BRD-EVENT-004 CASTRATE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
public interface ICastrateService {

    CastrateRecordVo recordCastrate(CastrateBo bo);

    TableDataInfo<CastrateRecordVo> queryPage(CastrateQuery query, PageQuery pageQuery);
}
