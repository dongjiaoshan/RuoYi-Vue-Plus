package org.dromara.djs.breed.core.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.PigVo;

/**
 * 猪只信息 Mapper（BRD-CORE-001）。
 */
public interface PigMapper extends BaseMapperPlus<Pig, PigVo> {

    /**
     * 按耳号简版查 pigId（mp 端二选一支持 — 工人不输 19 位 snowflake，直接输耳号）。
     * <p>返回 null 表示耳号不存在（被软删或未引种）。</p>
     */
    @Select("SELECT id FROM t_farm_pig_info WHERE ear_no = #{earNo} AND del_flag = '0' LIMIT 1")
    Long selectIdByEarNo(@Param("earNo") String earNo);

    /**
     * 按耳号查 current_status（跨模块只读：暴露给 {@code IPigQueryService} 用，
     * 不要在养殖域外直接 inject 本 mapper）。
     * <p>返回 null 表示耳号不存在或已软删。</p>
     */
    @Select("SELECT current_status FROM t_farm_pig_info WHERE ear_no = #{earNo} AND del_flag = '0' LIMIT 1")
    String selectCurrentStatusByEarNo(@Param("earNo") String earNo);
}
