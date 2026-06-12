package org.dromara.djs.breed.farm.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.breed.farm.domain.vo.PenDetailVo;

import java.util.List;

/**
 * 农场栋舍栏位聚合 Mapper（对齐原型重做：栋舍列表三类栏位数 / 存栏 + 栏位详情猪只关联）。
 *
 * <p>与 {@link PigReferenceCheckMapper} 同范式——直接 native SQL，不走 MP entity 路径
 * （V1 单农场 tenant '1001'，不依赖租户拦截器；存活判定 {@code del_flag='0' AND current_status<>'END'}）。</p>
 *
 * @author djs
 * @since BRD-AD-PROTO-ALIGN
 */
@Mapper
public interface FarmStatMapper {

    /**
     * 统计某栋舍下指定类型的栏位数量（栋舍列表「大栏数量 / 限位栏数量 / 产床数量」列）。
     *
     * @param barnId  栋舍 ID
     * @param penType 栏位类型（big / stall / farrow）
     * @return 栏位数量
     */
    @Select("SELECT COUNT(*) FROM t_farm_barn_pen WHERE barn_id = #{barnId} AND pen_type = #{penType} AND del_flag = '0'")
    int countPenByType(@Param("barnId") Long barnId, @Param("penType") String penType);

    /**
     * 统计某栋舍当前存栏（存活猪只，排除终态 END）。
     *
     * @param barnId 栋舍 ID
     * @return 存栏头数
     */
    @Select("SELECT COUNT(*) FROM t_farm_pig_info WHERE barn_id = #{barnId} AND del_flag = '0' AND current_status <> 'END'")
    int countLivePigByBarn(@Param("barnId") Long barnId);

    /**
     * 查某栋舍指定类型的栏位详情（栏位详情 3-tab）。大栏返回头数；限位栏 / 产床返回占栏耳号；
     * 产床额外返回占栏母猪最近一次分娩的活产数（仔猪数）。
     *
     * @param barnId  栋舍 ID
     * @param penType 栏位类型（big / stall / farrow）
     * @return 栏位详情列表（按栏位 id 升序）
     */
    @Select("""
        SELECT p.id,
               p.pen_code  AS penCode,
               p.pen_name  AS penName,
               p.pen_type  AS penType,
               p.capacity  AS capacity,
               (SELECT COUNT(*) FROM t_farm_pig_info pi
                  WHERE pi.pen_id = p.id AND pi.del_flag = '0' AND pi.current_status <> 'END') AS headCount,
               (SELECT pi2.ear_no FROM t_farm_pig_info pi2
                  WHERE pi2.pen_id = p.id AND pi2.del_flag = '0' AND pi2.current_status <> 'END'
                  ORDER BY pi2.id LIMIT 1) AS earNo,
               (SELECT pi3.id FROM t_farm_pig_info pi3
                  WHERE pi3.pen_id = p.id AND pi3.del_flag = '0' AND pi3.current_status <> 'END'
                  ORDER BY pi3.id LIMIT 1) AS pigId,
               (SELECT f.live_born FROM t_farm_pig_farrow f
                  JOIN t_farm_pig_info ps ON ps.id = f.pig_id
                  WHERE ps.pen_id = p.id AND ps.del_flag = '0'
                  ORDER BY f.farrow_date DESC LIMIT 1) AS pigletCount
        FROM t_farm_barn_pen p
        WHERE p.barn_id = #{barnId} AND p.pen_type = #{penType} AND p.del_flag = '0'
        ORDER BY p.id
        """)
    List<PenDetailVo> selectPenDetail(@Param("barnId") Long barnId, @Param("penType") String penType);

}
