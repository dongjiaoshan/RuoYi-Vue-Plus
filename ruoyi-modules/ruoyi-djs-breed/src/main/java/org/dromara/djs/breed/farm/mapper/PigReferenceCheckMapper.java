package org.dromara.djs.breed.farm.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 猪只反向引用校验 Mapper（BRD-MD-002 删除约束）。
 *
 * <p>用于：</p>
 * <ul>
 *   <li>{@link org.dromara.djs.breed.farm.service.impl.BarnServiceImpl#deleteWithValidByIds}
 *       —— 删栋舍前查 {@code t_farm_pig_info WHERE barn_id IN (...) AND del_flag='0'}</li>
 *   <li>{@link org.dromara.djs.breed.farm.service.impl.PenServiceImpl#deleteWithValidByIds}
 *       —— 删栏位前查 {@code t_farm_pig_info WHERE pen_id IN (...) AND del_flag='0'}
 *       （注：实际表字段名为 {@code pen_id}，doc/06 描述的 {@code current_pen_id} 即此字段；
 *       已 raise 到 _open-issues 由日终 closing 对齐 doc/06 字段命名）</li>
 * </ul>
 *
 * <p>本 mapper 不走 MP entity 路径——{@code t_farm_pig_info} 由 D05 BRD-CORE-001 才建 entity，
 * 本 ticket 直接 native SQL count 即可。D05 wire 完后可改走 PigInfoMapper.selectCount(wrapper)。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Mapper
public interface PigReferenceCheckMapper {

    /**
     * 按 barn_id 统计未删除猪只数。
     *
     * @param barnId 栋舍 ID
     * @return 未删除猪只数
     */
    @Select("SELECT COUNT(*) FROM t_farm_pig_info WHERE barn_id = #{barnId} AND del_flag = '0'")
    long countActiveByBarnId(@Param("barnId") Long barnId);

    /**
     * 按 pen_id 统计未删除猪只数。
     *
     * @param penId 栏位 ID
     * @return 未删除猪只数
     */
    @Select("SELECT COUNT(*) FROM t_farm_pig_info WHERE pen_id = #{penId} AND del_flag = '0'")
    long countActiveByPenId(@Param("penId") Long penId);

    /**
     * 按 pen_id 统计当前在栏头数（容量口径）：未删除 + 非终止态（current_status != 'END'）+ 排除仔猪（pig_type='piglet'）。
     *
     * <p>用于栏位容量占用计算（{@code usedCount}）与超容校验。仔猪入栏不计容量、不占名额。</p>
     *
     * @param penId 栏位 ID
     * @return 计入容量的在栏头数
     */
    @Select("SELECT COUNT(*) FROM t_farm_pig_info "
        + "WHERE pen_id = #{penId} AND del_flag = '0' "
        + "AND (current_status IS NULL OR current_status <> 'END') "
        + "AND pig_type <> 'piglet'")
    long countCapacityUsedByPenId(@Param("penId") Long penId);

}
