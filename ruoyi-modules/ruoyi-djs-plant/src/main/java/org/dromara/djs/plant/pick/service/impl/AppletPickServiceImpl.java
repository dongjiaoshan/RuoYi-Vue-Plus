package org.dromara.djs.plant.pick.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.farm.domain.bo.GrowRecordBo;
import org.dromara.djs.plant.farm.service.IFarmRecordsService;
import org.dromara.djs.plant.pick.domain.bo.PickSubmitBo;
import org.dromara.djs.plant.pick.domain.vo.PickCropTaskVo;
import org.dromara.djs.plant.pick.domain.vo.PickSummaryVo;
import org.dromara.djs.plant.pick.domain.vo.PickTaskVo;
import org.dromara.djs.plant.pick.event.PlantPickedEvent;
import org.dromara.djs.plant.pick.event.PlantPickedPayload;
import org.dromara.djs.plant.pick.service.IAppletPickService;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.domain.PlantPlan;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plan.mapper.PlantPlanMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * mp 端采摘任务 Service 实现（PLT-PLAN-002）。
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppletPickServiceImpl implements IAppletPickService {

    private final PlantDetailsMapper detailsMapper;
    private final PlantPlanMapper planMapper;
    private final PlotInfoMapper plotMapper;
    private final CropInfoMapper cropMapper;
    private final PlantWorkTeamMapper teamMapper;
    private final IFarmRecordsService farmRecordsService;
    private final ApplicationEventPublisher eventPublisher;

    /** is_pick=2 表示非游客采摘（普通采收）；mp 工人端只统计 / 展示这些。 */
    private static final int IS_PICK_NORMAL = 2;
    /** harvest_activity：农事记录采收类型（与 t_plant_farm_records.farm_type 字典 djs_farm_work_type 对齐）。 */
    private static final String HARVEST_FARM_TYPE = "harvest_activity";

    @Override
    public List<PickTaskVo> listMyTasks(String status) {
        List<String> statusList = parseStatus(status);

        List<PlantDetails> entities = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getIsPick, IS_PICK_NORMAL)   // PLT-PICK-001 决策①：隐藏游客采摘活动
                .in(PlantDetails::getHarvestStatus, statusList)
                .orderByAsc(PlantDetails::getEarliestHarvestdate)
                .orderByAsc(PlantDetails::getId));

        return enrichToVoList(entities);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitPick(PickSubmitBo bo) {
        PlantDetails detail = detailsMapper.selectById(bo.getDetailId());
        if (detail == null) {
            throw new ServiceException("采摘明细不存在或已删除：" + bo.getDetailId());
        }
        boolean finish = Boolean.TRUE.equals(bo.getFinish());
        // FIX-PLT-MP-PICK-001：weight 放宽 —— finish 必填且 > 0；begin 态(finish=false)可空（仅流转状态，不累加）
        boolean hasWeight = bo.getWeight() != null && bo.getWeight().compareTo(BigDecimal.ZERO) > 0;
        if (finish && !hasWeight) {
            throw new ServiceException("完成采摘时采收重量必填且必须大于 0");
        }
        if (bo.getWeight() != null && bo.getWeight().compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("采收重量不能为负");
        }
        // 捕获更新前的旧态：仅"非 completed → completed"首次流转才发跨域事件（幂等，防重复点完成产生多行待办）
        boolean alreadyCompleted = "completed".equals(detail.getHarvestStatus());

        // 1. 累加 actual_yield（无重量时不累加）
        BigDecimal newYield = detail.getActualYield() == null ? BigDecimal.ZERO : detail.getActualYield();
        if (hasWeight) {
            newYield = newYield.add(bo.getWeight());
            detail.setActualYield(newYield);
        }

        // 2. 首次采收回填 begin_harvestdate
        if (detail.getBeginHarvestdate() == null) {
            detail.setBeginHarvestdate(bo.getHarvestDate());
        }

        // 3. harvest_status 流转：pending → picking；finish 时 → completed
        if (finish) {
            detail.setHarvestStatus("completed");
            detail.setEndActualdate(LocalDate.now());
            detail.setEndHarvestdate(bo.getHarvestDate());
            // 4. average_yield = actual_yield / plot_area（plot_area 为 0/NULL 时跳过，不抛）
            BigDecimal area = detail.getPlotArea();
            if (area != null && area.compareTo(BigDecimal.ZERO) > 0) {
                detail.setAverageYield(newYield.divide(area, 3, RoundingMode.HALF_UP));
            }
        } else if (!"completed".equals(detail.getHarvestStatus())) {
            detail.setHarvestStatus("picking");
        }
        // 决策①：绝不动 is_pick
        detailsMapper.updateById(detail);

        // 5. INSERT 一行 t_plant_farm_records（farm_type='harvest_activity'，可追溯）
        //    复用 IFarmRecordsService.submitGrow（内含 record_no 生成 + plot_type/crop_name 冗余）。
        //    farm_by NOT NULL：取采摘明细指派班组 harvest_by；未指派则硬拦（采收需归属班组才能追溯）。
        if (detail.getHarvestBy() == null) {
            throw new ServiceException("该采摘任务未指派采摘班组，无法录入采收（请在 admin 采摘计划指派班组）");
        }
        GrowRecordBo grow = new GrowRecordBo();
        grow.setFarmType(HARVEST_FARM_TYPE);
        grow.setPlantId(detail.getPlantId());
        grow.setPlotId(detail.getPlotId());
        grow.setCropId(detail.getCropId());
        grow.setFarmBy(detail.getHarvestBy());
        grow.setOperatorUserId(bo.getPickerUserId());   // FIX-PLT-MP-PICK-001：采摘人员落 operator_user_id
        grow.setFarmDate(bo.getHarvestDate());
        grow.setProofOssIds(joinOssIds(bo.getProofOssIds()));
        grow.setRemark(bo.getRemark());
        farmRecordsService.submitGrow(grow);

        // 6. CROSS-FLOW-002：仅"非 completed → completed"首次流转时发 PlantPickedEvent，
        //    warehouse 域 listener 在 AFTER_COMMIT 写 1 行 t_warehouse_planting_record(handle_status='pending') 待办。
        //    逐次采收(finish=false)与重复点完成均不发，避免同 plot+crop 产生多行重复待办。
        if (finish && !alreadyCompleted) {
            PlantPickedPayload payload = new PlantPickedPayload();
            payload.setPlotId(detail.getPlotId());
            payload.setCropId(detail.getCropId());
            payload.setPlotName(resolvePlotName(detail.getPlotId()));
            payload.setCropName(resolveCropName(detail.getCropId()));
            payload.setPlantDate(detail.getBeginActualdate());
            payload.setHarvestDate(detail.getEndHarvestdate());
            payload.setHarvestWeight(detail.getActualYield());   // 累计采摘权威值
            payload.setTeamId(detail.getHarvestBy());
            payload.setTeamName(resolveTeamName(detail.getHarvestBy()));
            eventPublisher.publishEvent(new PlantPickedEvent(this, payload));
        }
    }

    @Override
    public PickSummaryVo todaySummary() {
        LocalDate today = LocalDate.now();
        // 全部非游客采摘任务（is_pick=2），应用层聚合（V1 数据量小，不写裸 SQL）
        List<PlantDetails> all = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getIsPick, IS_PICK_NORMAL)
                .select(PlantDetails::getHarvestStatus, PlantDetails::getCropId,
                    PlantDetails::getActualYield, PlantDetails::getBeginHarvestdate));

        long total = all.size();
        long completed = all.stream().filter(d -> "completed".equals(d.getHarvestStatus())).count();
        int rate = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);

        // 今日采摘明细 = begin_harvestdate=今日（首次采收落在今日的明细）
        List<PlantDetails> todayPicked = all.stream()
            .filter(d -> today.equals(d.getBeginHarvestdate()))
            .toList();
        int cropKindCount = (int) todayPicked.stream()
            .map(PlantDetails::getCropId).filter(Objects::nonNull).distinct().count();
        BigDecimal todayWeight = todayPicked.stream()
            .map(d -> d.getActualYield() == null ? BigDecimal.ZERO : d.getActualYield())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        PickSummaryVo vo = new PickSummaryVo();
        vo.setTaskCompletionRate(rate);
        vo.setTodayCropKindCount(cropKindCount);
        vo.setTodayWeight(todayWeight);
        return vo;
    }

    @Override
    public List<PickCropTaskVo> listCropTasks(Long zoneId) {
        // 全部非游客采摘明细（is_pick=2），应用层按片区过滤 + 作物聚合（V1 数据量小）
        List<PlantDetails> all = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getIsPick, IS_PICK_NORMAL));
        if (CollUtil.isEmpty(all)) {
            return Collections.emptyList();
        }

        // 取地块 → 片区映射（用于 zone 过滤）
        Set<Long> plotIds = all.stream().map(PlantDetails::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PlotInfo> plotMap = plotIds.isEmpty() ? Map.of()
            : plotMapper.selectByIds(plotIds).stream()
                .collect(Collectors.toMap(PlotInfo::getId, p -> p, (a, b) -> a));

        List<PlantDetails> filtered = all.stream()
            .filter(d -> {
                if (zoneId == null) {
                    return true;
                }
                PlotInfo plot = plotMap.get(d.getPlotId());
                return plot != null && zoneId.equals(plot.getZoneId());
            })
            .toList();
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        // 按 crop 聚合
        Map<Long, List<PlantDetails>> byCrop = filtered.stream()
            .filter(d -> d.getCropId() != null)
            .collect(Collectors.groupingBy(PlantDetails::getCropId, LinkedHashMap::new, Collectors.toList()));

        Set<Long> cropIds = byCrop.keySet();
        Map<Long, CropInfo> cropMap = cropIds.isEmpty() ? Map.of()
            : cropMapper.selectByIds(cropIds).stream()
                .collect(Collectors.toMap(CropInfo::getId, c -> c, (a, b) -> a));

        List<PickCropTaskVo> result = new ArrayList<>(byCrop.size());
        for (Map.Entry<Long, List<PlantDetails>> e : byCrop.entrySet()) {
            List<PlantDetails> rows = e.getValue();
            PickCropTaskVo vo = new PickCropTaskVo();
            vo.setCropId(e.getKey());
            CropInfo crop = cropMap.get(e.getKey());
            if (crop != null) {
                vo.setCropName(crop.getCropName());
                vo.setCropImg(crop.getCropImagePreview());
            }
            int plotCount = rows.size();
            long completed = rows.stream().filter(d -> "completed".equals(d.getHarvestStatus())).count();
            vo.setPlotCount(plotCount);
            vo.setCompletionRate(plotCount == 0 ? 0 : (int) Math.round(completed * 100.0 / plotCount));
            vo.setStartDate(rows.stream().map(PlantDetails::getEarliestHarvestdate)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null));
            vo.setLastDate(rows.stream().map(PlantDetails::getLastHarvestdate)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
            vo.setExpectedYield(rows.stream()
                .map(d -> d.getExpectedYield() == null ? BigDecimal.ZERO : d.getExpectedYield())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            vo.setActualYield(rows.stream()
                .map(d -> d.getActualYield() == null ? BigDecimal.ZERO : d.getActualYield())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<PickTaskVo> listCropPlots(Long planId, Long cropId) {
        if (cropId == null) {
            throw new ServiceException("作物 id 必填");
        }
        List<PlantDetails> entities = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getIsPick, IS_PICK_NORMAL)
                .eq(PlantDetails::getCropId, cropId)
                .eq(planId != null, PlantDetails::getPlantId, planId)
                .orderByAsc(PlantDetails::getEarliestHarvestdate)
                .orderByAsc(PlantDetails::getId));
        return enrichToVoList(entities);
    }

    @Override
    public PickTaskVo getTaskDetail(Long id) {
        if (id == null) {
            throw new ServiceException("id 必填");
        }
        PlantDetails detail = detailsMapper.selectById(id);
        if (detail == null) {
            throw new ServiceException("采摘任务不存在或已删除：" + id);
        }
        List<PickTaskVo> list = enrichToVoList(Collections.singletonList(detail));
        return list.get(0);
    }

    // ============================================================
    // 内部
    // ============================================================
    /** 凭证图 OSS id 列表拼成逗号分隔 string（与 t_plant_farm_records.proof_oss_ids 存法一致）。 */
    private String joinOssIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return null;
        }
        return ids.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 反查地块名（null 安全：id 为空或查无返 null，不抛）。CROSS-FLOW-002 组装事件载荷用。 */
    private String resolvePlotName(Long plotId) {
        if (plotId == null) {
            return null;
        }
        PlotInfo plot = plotMapper.selectById(plotId);
        return plot == null ? null : plot.getPlotName();
    }

    /** 反查作物名（null 安全）。 */
    private String resolveCropName(Long cropId) {
        if (cropId == null) {
            return null;
        }
        CropInfo crop = cropMapper.selectById(cropId);
        return crop == null ? null : crop.getCropName();
    }

    /** 反查班组名（null 安全）。 */
    private String resolveTeamName(Long teamId) {
        if (teamId == null) {
            return null;
        }
        PlantWorkTeam team = teamMapper.selectById(teamId);
        return team == null ? null : team.getTeamName();
    }

    private List<String> parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return Arrays.asList("pending", "picking");
        }
        List<String> parsed = Arrays.stream(status.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).toList();
        return parsed.isEmpty() ? Arrays.asList("pending", "picking") : parsed;
    }

    private List<PickTaskVo> enrichToVoList(List<PlantDetails> entities) {
        if (CollUtil.isEmpty(entities)) {
            return Collections.emptyList();
        }
        Set<Long> planIds = entities.stream().map(PlantDetails::getPlantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> plotIds = entities.stream().map(PlantDetails::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> cropIds = entities.stream().map(PlantDetails::getCropId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> teamIds = new HashSet<>();
        entities.forEach(d -> { if (d.getHarvestBy() != null) { teamIds.add(d.getHarvestBy()); } });

        Map<Long, String> planNoMap = planIds.isEmpty() ? Map.of()
            : planMapper.selectByIds(planIds).stream()
                .collect(Collectors.toMap(PlantPlan::getId, PlantPlan::getPlanNo, (a, b) -> a));
        Map<Long, PlotInfo> plotMap = plotIds.isEmpty() ? Map.of()
            : plotMapper.selectByIds(plotIds).stream()
                .collect(Collectors.toMap(PlotInfo::getId, p -> p, (a, b) -> a));
        Map<Long, String> cropMap = cropIds.isEmpty() ? Map.of()
            : cropMapper.selectByIds(cropIds).stream()
                .collect(Collectors.toMap(CropInfo::getId, CropInfo::getCropName, (a, b) -> a));
        Map<Long, String> teamMap = teamIds.isEmpty() ? Map.of()
            : teamMapper.selectByIds(teamIds).stream()
                .collect(Collectors.toMap(PlantWorkTeam::getId, PlantWorkTeam::getTeamName, (a, b) -> a));

        return entities.stream().map(d -> {
            PickTaskVo vo = new PickTaskVo();
            vo.setId(d.getId());
            vo.setPlantId(d.getPlantId());
            vo.setPlanNo(planNoMap.get(d.getPlantId()));
            vo.setPlotId(d.getPlotId());
            PlotInfo plot = plotMap.get(d.getPlotId());
            if (plot != null) {
                vo.setPlotCode(plot.getPlotCode());
                vo.setPlotName(plot.getPlotName());
            }
            vo.setCropId(d.getCropId());
            vo.setCropName(cropMap.get(d.getCropId()));
            vo.setEarliestHarvestdate(d.getEarliestHarvestdate());
            vo.setLastHarvestdate(d.getLastHarvestdate());
            vo.setBeginHarvestdate(d.getBeginHarvestdate());
            vo.setEndHarvestdate(d.getEndHarvestdate());
            vo.setHarvestStatus(d.getHarvestStatus());
            vo.setExpectedYield(d.getExpectedYield());
            vo.setActualYield(d.getActualYield());
            vo.setIsPick(d.getIsPick());
            vo.setHarvestBy(d.getHarvestBy());
            vo.setHarvestTeamName(teamMap.get(d.getHarvestBy()));
            return vo;
        }).collect(Collectors.toList());
    }
}
