package org.dromara.djs.plant.team.service;

import org.dromara.djs.plant.team.domain.bo.PlantWorkPeopleBatchBo;
import org.dromara.djs.plant.team.domain.vo.CandidateUserVo;
import org.dromara.djs.plant.team.domain.vo.PlantWorkPeopleVo;

import java.util.List;

/**
 * 班组成员 Service（PLT-MD-002）。
 *
 * @author djs
 * @since PLT-MD-002
 */
public interface IPlantWorkPeopleService {

    /** 班组成员列表（含 sys_user.nick_name / phonenumber 冗余）。 */
    List<PlantWorkPeopleVo> listByTeamId(Long teamId);

    /** 候选员工列表：dept_id=种植部 + 未在任何 active 班组。 */
    List<CandidateUserVo> listCandidateUsers();

    /**
     * 批量加入成员。INSERT 前 4 段校验，命中冲突抛 ServiceException：
     * <ul>
     *   <li>team 存在 + 未删除</li>
     *   <li>userIds 全部在 sys_user 中且 dept_id=种植部 + status='0'</li>
     *   <li>userIds 均未在 t_plant_work_people active 中</li>
     *   <li>是空集合直接返回 0</li>
     * </ul>
     */
    int batchAddMembers(PlantWorkPeopleBatchBo bo);

    /**
     * 调出单个成员（软删）。如该 user 是 team.leader_id → 同步 UNSET 班组 leader_id。
     */
    int removeMember(Long teamId, Long userId);
}
