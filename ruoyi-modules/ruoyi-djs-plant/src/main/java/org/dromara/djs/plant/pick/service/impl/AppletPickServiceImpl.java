package org.dromara.djs.plant.pick.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.image.service.ImageUrlResolver;
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
import org.dromara.djs.plant.team.domain.PlantWorkPeople;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.mapper.PlantWorkPeopleMapper;
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
    private final PlantWorkPeopleMapper peopleMapper;
    private final IFarmRecordsService farmRecordsService;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlResolver imageUrlResolver;

    /** 作物 L2 默认图统一走果蔬（IMG-LIB-001）。 */
    private static final String CROP_BELONG_TYPE = "vegetable";

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
        // FIX-PLT-MP-PICK-001（#3=a 按原型）：采收 tab 只走"开始/完成采摘"流程（采摘人员 + 日期），
        // 不录重量。采摘重量改由农事「采摘活动管理」submitHarvestWeight 累加 actual_yield。
        // 捕获更新前的旧态：仅"非 completed → completed"首次流转才发跨域事件（幂等，防重复点完成产生多行待办）
        boolean alreadyCompleted = "completed".equals(detail.getHarvestStatus());

        // 1. 首次采收回填 begin_harvestdate
        if (detail.getBeginHarvestdate() == null) {
            detail.setBeginHarvestdate(bo.getHarvestDate());
        }

        // 2. harvest_status 流转：pending → picking；finish 时 → completed
        if (finish) {
            detail.setHarvestStatus("completed");
            detail.setEndActualdate(LocalDate.now());
            detail.setEndHarvestdate(bo.getHarvestDate());
            // 3. average_yield = actual_yield / plot_area（actual_yield 由采摘活动管理累加；为空 / area 为 0 时跳过，不抛）
            BigDecimal yield = detail.getActualYield();
            BigDecimal area = detail.getPlotArea();
            if (yield != null && area != null && area.compareTo(BigDecimal.ZERO) > 0) {
                detail.setAverageYield(yield.divide(area, 3, RoundingMode.HALF_UP));
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
            throw new ServiceException("该地块未指派采摘班组，无法录入采收（请在采摘计划中指派）");
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
        // 全部非游客采摘明细（is_pick=2），应用层按片区过滤 + 作物聚合（V1 数据量小）。
        // 181-1（决策#1=a）：只取「当前日期落在 [earliest_harvestdate, last_harvestdate] 采摘窗口」的明细
        //   （earliest_harvestdate ≤ today ≤ last_harvestdate），区间外的不在采摘活动列表显示。
        LocalDate today = LocalDate.now();
        List<PlantDetails> all = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getIsPick, IS_PICK_NORMAL)
                .le(PlantDetails::getEarliestHarvestdate, today)
                .ge(PlantDetails::getLastHarvestdate, today));
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
                // cropImg 暂存作物 image_oss_id（L1），return 前统一走 resolver 转 url + 兜底
                vo.setCropImg(crop.getImageOssId());
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
        // IMG-LIB-001：cropImg 走 4 层 resolver（L1 image_oss_id → L2 vegetable → L3 全局），批量禁 N+1
        if (!result.isEmpty()) {
            List<ImageUrlResolver.Item> items = result.stream()
                .map(v -> new ImageUrlResolver.Item(v.getCropImg(), CROP_BELONG_TYPE))
                .toList();
            List<String> urls = imageUrlResolver.resolveList(items);
            if (urls.size() == result.size()) {
                for (int i = 0; i < result.size(); i++) {
                    result.get(i).setCropImg(urls.get(i));
                }
            }
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
        // 班组成员数：t_plant_work_people 按 team_id 计数（V1 数据量小，应用层聚合；
        // 供 mp 区分「未指派班组」vs「指派了但班组无成员」两种空 picker）。
        Map<Long, Long> teamMemberCountMap = teamIds.isEmpty() ? Map.of()
            : peopleMapper.selectList(
                    new LambdaQueryWrapper<PlantWorkPeople>()
                        .select(PlantWorkPeople::getTeamId)
                        .in(PlantWorkPeople::getTeamId, teamIds))
                .stream()
                .filter(p -> p.getTeamId() != null)
                .collect(Collectors.groupingBy(PlantWorkPeople::getTeamId, Collectors.counting()));

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
            vo.setPlantMonth(d.getPlantMonth());
            vo.setPlantPeriod(d.getPlantPeriod());
            vo.setPlantDate(d.getBeginActualdate());
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
            vo.setMemberCount(d.getHarvestBy() == null ? null
                : Math.toIntExact(teamMemberCountMap.getOrDefault(d.getHarvestBy(), 0L)));
            return vo;
        }).collect(Collectors.toList());
    }
}
