package org.dromara.djs.breed.event.eartag.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletnoVo;

/**
 * 仔猪耳号打标记录 Mapper（BRD-EVENT-003）。
 *
 * <p>{@link PigletnoVo} 给 admin 只读列表分页用；service 内部组合 pig+pigletno 的
 * {@link org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo} 不走此 mapper。</p>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
public interface PigPigletnoMapper extends BaseMapperPlus<PigPigletno, PigletnoVo> {
}
