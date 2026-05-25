package org.dromara.djs.breed.event.farrow.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.domain.vo.PigFarrowVo;

/**
 * 母猪分娩 mapper（BRD-EVENT-002）。
 *
 * <p>额外暴露 {@link #selectBoarEarByBreedingId} 给 BRD-EVENT-003 仔猪耳标做"配种 → 父猪耳号"反查。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
public interface PigFarrowMapper extends BaseMapperPlus<PigFarrow, PigFarrowVo> {

    /**
     * 根据配种记录 ID 反查父猪（公猪）耳号。
     *
     * <p>{@code t_farm_pig_breeding.boar_ear_no} 在配种环节填入；
     * 自然配种通常有值，人工授精 / 输精时也可能为空（DDL 列允许 null）。</p>
     */
    @Select("SELECT boar_ear_no FROM t_farm_pig_breeding WHERE id = #{breedingId} AND del_flag = '0'")
    String selectBoarEarByBreedingId(@Param("breedingId") Long breedingId);
}
