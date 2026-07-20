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
 * {@link #countPendingFarrows} / {@link #sumPendingPiglets} 给
 * DJS-FIX-MP-W22-003 mp breed home 徽标查"待贴 N 头"用。</p>
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
     * 待贴标 farrow 批数：仍有 live_born &gt; 已贴 pigletno 行数。
     *
     * <p>{@code t_farm_pig_farrow} 表无 {@code farrow_status} / {@code tagged_count} 字段，
     * 已贴数从 {@code t_farm_pig_pigletno} 反查（与 {@code statByFarrow} 同口径）。
     * 多租户过滤 mp 走 sa-token 默认 1001（V1 单租户，详 ADR-0001）。</p>
     */
    @Select("""
        SELECT COUNT(*) FROM t_farm_pig_farrow f
        WHERE f.del_flag = '0'
          AND f.tenant_id = '1001'
          AND COALESCE(f.live_born, 0) > (
            SELECT COUNT(*) FROM t_farm_pig_pigletno p
            WHERE p.farrow_id = f.id AND p.del_flag = '0'
          )
        """)
    Integer countPendingFarrows();

    /**
     * 待贴标仔猪总头数：SUM(live_born - 已贴 pigletno 数)。
     *
     * <p>用 LEFT JOIN + GROUP BY 计算每批分娩剩余头数，再 SUM。返 0 时由 controller
     * 转 wd-badge value=0 mp 端自动隐藏徽标。</p>
     */
    @Select("""
        SELECT COALESCE(SUM(remain), 0) FROM (
          SELECT GREATEST(0, COALESCE(f.live_born, 0) - COALESCE(t.tagged, 0)) AS remain
          FROM t_farm_pig_farrow f
          LEFT JOIN (
            SELECT farrow_id, COUNT(*) AS tagged
            FROM t_farm_pig_pigletno
            WHERE del_flag = '0'
            GROUP BY farrow_id
          ) t ON t.farrow_id = f.id
          WHERE f.del_flag = '0'
            AND f.tenant_id = '1001'
        ) x
        """)
    Integer sumPendingPiglets();
}
