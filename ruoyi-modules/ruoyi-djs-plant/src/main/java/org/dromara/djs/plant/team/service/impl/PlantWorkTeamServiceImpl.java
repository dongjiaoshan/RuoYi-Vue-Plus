package org.dromara.djs.plant.team.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.plant.team.constant.PlantTeamConstants;
import org.dromara.djs.plant.team.domain.PlantWorkPeople;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.domain.bo.PlantWorkTeamBo;
import org.dromara.djs.plant.team.domain.query.PlantWorkTeamQuery;
import org.dromara.djs.plant.team.domain.vo.PlantWorkTeamVo;
import org.dromara.djs.plant.team.mapper.PlantWorkPeopleMapper;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.dromara.djs.plant.team.service.IPlantWorkTeamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 班组 Service 实现（PLT-MD-002）。
 *
 * <p>软删走 {@link DjsBaseServiceImpl#softDelete}；列表 enrich：先 selectVoPage 拿基础数据，
 * 再批量 select sys_user / count members 一次性回灌。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
@Slf4j
@Service
public class PlantWorkTeamServiceImpl extends DjsBaseServiceImpl<PlantWorkTeamMapper, PlantWorkTeam>
    implements IPlantWorkTeamService {

    private final PlantWorkPeopleMapper peopleMapper;

    public PlantWorkTeamServiceImpl(PlantWorkTeamMapper baseMapper, PlantWorkPeopleMapper peopleMapper) {
        super(baseMapper);
        this.peopleMapper = peopleMapper;
    }

    @Override
    public TableDataInfo<PlantWorkTeamVo> queryPageList(PlantWorkTeamQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PlantWorkTeam> wrapper = buildQueryWrapper(query);
        Page<PlantWorkTeamVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrich(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PlantWorkTeamVo> queryList(PlantWorkTeamQuery query) {
        List<PlantWorkTeamVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        enrich(list);
        return list;
    }

    @Override
    public PlantWorkTeamVo queryById(Long id) {
        PlantWorkTeamVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            enrich(Collections.singletonList(vo));
        }
        return vo;
    }

    @Override
    public int insertByBo(PlantWorkTeamBo bo) {
        PlantWorkTeam entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("班组入参转换失败");
        }
        if (entity.getTeamStatus() == null) {
            entity.setTeamStatus(PlantTeamConstants.TEAM_STATUS_ACTIVE);
        }
        // leader_id 不在新增 BO 中，统一通过"成员管理弹窗 / setLeader"端点设置
        entity.setLeaderId(null);
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(PlantWorkTeamBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("班组 ID 不能为空");
        }
        PlantWorkTeam exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("班组不存在或已删除：" + bo.getId());
        }
        PlantWorkTeam entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("班组入参转换失败");
        }
        // leader_id 由 setLeader 端点统一切换，update 时强制锁回原值（防止 form 提交误清）
        entity.setLeaderId(exists.getLeaderId());
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 删除前校验：班组下若仍有 active 成员则禁止删除
        for (Long id : ids) {
            long activeMembers = peopleMapper.selectCount(
                new LambdaQueryWrapper<PlantWorkPeople>()
                    .eq(PlantWorkPeople::getTeamId, id)
                    .eq(PlantWorkPeople::getDelFlag, "0")
            );
            if (activeMembers > 0) {
                PlantWorkTeam team = baseMapper.selectById(id);
                String name = team != null ? team.getTeamName() : String.valueOf(id);
                throw new ServiceException("班组 [" + name + "] 下还有成员，请先清空成员再删除");
            }
        }
        return softDelete(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int setLeader(Long teamId, Long userId) {
        if (teamId == null || userId == null) {
            throw new ServiceException("班组 ID 与负责人 user_id 必填");
        }
        PlantWorkTeam team = baseMapper.selectById(teamId);
        if (team == null) {
            throw new ServiceException("班组不存在或已删除：" + teamId);
        }
        // 1. userId 必须是该 team 的 active 成员
        long isMember = peopleMapper.selectCount(
            new LambdaQueryWrapper<PlantWorkPeople>()
                .eq(PlantWorkPeople::getTeamId, teamId)
                .eq(PlantWorkPeople::getUserId, userId)
                .eq(PlantWorkPeople::getDelFlag, "0")
        );
        if (isMember == 0) {
            throw new ServiceException("用户 [" + userId + "] 不是该班组成员，无法设为负责人");
        }
        // 2. 同步双写
        //    2a. 班组 leader_id = userId
        team.setLeaderId(userId);
        baseMapper.updateById(team);
        //    2b. 同班组成员 is_leader 全部置 2，再把 userId 置 1
        UpdateWrapper<PlantWorkPeople> resetWrapper = Wrappers.<PlantWorkPeople>update()
            .eq("team_id", teamId)
            .eq("del_flag", "0")
            .set("is_leader", PlantTeamConstants.IS_LEADER_NO);
        peopleMapper.update(null, resetWrapper);
        UpdateWrapper<PlantWorkPeople> setWrapper = Wrappers.<PlantWorkPeople>update()
            .eq("team_id", teamId)
            .eq("user_id", userId)
            .eq("del_flag", "0")
            .set("is_leader", PlantTeamConstants.IS_LEADER_YES);
        return peopleMapper.update(null, setWrapper);
    }

    /**
     * 批量给 VO 补 leaderName + leaderPhone + memberCount（避免列表 N+1 查询）。
     */
    private void enrich(List<PlantWorkTeamVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        // 1) 收集 leader user_ids → 一次 sys_user 查询（取 nick_name + phonenumber）
        Set<Long> leaderIds = list.stream()
            .map(PlantWorkTeamVo::getLeaderId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> idToName = new HashMap<>();
        Map<Long, String> idToPhone = new HashMap<>();
        if (!leaderIds.isEmpty()) {
            List<Map<String, Object>> rows = baseMapper.selectLeaderNames(leaderIds);
            for (Map<String, Object> row : rows) {
                Object idObj = row.get("userId");
                if (idObj instanceof Number n) {
                    idToName.put(n.longValue(), (String) row.get("nickName"));
                    idToPhone.put(n.longValue(), (String) row.get("phonenumber"));
                }
            }
        }
        // 2) 收集 team_ids → 一次 count 查询
        Set<Long> teamIds = list.stream()
            .map(PlantWorkTeamVo::getId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        Map<Long, Integer> teamToCount = new HashMap<>();
        if (!teamIds.isEmpty()) {
            List<Map<String, Object>> rows = baseMapper.selectMemberCounts(teamIds);
            for (Map<String, Object> row : rows) {
                Object idObj = row.get("teamId");
                Object cntObj = row.get("cnt");
                if (idObj instanceof Number nid && cntObj instanceof Number ncnt) {
                    teamToCount.put(nid.longValue(), ncnt.intValue());
                }
            }
        }
        // 3) 回灌
        for (PlantWorkTeamVo vo : list) {
            if (vo.getLeaderId() != null) {
                vo.setLeaderName(idToName.get(vo.getLeaderId()));
                vo.setLeaderPhone(idToPhone.get(vo.getLeaderId()));
            }
            vo.setMemberCount(teamToCount.getOrDefault(vo.getId(), 0));
        }
    }

    protected PlantWorkTeam toEntity(PlantWorkTeamBo bo) {
        return MapstructUtils.convert(bo, PlantWorkTeam.class);
    }

    private LambdaQueryWrapper<PlantWorkTeam> buildQueryWrapper(PlantWorkTeamQuery query) {
        LambdaQueryWrapper<PlantWorkTeam> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PlantWorkTeam::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getTeamName()), PlantWorkTeam::getTeamName, query.getTeamName())
            .eq(query.getTeamStatus() != null, PlantWorkTeam::getTeamStatus, query.getTeamStatus())
            .eq(query.getLeaderId() != null, PlantWorkTeam::getLeaderId, query.getLeaderId())
            .orderByDesc(PlantWorkTeam::getId);
        return wrapper;
    }
}
