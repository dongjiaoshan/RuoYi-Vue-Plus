package org.dromara.djs.warehouse.thirdphase.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.thirdphase.domain.ThirdPhaseIn;
import org.dromara.djs.warehouse.thirdphase.domain.vo.ThirdPhaseInVo;

/**
 * 三期作物入库记录 Mapper（V6 row88）。
 *
 * <p>无手写 SQL：列表只按 {@code in_time} 区间 + {@code crop_name} 模糊筛本表，
 * 展示字段全是写入时落好的快照，MP wrapper 足够。</p>
 *
 * @author djs
 * @since V6-R88
 */
public interface ThirdPhaseInMapper extends BaseMapperPlus<ThirdPhaseIn, ThirdPhaseInVo> {
}
