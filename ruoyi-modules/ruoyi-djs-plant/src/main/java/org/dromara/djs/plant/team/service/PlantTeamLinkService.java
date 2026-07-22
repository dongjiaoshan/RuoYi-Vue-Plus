package org.dromara.djs.plant.team.service;

import cn.hutool.core.collection.CollUtil;
import org.dromara.djs.plant.activity.domain.PlantActivityTeam;
import org.dromara.djs.plant.activity.mapper.PlantActivityTeamMapper;
import org.dromara.djs.plant.farm.domain.FarmRecordsTeam;
import org.dromara.djs.plant.farm.mapper.FarmRecordsTeamMapper;
import org.dromara.djs.plant.plan.domain.PlantDetailsTeam;
import org.dromara.djs.plant.plan.mapper.PlantDetailsTeamMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 班组多选中间表统一读写服务（G1-TEAMS-MULTISELECT，row36/37/40）。
 *
 * <p>集中承载 3 张中间表（{@code t_plant_details_team} / {@code t_farm_records_team} /
 * {@code t_plant_activity_team}）的「先物理删后插」同步 + 批量名称 enrich，避免各业务
 * service 重复实现。旧单列（plant_by/harvest_by/farm_by/activity_by）由调用方各自维护为
 * 「多选第一个」，本服务只管全集。</p>
 *
 * @author djs
 * @since G1-TEAMS-MULTISELECT
 */
@Service
public class PlantTeamLinkService {

    /** 明细班组角色：种植。 */
    public static final String ROLE_PLANT = "plant";
    /** 明细班组角色：采摘。 */
    public static final String ROLE_HARVEST = "harvest";

    private final PlantDetailsTeamMapper detailsTeamMapper;
    private final FarmRecordsTeamMapper farmTeamMapper;
    private final PlantActivityTeamMapper activityTeamMapper;

    public PlantTeamLinkService(PlantDetailsTeamMapper detailsTeamMapper,
                                FarmRecordsTeamMapper farmTeamMapper,
                                PlantActivityTeamMapper activityTeamMapper) {
        this.detailsTeamMapper = detailsTeamMapper;
        this.farmTeamMapper = farmTeamMapper;
        this.activityTeamMapper = activityTeamMapper;
    }

    // ============================================================
    // 写：先物理删旧全集，再插新全集（去重 + 去空）
    // ============================================================

    /**
     * 同步某种植明细某角色的班组全集。teamIds 为 null 时不动（用于「仅传单列」场景保留旧关联）；
     * 传空 list 表示清空该角色关联。
     */
    public void syncDetailTeams(Long detailId, String role, Collection<Long> teamIds) {
        if (detailId == null || role == null || teamIds == null) {
            return;
        }
        detailsTeamMapper.physicalDeleteByDetailRole(detailId, role);
        for (Long teamId : dedupe(teamIds)) {
            PlantDetailsTeam link = new PlantDetailsTeam();
            link.setDetailId(detailId);
            link.setTeamId(teamId);
            link.setRole(role);
            detailsTeamMapper.insert(link);
        }
    }

    /** 同步某农事记录的班组全集。 */
    public void syncFarmTeams(Long recordId, Collection<Long> teamIds) {
        if (recordId == null || teamIds == null) {
            return;
        }
        farmTeamMapper.physicalDeleteByRecordId(recordId);
        for (Long teamId : dedupe(teamIds)) {
            FarmRecordsTeam link = new FarmRecordsTeam();
            link.setRecordId(recordId);
            link.setTeamId(teamId);
            farmTeamMapper.insert(link);
        }
    }

    /** 同步某采摘活动的班组全集。 */
    public void syncActivityTeams(Long activityId, Collection<Long> teamIds) {
        if (activityId == null || teamIds == null) {
            return;
        }
        activityTeamMapper.physicalDeleteByActivityId(activityId);
        for (Long teamId : dedupe(teamIds)) {
            PlantActivityTeam link = new PlantActivityTeam();
            link.setActivityId(activityId);
            link.setTeamId(teamId);
            activityTeamMapper.insert(link);
        }
    }

    // ============================================================
    // 读：批量 enrich 名称 / id（保序去重）
    // ============================================================

