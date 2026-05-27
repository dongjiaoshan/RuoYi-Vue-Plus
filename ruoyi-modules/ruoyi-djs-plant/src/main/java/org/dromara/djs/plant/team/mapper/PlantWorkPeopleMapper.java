package org.dromara.djs.plant.team.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.team.domain.PlantWorkPeople;
import org.dromara.djs.plant.team.domain.vo.CandidateUserVo;
import org.dromara.djs.plant.team.domain.vo.PlantWorkPeopleVo;

import java.util.Collection;
import java.util.List;

/**
 * 班组成员 Mapper（PLT-MD-002）。
 *
 * <p>同 ticket 内涉及跨 t_plant_work_people + sys_user 的 JOIN 查询，
 * 走 {@link Select @Select} 注解（与 djs-breed AggregateQueryMapper / PigMapper 一致）。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
public interface PlantWorkPeopleMapper extends BaseMapperPlus<PlantWorkPeople, PlantWorkPeopleVo> {

    /**
     * 查询指定班组的成员（含 sys_user 冗余字段）。
     */
    @Select("SELECT p.id, p.team_id AS teamId, p.user_id AS userId, " +
        "       u.user_name AS userName, u.nick_name AS nickName, u.phonenumber, " +
        "       p.is_leader AS isLeader, p.join_date AS joinDate " +
        "FROM t_plant_work_people p " +
        "LEFT JOIN sys_user u ON u.user_id = p.user_id AND u.del_flag = '0' " +
        "WHERE p.del_flag = '0' AND p.team_id = #{teamId} " +
        "ORDER BY p.is_leader ASC, p.id DESC")
    List<PlantWorkPeopleVo> listByTeamId(@Param("teamId") Long teamId);

    /**
     * 检查多个 user_id 是否已在 active 班组中；返回已占用的关联行（含 team_id + nick_name + 班组名）。
     *
     * <p>仅用于 service 层批量加入成员前预校验报错时取冲突详情。</p>
     */
    @Select("<script>" +
        "SELECT p.user_id AS userId, u.nick_name AS nickName, " +
        "       p.team_id AS teamId, t.team_name AS teamName " +
        "FROM t_plant_work_people p " +
        "LEFT JOIN sys_user u ON u.user_id = p.user_id " +
        "LEFT JOIN t_plant_work_team t ON t.id = p.team_id AND t.del_flag = '0' " +
        "WHERE p.del_flag = '0' AND p.user_id IN " +
        "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
        "</script>")
    List<java.util.Map<String, Object>> findOccupiedUsers(@Param("userIds") Collection<Long> userIds);

    /**
     * 候选员工：dept_id=种植部 + 未在任何 active 班组的 sys_user。
     */
    @Select("SELECT u.user_id AS userId, u.user_name AS userName, u.nick_name AS nickName, " +
        "       u.phonenumber, d.dept_name AS deptName " +
        "FROM sys_user u " +
        "LEFT JOIN sys_dept d ON d.dept_id = u.dept_id " +
        "WHERE u.del_flag = '0' AND u.status = '0' " +
        "  AND u.dept_id = #{deptId} " +
        "  AND u.user_id NOT IN ( " +
        "    SELECT user_id FROM t_plant_work_people WHERE del_flag = '0' AND tenant_id = #{tenantId} " +
        "  ) " +
        "ORDER BY u.user_id")
    List<CandidateUserVo> listCandidateUsers(@Param("deptId") Long deptId,
                                             @Param("tenantId") String tenantId);

    /**
     * 校验给定 user_id 集合中实际存在于 sys_user.dept_id=种植部 + 状态正常的 user 数量。
     */
    @Select("<script>" +
        "SELECT COUNT(*) FROM sys_user " +
        "WHERE del_flag = '0' AND status = '0' AND dept_id = #{deptId} AND user_id IN " +
        "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
        "</script>")
    long countValidPlantUsers(@Param("userIds") Collection<Long> userIds, @Param("deptId") Long deptId);
}
