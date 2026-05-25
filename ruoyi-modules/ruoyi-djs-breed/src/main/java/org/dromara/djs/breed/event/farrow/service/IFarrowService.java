package org.dromara.djs.breed.event.farrow.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.farrow.domain.bo.FarrowBo;
import org.dromara.djs.breed.event.farrow.domain.query.FarrowQuery;
import org.dromara.djs.breed.event.farrow.domain.vo.PigFarrowVo;

import java.util.List;

/**
 * 母猪分娩事件 Service（BRD-EVENT-002 FARROW）。
 *
 * <p>主入口 {@link #recordFarrow}：INSERT t_farm_pig_farrow → fireEvent(FARROW)（PH → FM, parity +1）
 * → publish {@code PigFarrowEvent} 通知耳标域。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
public interface IFarrowService {

    /** mp 端分娩事件录入。 */
    PigFarrowVo recordFarrow(FarrowBo bo);

    /** admin 只读列表分页。 */
    TableDataInfo<PigFarrowVo> queryPage(FarrowQuery query, PageQuery pageQuery);

    /**
     * mp 端"分娩 picker"用：近 7 天 + 本人录入的分娩，按 farrow_date 倒序，含 tagged / remaining。
     * 上限 50 条。
     */
    List<PigFarrowVo> queryRecent(Long operatorId, int limit);
}
