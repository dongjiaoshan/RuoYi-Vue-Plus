package org.dromara.djs.breed.event.farrow.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.domain.vo.PigFarrowVo;

/**
 * 母猪分娩 mapper（BRD-EVENT-002）。
 *
 * <p>额外暴露 {@link #selectBoarEarByBreedingId} 给 BRD-EVENT-003 仔猪耳标做"配种 → 父猪耳号"反查；
 * {@link #countPendingFarrows} / {@link #sumPendingPiglets} 给 mp breed home 徽标查
 * "未断奶 N 窝 / N 头"用（V6 行243 口径，与选窝列表同源）。</p>
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

    /**
     * 未断奶窝数（mp breed home「仔猪耳号」卡徽标的批数，V6 行243 口径）。
     *
     * <p>口径与 {@code IFarrowService.queryPendingLitters} 一致——<b>母猪未断奶</b>即在列表里，
     * 一断奶就消失。V6 行242 起整窝在分娩提交时自动建档，旧的「live_born 大于已打标数」口径
     * 会恒为 0，徽标与列表两边对不上，故一并换掉。</p>
     *
     * <p>{@code t_farm_pig_farrow} 无 status 冗余列，全表动态聚合；V1 单租户 tenant_id 写死 '1001'
     * （详 ADR-0001），子查询显式对齐租户列。</p>
     */
    @Select("""
        SELECT COUNT(*) FROM t_farm_pig_farrow f
        WHERE f.del_flag = '0'
          AND f.tenant_id = '1001'
          AND NOT EXISTS (
            SELECT 1 FROM t_farm_pig_weaning w
            WHERE w.farrow_id = f.id AND w.del_flag = '0' AND w.tenant_id = f.tenant_id
          )
        """)
    Integer countPendingFarrows();

    /**
     * 未断奶窝的仔猪总头数 = SUM(live_born)（mp breed home 徽标数字，V6 行243 口径）。
     *
     * <p>返 0 时 controller 转 wd-badge value=0，mp 端自动隐藏徽标。</p>
     */
    @Select("""
        SELECT COALESCE(SUM(COALESCE(f.live_born, 0)), 0) FROM t_farm_pig_farrow f
        WHERE f.del_flag = '0'
          AND f.tenant_id = '1001'
          AND NOT EXISTS (
            SELECT 1 FROM t_farm_pig_weaning w
            WHERE w.farrow_id = f.id AND w.del_flag = '0' AND w.tenant_id = f.tenant_id
          )
        """)
    Integer sumPendingPiglets();
}
