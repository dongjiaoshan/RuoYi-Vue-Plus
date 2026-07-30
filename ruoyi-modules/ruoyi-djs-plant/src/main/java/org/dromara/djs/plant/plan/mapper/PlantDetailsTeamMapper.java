package org.dromara.djs.plant.plan.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.plan.domain.PlantDetailsTeam;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 种植计划明细·班组多选中间表 Mapper（G1-TEAMS-MULTISELECT）。
 *
 * @author djs
 * @since G1-TEAMS-MULTISELECT
 */
public interface PlantDetailsTeamMapper extends BaseMapperPlus<PlantDetailsTeam, PlantDetailsTeam> {

    /**
     * 批量查询若干明细各角色的班组名（一次 SQL，enrich 用，禁 N+1）。
     *
     * @param detailIds {@code t_plant_plant_details.id} 集合（非空）
     * @return 每行 detailId(Long) / role(String) / teamId(Long) / teamName(String)，按 team_id 升序
     */
    @Select("<script>" +
        "SELECT dt.detail_id AS detailId, dt.role AS role, dt.team_id AS teamId, t.team_name AS teamName " +
        "FROM t_plant_details_team dt " +
        "LEFT JOIN t_plant_work_team t ON t.id = dt.team_id AND t.del_flag = '0' " +
        "WHERE dt.del_flag = '0' AND dt.detail_id IN " +
        "<foreach collection='detailIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
        "ORDER BY dt.team_id ASC" +
        "</script>")
    List<Map<String, Object>> selectTeamNamesByDetailIds(@Param("detailIds") Collection<Long> detailIds);

    /**
     * 物理删除某明细某角色的全部旧班组关联（sync 先删后插，避免逻辑删累积撞 UNIQUE）。
     *
     * @param detailId {@code t_plant_plant_details.id}
     * @param role     {@code plant} / {@code harvest}
     */
    @Delete("DELETE FROM t_plant_details_team WHERE detail_id = #{detailId} AND role = #{role}")
    int physicalDeleteByDetailRole(@Param("detailId") Long detailId, @Param("role") String role);
}
