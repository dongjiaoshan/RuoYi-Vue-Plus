package org.dromara.djs.breed.event.eartag.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
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
    /**
     * 这一窝有没有过仔猪档案 —— <b>连软删的行一起数</b>（D-0116）。
     *
     * <p>仔猪死亡登记会把 {@code t_farm_pig_pigletno} 行软删。判断「这是不是一窝从没贴过标的老窝」
     * 时不能只看未删行：一窝逐头贴过标的仔猪全部死亡之后，未删行也是 0，会被当成零档案老窝再补建一遍，
     * 凭空造出一窝幽灵档案。「这一窝生过哪几头」是事实，删不掉。
     * 与 {@code PigFarrowMapper.NO_PIGLET_ARCHIVE} 同口径。</p>
     *
     * @param farrowId 分娩记录 id
     * @return 该窝历史上建过的仔猪档案行数（含已软删）
     */
    @Select("SELECT COUNT(*) FROM t_farm_pig_pigletno WHERE farrow_id = #{farrowId}")
    int countByFarrowIgnoreDeleted(@Param("farrowId") Long farrowId);

}
