package org.dromara.djs.breed.event.breeding.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.breeding.domain.bo.BreedingBo;
import org.dromara.djs.breed.event.breeding.domain.query.BreedingQuery;
import org.dromara.djs.breed.event.breeding.domain.vo.PigBreedingVo;

/**
 * 配种事件 Service（BRD-EVENT-002 BREED）。
 *
 * <p>主入口 {@link #recordBreeding}：INSERT t_farm_pig_breeding →
 * {@code pigCoreService.fireEvent(BREED)} 推动状态机 HB/DN/LC/KH/FQ → PZ
 * 并由 {@code applyEventSideEffects + applyBreedingCounters} 更新 mating_id +
 * mating_count + last_mating_date（D5 audit a-1）。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
public interface IBreedingService {

    PigBreedingVo recordBreeding(BreedingBo bo);

    TableDataInfo<PigBreedingVo> queryPage(BreedingQuery query, PageQuery pageQuery);
}
