package org.dromara.djs.plant.team.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.plant.team.constant.PlantTeamConstants;
import org.dromara.djs.plant.team.domain.PlantWorkPeople;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.domain.bo.PlantWorkPeopleBatchBo;
import org.dromara.djs.plant.team.domain.vo.CandidateUserVo;
import org.dromara.djs.plant.team.domain.vo.PlantWorkPeopleVo;
import org.dromara.djs.plant.team.mapper.PlantWorkPeopleMapper;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.dromara.djs.plant.team.service.IPlantWorkPeopleService;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 班组成员 Service 实现（PLT-MD-002）。
 *
 * <p>核心约束：<b>一人只能属于一个班组</b>（service 层 INSERT 前预校验 + DB 端
 * {@code UNIQUE(tenant_id, user_id, del_unique)} 兜底）。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
@Slf4j
@Service
public class PlantWorkPeopleServiceImpl implements IPlantWorkPeopleService {

    private final PlantWorkPeopleMapper peopleMapper;
    private final PlantWorkTeamMapper teamMapper;

    public PlantWorkPeopleServiceImpl(PlantWorkPeopleMapper peopleMapper, PlantWorkTeamMapper teamMapper) {
        this.peopleMapper = peopleMapper;
        this.teamMapper = teamMapper;
    }

    @Override
    public List<PlantWorkPeopleVo> listByTeamId(Long teamId) {
        if (teamId == null) {
            return new ArrayList<>();
        }
        return peopleMapper.listByTeamId(teamId);
    }

    @Override
    public List<CandidateUserVo> listCandidateUsers() {
        String tenantId = currentTenantId();
        return peopleMapper.listCandidateUsers(PlantTeamConstants.PLANT_DEPT_ID, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchAddMembers(PlantWorkPeopleBatchBo bo) {
        if (bo == null || bo.getTeamId() == null) {
            throw new ServiceException("班组 ID 必填");
        }
        List<Long> userIds = bo.getUserIds();
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        // dedupe
        Set<Long> dedup = new HashSet<>(userIds);
        userIds = new ArrayList<>(dedup);

        // 1. 班组存在 + 未删除
        PlantWorkTeam team = teamMapper.selectById(bo.getTeamId());
        if (team == null) {
            throw new ServiceException("班组不存在或已删除：" + bo.getTeamId());
        }

        // 2. userIds 全部存在 sys_user 且 dept_id=种植部 + status='0'
        long validCount = peopleMapper.countValidPlantUsers(userIds, PlantTeamConstants.PLANT_DEPT_ID);
        if (validCount != userIds.size()) {
            throw new ServiceException("待加入用户中有非种植部员工或已停用账户，请刷新候选员工列表");
        }

        // 3. userIds 均未在 t_plant_work_people active 中
        List<Map<String, Object>> occupied = peopleMapper.findOccupiedUsers(userIds);
        if (occupied != null && !occupied.isEmpty()) {
            Map<String, Object> first = occupied.get(0);
            String nickName = String.valueOf(first.get("nickName"));
            String teamName = String.valueOf(first.get("teamName"));
            throw new ServiceException("用户 [" + nickName + "] 已属于班组 [" + teamName + "]，请先调出再加入");
        }

        // 4. 批量 INSERT
        Date now = new Date();
        Long createBy = currentUserIdSafe();
        int count = 0;
        for (Long userId : userIds) {
            PlantWorkPeople row = new PlantWorkPeople();
            row.setTeamId(bo.getTeamId());
            row.setUserId(userId);
            row.setIsLeader(PlantTeamConstants.IS_LEADER_NO);
            row.setJoinDate(now);
            row.setCreateBy(createBy);
            row.setCreateTime(now);
            row.setUpdateBy(createBy);
            row.setUpdateTime(now);
            row.setDelFlag("0");
            row.setDelUnique(0L);
            count += peopleMapper.insert(row);
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeMember(Long teamId, Long userId) {
        if (teamId == null || userId == null) {
            throw new ServiceException("班组 ID 与 user_id 必填");
        }
        PlantWorkPeople row = peopleMapper.selectOne(
            new LambdaQueryWrapper<PlantWorkPeople>()
                .eq(PlantWorkPeople::getTeamId, teamId)
                .eq(PlantWorkPeople::getUserId, userId)
                .eq(PlantWorkPeople::getDelFlag, "0")
        );
        if (row == null) {
            throw new ServiceException("用户不在该班组中：teamId=" + teamId + ", userId=" + userId);
        }

        // 软删该成员行（与 DjsBaseServiceImpl.softDelete 一致的 wrapper-only 写法）
        Long updateBy = currentUserIdSafe();
        Date now = new Date();
        UpdateWrapper<PlantWorkPeople> wrapper = Wrappers.<PlantWorkPeople>update()
            .eq("id", row.getId())
            .eq("del_flag", "0")
            .set("del_flag", "1")
            .set("del_unique", row.getId())
            .set("update_by", updateBy)
            .set("update_time", now);
        int n = peopleMapper.update(null, wrapper);

        // 如果调出的是当前 leader → 同步清空班组 leader_id
        PlantWorkTeam team = teamMapper.selectById(teamId);
        if (team != null && Objects.equals(team.getLeaderId(), userId)) {
            team.setLeaderId(null);
            teamMapper.updateById(team);
        }
        return n;
    }

    private Long currentUserIdSafe() {
        try {
            Long id = LoginHelper.getUserId();
            return id != null ? id : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private String currentTenantId() {
        try {
            String t = TenantHelper.getTenantId();
            return t != null ? t : "1001";
        } catch (Exception e) {
            return "1001";
        }
    }
}
