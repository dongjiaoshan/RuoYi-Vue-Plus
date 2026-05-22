package org.dromara.djs.breed.event.eartag.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.event.eartag.domain.FarrowRef;

/**
 * 分娩记录只读 mapper（BRD-EVENT-003 临时依赖）。
 *
 * <p>BRD-EVENT-002 落地后由该 ticket 提供 PigFarrowMapper 替换；本 mapper 仅做读，
 * 不持有 INSERT/UPDATE/DELETE 业务方法。</p>
 *
 * <p>额外暴露 {@link #selectBoarEarByBreedingId} 用于"由 breeding 找父猪耳号"，
 * 避免本 ticket 多引入 breeding entity（同样会与 BRD-EVENT-002 冲突）。</p>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
public interface FarrowRefMapper extends BaseMapperPlus<FarrowRef, FarrowRef> {

    /**
     * 根据配种记录 ID 反查父猪（公猪）耳号。
     *
     * <p>{@code t_farm_pig_breeding.boar_ear_no} 在配种环节填入；
     * 自然配种通常有值，人工授精 / 输精时也可能为空（DDL 列允许 null）。</p>
     */
    @Select("SELECT boar_ear_no FROM t_farm_pig_breeding WHERE id = #{breedingId} AND del_flag = '0'")
    String selectBoarEarByBreedingId(@Param("breedingId") Long breedingId);
}
