package org.dromara.djs.plant.team.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.team.domain.bo.PlantWorkTeamBo;
import org.dromara.djs.plant.team.domain.query.PlantWorkTeamQuery;
import org.dromara.djs.plant.team.domain.vo.PlantWorkTeamVo;

import java.util.Collection;
import java.util.List;

/**
 * 班组 Service（PLT-MD-002）。
 *
 * @author djs
 * @since PLT-MD-002
 */
public interface IPlantWorkTeamService {

    /** 分页查询（含 leaderName + memberCount 冗余）。 */
    TableDataInfo<PlantWorkTeamVo> queryPageList(PlantWorkTeamQuery query, PageQuery pageQuery);

    /** 列表查询（不分页，下游 PLT-PLAN-001 / PLT-WORK-001 / mp 用）。 */
    List<PlantWorkTeamVo> queryList(PlantWorkTeamQuery query);

    /** 按 ID 查单条（含 leaderName 冗余）。 */
    PlantWorkTeamVo queryById(Long id);

    /** 新增。 */
    int insertByBo(PlantWorkTeamBo bo);

    /** 修改（不修改 leaderId — 走 {@code setLeader} 端点）。 */
    int updateByBo(PlantWorkTeamBo bo);

    /**
     * 软删（支持批量）；删除前若班组下还有 active 成员则抛 {@code plant.workTeam.deleteBlockedHasMembers}。
     */
    int deleteWithValidByIds(Collection<Long> ids);

    /**
     * 设置班组负责人。
     *
     * <p>双轨写：(1) {@code t_plant_work_team.leader_id = userId}；
     * (2) 同班组 active 成员行的 {@code is_leader} 全部置 2，再把 userId 这一行置 1。
     * 前提：userId 必须已是该 team 的 active 成员，否则抛
     * {@code plant.workTeam.leaderNotMember}。</p>
     */
    int setLeader(Long teamId, Long userId);
}
