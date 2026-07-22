package org.dromara.djs.plant.activity.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.activity.domain.PlantActivityTeam;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 采摘活动·班组多选中间表 Mapper（G1-TEAMS-MULTISELECT）。
 *
 * @author djs
 * @since G1-TEAMS-MULTISELECT
 */
public interface PlantActivityTeamMapper extends BaseMapperPlus<PlantActivityTeam, PlantActivityTeam> {

    /**
     * 批量查询若干采摘活动的班组名（一次 SQL，enrich 用，禁 N+1）。
     *
     * @param activityIds {@code t_plant_plant_activity.id} 集合（非空）
     * @return 每行 activityId(Long) / teamId(Long) / teamName(String)，按 team_id 升序
     */
    @Select("<script>" +
        "SELECT at.activity_id AS activityId, at.team_id AS teamId, t.team_name AS teamName " +
        "FROM t_plant_activity_team at " +
        "LEFT JOIN t_plant_work_team t ON t.id = at.team_id AND t.del_flag = '0' " +
        "WHERE at.del_flag = '0' AND at.activity_id IN " +
        "<foreach collection='activityIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
        "ORDER BY at.team_id ASC" +
        "</script>")
    List<Map<String, Object>> selectTeamNamesByActivityIds(@Param("activityIds") Collection<Long> activityIds);

    /**
     * 物理删除某采摘活动的全部旧班组关联（sync 先删后插）。
     *
     * @param activityId {@code t_plant_plant_activity.id}
     */
    @Delete("DELETE FROM t_plant_activity_team WHERE activity_id = #{activityId}")
    int physicalDeleteByActivityId(@Param("activityId") Long activityId);
}