    /**
     * 批量取明细各角色班组名。
     *
     * @return detailId → role → List&lt;teamName&gt;（按 team_id 升序）
     */
    public Map<Long, Map<String, List<String>>> detailTeamNames(Collection<Long> detailIds) {
        Map<Long, Map<String, List<String>>> result = new LinkedHashMap<>();
        if (CollUtil.isEmpty(detailIds)) {
            return result;
        }
        for (Map<String, Object> row : detailsTeamMapper.selectTeamNamesByDetailIds(detailIds)) {
            Long detailId = toLong(row.get("detailId"));
            String role = row.get("role") == null ? null : row.get("role").toString();
            String name = row.get("teamName") == null ? null : row.get("teamName").toString();
            if (detailId == null || role == null || name == null) {
                continue;
            }
            result.computeIfAbsent(detailId, k -> new LinkedHashMap<>())
                .computeIfAbsent(role, k -> new ArrayList<>()).add(name);
        }
        return result;
    }

    /**
     * 批量取明细各角色 teamId。
     *
     * @return detailId → role → List&lt;teamId&gt;
     */
    public Map<Long, Map<String, List<Long>>> detailTeamIds(Collection<Long> detailIds) {
        Map<Long, Map<String, List<Long>>> result = new LinkedHashMap<>();
        if (CollUtil.isEmpty(detailIds)) {
            return result;
        }
        for (Map<String, Object> row : detailsTeamMapper.selectTeamNamesByDetailIds(detailIds)) {
            Long detailId = toLong(row.get("detailId"));
            String role = row.get("role") == null ? null : row.get("role").toString();
            Long teamId = toLong(row.get("teamId"));
            if (detailId == null || role == null || teamId == null) {
                continue;
            }
            result.computeIfAbsent(detailId, k -> new LinkedHashMap<>())
                .computeIfAbsent(role, k -> new ArrayList<>()).add(teamId);
        }
        return result;
    }

    /** 批量取农事记录班组名。recordId → List&lt;teamName&gt;。 */
    public Map<Long, List<String>> farmTeamNames(Collection<Long> recordIds) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        if (CollUtil.isEmpty(recordIds)) {
            return result;
        }
        for (Map<String, Object> row : farmTeamMapper.selectTeamNamesByRecordIds(recordIds)) {
            Long recordId = toLong(row.get("recordId"));
            String name = row.get("teamName") == null ? null : row.get("teamName").toString();
            if (recordId == null || name == null) {
                continue;
            }
            result.computeIfAbsent(recordId, k -> new ArrayList<>()).add(name);
        }
        return result;
    }

    /** 批量取农事记录 teamId。recordId → List&lt;teamId&gt;。 */
    public Map<Long, List<Long>> farmTeamIds(Collection<Long> recordIds) {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        if (CollUtil.isEmpty(recordIds)) {
            return result;
        }
        for (Map<String, Object> row : farmTeamMapper.selectTeamNamesByRecordIds(recordIds)) {
            Long recordId = toLong(row.get("recordId"));
            Long teamId = toLong(row.get("teamId"));
            if (recordId == null || teamId == null) {
                continue;
            }
            result.computeIfAbsent(recordId, k -> new ArrayList<>()).add(teamId);
        }
        return result;
    }

    /** 批量取采摘活动班组名。activityId → List&lt;teamName&gt;。 */
    public Map<Long, List<String>> activityTeamNames(Collection<Long> activityIds) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        if (CollUtil.isEmpty(activityIds)) {
            return result;
        }
        for (Map<String, Object> row : activityTeamMapper.selectTeamNamesByActivityIds(activityIds)) {
            Long activityId = toLong(row.get("activityId"));
            String name = row.get("teamName") == null ? null : row.get("teamName").toString();
            if (activityId == null || name == null) {
                continue;
            }
            result.computeIfAbsent(activityId, k -> new ArrayList<>()).add(name);
        }
        return result;
    }

    // ============================================================
    // 工具
    // ============================================================

    private List<Long> dedupe(Collection<Long> ids) {
        return new ArrayList<>(new LinkedHashSet<>(
            ids.stream().filter(Objects::nonNull).toList()));
    }

    private Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(v.toString());
    }
}
