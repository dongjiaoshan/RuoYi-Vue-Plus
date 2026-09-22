package org.dromara.djs.breed.event.farrow.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.domain.vo.PigFarrowVo;
import org.dromara.djs.breed.event.weaning.mapper.PigWeaningMapper;

/**
 * 母猪分娩 mapper（BRD-EVENT-002）。
 *
 * <p>额外暴露 {@link #selectBoarEarByBreedingId} 给 BRD-EVENT-003 仔猪耳标做"配种 → 父猪耳号"反查；
 * {@link #countPendingFarrows} / {@link #sumPendingPiglets} 给 mp breed home 徽标查
 * "待订正出生重 N 窝 / N 头"用（判据 {@link #PENDING_BIRTH_WEIGHT}，与选窝列表同源）。</p>
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
     * 本窝一头仔猪档案都没有（整窝从未贴标）。别名契约：调用方 SQL 里 {@code f} = {@code t_farm_pig_farrow}。
     *
     * <p>🔴 <b>刻意不过滤 {@code pn.del_flag}</b>：仔猪死亡登记会把 {@code t_farm_pig_pigletno} 那一行软删
     * （{@code DieServiceImpl}）。带上 {@code del_flag='0'} 的话，一窝逐头贴过标的仔猪全部死亡之后，
     * 这一窝在 SQL 上就伪装成「零档案窝」—— 母猪继续列在断奶待办里，点进去后端返 0 条逐头行、
     * mp 回落成按 {@code live_born} 铺匿名行，工人照着提交就录出一窝幽灵断奶头数，污染窝均断奶数 /
     * 产房损失率 / PSY（独立验收 2026-09-22 实测 10 头全死的窝录出 10 头断奶并转 DN）。
     * 「这一窝生过哪几头」是事实，删不掉；D-0110 要照顾的真·老窝连软删行都没有，不受影响。
     * 根因（死亡登记该不该软删 pigletno）另挂 D-0116 等拍板，这里先把入口堵上。</p>
     */
    String NO_PIGLET_ARCHIVE = "NOT EXISTS (SELECT 1 FROM t_farm_pig_pigletno pn"
        + " WHERE pn.farrow_id = f.id AND pn.tenant_id = f.tenant_id)";

    /** 本窝没有任何断奶记录。别名契约同 {@link #NO_PIGLET_ARCHIVE}。 */
    String NO_WEANING_RECORD = "NOT EXISTS (SELECT 1 FROM t_farm_pig_weaning w"
        + " WHERE w.farrow_id = f.id AND w.del_flag = '0' AND w.tenant_id = f.tenant_id)";

    /**
     * 「这一窝还要出现在出生重订正页上」的唯一判据 —— 选窝列表与 mp 徽标共用一串（D-0112 + D-0110）。
     *
     * <p>两种情况之一即列出：</p>
     * <ol>
     *   <li><b>窝里还有没断奶的仔猪</b>（D-0112，甲方 2026-09-22 选②按仔猪算）——
     *       原判据是「本窝有没有断奶记录」，断第一头整窝就消失，剩下还在哺乳的仔猪出生重再也改不了；</li>
     *   <li><b>还没建档、也没断过奶的老窝</b>（D-0110，甲方 2026-09-22 选①自动补建）——
     *       它一头仔猪档案都没有，第 1 条天然命中不到；不留这一支，点开即补建就永远触发不了。
     *       已断过奶的零档案老窝不在此列（不给早已离场的猪补建档案）。</li>
     * </ol>
     */
    String PENDING_BIRTH_WEIGHT = "(" + PigWeaningMapper.UNWEANED_PIGLET_EXISTS
        + " OR (" + NO_PIGLET_ARCHIVE + " AND " + NO_WEANING_RECORD + "))";

    /**
     * 出生重订正页待处理窝数（mp breed home「仔猪耳号」卡徽标的批数）。
     *
     * <p>口径 = {@link #PENDING_BIRTH_WEIGHT}，与选窝列表 {@code IFarrowService.queryPendingLitters}
     * 同源：窝里还有没断奶的仔猪就在列表里，整窝断完才消失。</p>
     *
     * <p>{@code t_farm_pig_farrow} 无 status 冗余列，全表动态聚合；V1 单租户 tenant_id 写死 '1001'
     * （详 ADR-0001），子查询显式对齐租户列。</p>
     */
    @Select("SELECT COUNT(*) FROM t_farm_pig_farrow f"
        + " WHERE f.del_flag = '0' AND f.tenant_id = '1001'"
        + " AND " + PENDING_BIRTH_WEIGHT)
    Integer countPendingFarrows();

    /**
     * 出生重订正页待处理仔猪头数（mp breed home 徽标数字）。
     *
     * <p>已建档的窝数「还没断奶的头数」（D-0112 —— 断掉的那几头已经不能再改出生重，不该继续计数）；
     * 零档案老窝没有逐头行可数，退回窝级 {@code live_born}（点开时按这个数补建，D-0110）。</p>
     *
     * <p>返 0 时 controller 转 wd-badge value=0，mp 端自动隐藏徽标。</p>
     */
    @Select("SELECT COALESCE(SUM("
        + " CASE WHEN " + NO_PIGLET_ARCHIVE
        + " THEN COALESCE(f.live_born, 0)"
        + " ELSE " + PigWeaningMapper.UNWEANED_PIGLET_COUNT
        + " END), 0)"
        + " FROM t_farm_pig_farrow f"
        + " WHERE f.del_flag = '0' AND f.tenant_id = '1001'"
        + " AND " + PENDING_BIRTH_WEIGHT)
    Integer sumPendingPiglets();
}
