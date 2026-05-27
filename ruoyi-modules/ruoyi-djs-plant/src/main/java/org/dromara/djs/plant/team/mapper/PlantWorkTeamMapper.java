package org.dromara.djs.plant.team.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.domain.vo.PlantWorkTeamVo;

import java.util.Collection;
import java.util.Map;

/**
 * 班组 Mapper（PLT-MD-002）。
 *
 * <p>主体走 {@link BaseMapperPlus#selectVoPage}；列表展示需要的
 * {@code leaderName}（sys_user.nick_name）和 {@code memberCount} 由 service 层批量
 * enrich（避免 N+1，详 {@link #selectLeaderNames} / {@link #selectMemberCounts}）。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
public interface PlantWorkTeamMapper extends BaseMapperPlus<PlantWorkTeam, PlantWorkTeamVo> {

    /**
     * 批量查询 leaderId → nickName 映射（一次 SQL）。
     *
     * @param userIds sys_user.user_id 集合（已 dedupe，且非空）
     * @return key=userId(Long) / value=nickName(String)
     */
    @Select("<script>" +
        "SELECT user_id AS userId, nick_name AS nickName " +
        "FROM sys_user " +
        "WHERE del_flag = '0' AND user_id IN " +
        "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
        "</script>")
    java.util.List<Map<String, Object>> selectLeaderNames(@Param("userIds") Collection<Long> userIds);

    /**
     * 批量查询每个班组的 active 成员数（一次 SQL）。
     *
     * @param teamIds 班组 id 集合（非空）
     * @return key=teamId(Long) / value=memberCount(Long)
     */
    @Select("<script>" +
        "SELECT team_id AS teamId, COUNT(*) AS cnt " +
        "FROM t_plant_work_people " +
        "WHERE del_flag = '0' AND team_id IN " +
        "<foreach collection='teamIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
        "GROUP BY team_id" +
        "</script>")
    java.util.List<Map<String, Object>> selectMemberCounts(@Param("teamIds") Collection<Long> teamIds);
}
