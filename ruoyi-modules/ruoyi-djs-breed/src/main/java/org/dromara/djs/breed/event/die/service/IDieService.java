package org.dromara.djs.breed.event.die.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.die.domain.bo.DieBo;
import org.dromara.djs.breed.event.die.domain.query.DieQuery;
import org.dromara.djs.breed.event.die.domain.vo.PigDeathVo;

/**
 * 死亡事件 Service（BRD-EVENT-004 DIE）。
 *
 * <p>主入口 {@link #recordDie}：INSERT t_farm_pig_death →
 * {@code pigCoreService.fireEvent(DIE)} 推进状态机 (非 END) → END，end_reason=DEAD。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
public interface IDieService {

    PigDeathVo recordDie(DieBo bo);

    TableDataInfo<PigDeathVo> queryPage(DieQuery query, PageQuery pageQuery);
}
