package org.dromara.djs.plant.pick.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.pick.domain.bo.PickAdjustBatchBo;
import org.dromara.djs.plant.pick.domain.bo.PickDetailAdjustBo;
import org.dromara.djs.plant.pick.domain.query.PickPlanQuery;
import org.dromara.djs.plant.pick.domain.vo.PickPlanGroupVo;
import org.dromara.djs.plant.pick.mapper.PickPlanMapper;
import org.dromara.djs.plant.pick.service.IPickPlanService;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.domain.vo.PlantDetailsVo;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采摘计划 Service 实现（PLT-PLAN-002）。
 *
 * <p>核心职责（doc/10 §F-PLT-05）：</p>
 * <ol>
 *   <li>{@link #listByCrop(PickPlanQuery)}：admin 列表，按 (plant_id, crop_id) 聚合 plant_details</li>
 *   <li>{@link #listDetailsByPlanCrop(Long, Long)}：admin 调整页 step1，拉行</li>
 *   <li>{@link #adjustDetails(PickAdjustBatchBo)}：admin 调整页 step2，批量 UPDATE 4 时间字段 + is_pick + harvest_by</li>
 * </ol>
 *
 * <p>本 Service **不** extends DjsBaseServiceImpl（无独立主表 entity；纯查询 + UPDATE 操作 plant_details）。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PickPlanServiceImpl implements IPickPlanService {

    private final PickPlanMapper pickPlanMapper;
    private final PlantDetailsMapper detailsMapper;
    private final CropInfoMapper cropMapper;
    private final PlotInfoMapper plotMapper;
    private final PlantWorkTeamMapper teamMapper;

    @Override
    public List<PickPlanGroupVo> listByCrop(PickPlanQuery query) {
        String tenantId = currentTenantSafe();
        PickPlanQuery q = (query == null) ? new PickPlanQuery() : query;
        return pickPlanMapper.aggregateByPlanCrop(
            tenantId,
            q.getPlanYear(),
            q.getPlanSeason(),
            q.getCropId(),
            q.getHarvestStatus()
        );
    }

    @Override
    public List<PlantDetailsVo> listDetailsByPlanCrop(Long planId, Long cropId) {
        if (planId == null || cropId == null) {
            throw new ServiceException("planId / cropId 必填");
        }
        List<PlantDetailsVo> details = detailsMapper.selectVoList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlantId, planId)
                .eq(PlantDetails::getCropId, cropId)
                .orderByAsc(PlantDetails::getPlantMonth)
                .orderByAsc(PlantDetails::getPlantPeriod));
        enrich(details);
        return details;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int adjustDetails(PickAdjustBatchBo bo) {
        if (bo == null || CollUtil.isEmpty(bo.getRows())) {
            return 0;
        }
        Set<Long> ids = bo.getRows().stream()
            .map(PickDetailAdjustBo::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return 0;
        }
        // 安全校验：所有 row.id 必须属于 (plantId, cropId)，并一次性取出现有行用于「发布必填」校验
        List<PlantDetails> owned = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .in(PlantDetails::getId, ids)
                .eq(PlantDetails::getPlantId, bo.getPlantId())
                .eq(PlantDetails::getCropId, bo.getCropId()));
        if (owned.size() != ids.size()) {
            throw new ServiceException("调整行不全属于指定计划/作物，已拒绝（防越权）");
        }
        Map<Long, PlantDetails> ownedMap = owned.stream()
            .collect(Collectors.toMap(PlantDetails::getId, d -> d, (a, b) -> a));

        Long updateBy = currentUserIdSafe();
        Date now = new Date();
        int updated = 0;
        for (PickDetailAdjustBo row : bo.getRows()) {
            if (row.getBeginHarvestdate() != null && row.getEndHarvestdate() != null
                && row.getBeginHarvestdate().isAfter(row.getEndHarvestdate())) {
                throw new ServiceException("begin_harvestdate 不能晚于 end_harvestdate（明细 id=" + row.getId() + "）");
            }
            if (row.getIsPick() != null && row.getIsPick() != 1 && row.getIsPick() != 2) {
                throw new ServiceException("is_pick 仅允许 1 或 2（明细 id=" + row.getId() + "）");
            }
            // D3：采摘班组发布必填（源头杜绝 mp 空 picker）。
            // 普通采收明细（effective is_pick=2）调整后必须有 harvest_by；游客采摘活动（is_pick=1）不强制。
            PlantDetails existing = ownedMap.get(row.getId());
            Integer effectiveIsPick = row.getIsPick() != null ? row.getIsPick()
                : (existing != null ? existing.getIsPick() : null);
            Long effectiveHarvestBy = row.getHarvestBy() != null ? row.getHarvestBy()
                : (existing != null ? existing.getHarvestBy() : null);
            boolean isGuestPick = effectiveIsPick != null && effectiveIsPick == 1;
            if (!isGuestPick && effectiveHarvestBy == null) {
                throw new ServiceException("请指派采摘班组后再发布（明细 id=" + row.getId() + "）");
            }
            updated += detailsMapper.update(null,
                Wrappers.<PlantDetails>update()
                    .eq("id", row.getId())
                    .eq("del_flag", "0")
                    .set(row.getBeginHarvestdate() != null, "begin_harvestdate", row.getBeginHarvestdate())
                    .set(row.getEndHarvestdate()   != null, "end_harvestdate",   row.getEndHarvestdate())
                    .set(row.getIsPick()           != null, "is_pick",           row.getIsPick())
                    .set(row.getHarvestBy()        != null, "harvest_by",        row.getHarvestBy())
                    .set("update_by", updateBy)
                    .set("update_time", now));
        }
        return updated;
    }

    // ============================================================
    // enrich：plot_name / crop_name / harvest_team_name 批量补
    // ============================================================
    private void enrich(List<PlantDetailsVo> details) {
        if (CollUtil.isEmpty(details)) {
            return;
        }
        Set<Long> plotIds = details.stream().map(PlantDetailsVo::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> cropIds = details.stream().map(PlantDetailsVo::getCropId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> teamIds = new HashSet<>();
        details.forEach(d -> {
            if (d.getPlantBy() != null) { teamIds.add(d.getPlantBy()); }
            if (d.getHarvestBy() != null) { teamIds.add(d.getHarvestBy()); }
        });

        Map<Long, PlotInfo> plotMap = plotIds.isEmpty() ? Map.of()
            : plotMapper.selectByIds(plotIds).stream().collect(Collectors.toMap(PlotInfo::getId, p -> p, (a, b) -> a));
        Map<Long, String> cropMap = cropIds.isEmpty() ? Map.of()
            : cropMapper.selectByIds(cropIds).stream().collect(Collectors.toMap(CropInfo::getId, CropInfo::getCropName, (a, b) -> a));
        Map<Long, String> teamMap = teamIds.isEmpty() ? Map.of()
            : teamMapper.selectByIds(teamIds).stream().collect(Collectors.toMap(PlantWorkTeam::getId, PlantWorkTeam::getTeamName, (a, b) -> a));

        for (PlantDetailsVo d : details) {
            PlotInfo plot = plotMap.get(d.getPlotId());
            if (plot != null) {
                d.setPlotName(plot.getPlotName());
                d.setPlotCode(plot.getPlotCode());
            }
            d.setCropName(cropMap.get(d.getCropId()));
            d.setPlantTeamName(teamMap.get(d.getPlantBy()));
            d.setHarvestTeamName(teamMap.get(d.getHarvestBy()));
        }
    }

    private String currentTenantSafe() {
        try {
            String t = LoginHelper.getTenantId();
            return (t != null && !t.isBlank()) ? t : "1001";
        } catch (Exception e) {
            return "1001";
        }
    }

    private Long currentUserIdSafe() {
        try {
            Long id = LoginHelper.getUserId();
            return id != null ? id : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
