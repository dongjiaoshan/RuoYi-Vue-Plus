package org.dromara.djs.breed.event.nullreturn.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.nullreturn.domain.bo.NullReturnBo;
import org.dromara.djs.breed.event.nullreturn.domain.query.NullReturnQuery;
import org.dromara.djs.breed.event.nullreturn.domain.vo.PigAbnormalVo;

/**
 * 返空事件 Service（BRD-EVENT-002 NULL_RETURN）。
 *
 * <p>状态机源态必须 PH（service 提前校验 + 状态机兜底）；按 abnormalType 分流到 LC/KH/FQ。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
public interface INullReturnService {

    PigAbnormalVo recordNullReturn(NullReturnBo bo);

    TableDataInfo<PigAbnormalVo> queryPage(NullReturnQuery query, PageQuery pageQuery);
}
