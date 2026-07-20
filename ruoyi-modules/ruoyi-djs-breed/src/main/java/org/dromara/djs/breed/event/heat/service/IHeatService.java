package org.dromara.djs.breed.event.heat.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.heat.domain.bo.HeatBo;
import org.dromara.djs.breed.event.heat.domain.query.HeatQuery;
import org.dromara.djs.breed.event.heat.domain.vo.PigHeatVo;

/**
 * 查情事件 Service（BRD-EVENT-002 OESTRUS）。
 *
 * <p>处理（确认妊娠不切态，见 ADR-0010）：</p>
 * <ul>
 *   <li>{@code isPregnantConfirmed=true} → fireEvent(OESTRUS)，状态仍保持 PZ（仅落审计记录）；</li>
 *   <li>{@code isPregnantConfirmed=false} → 仅写记录，不调状态机。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
public interface IHeatService {

    PigHeatVo recordHeat(HeatBo bo);

    TableDataInfo<PigHeatVo> queryPage(HeatQuery query, PageQuery pageQuery);
}
