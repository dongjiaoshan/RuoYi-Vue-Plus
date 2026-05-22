package org.dromara.djs.breed.event.eartag.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;

/**
 * 仔猪耳号打标记录 Mapper（BRD-EVENT-003）。
 *
 * <p>无独立 VO（{@link org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo}
 * 是 service 层 pig+pigletno 组合产物），用 entity 自身做 selectVoList 返回类型。</p>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
public interface PigPigletnoMapper extends BaseMapperPlus<PigPigletno, PigPigletno> {
}
