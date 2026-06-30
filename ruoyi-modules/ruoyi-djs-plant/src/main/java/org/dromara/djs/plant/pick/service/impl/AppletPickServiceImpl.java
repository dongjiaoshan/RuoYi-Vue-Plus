package org.dromara.djs.plant.pick.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.activity.mapper.PlantActivityMapper;
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
    private final PlantActivityMapper plantActivityMapper;
    private final IFarmRecordsService farmRecordsService;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlResolver imageUrlResolver;

    /** 作物 L2 默认图统一走果蔬（IMG-LIB-001）。 */
    private static final String CROP_BELONG_TYPE = "vegetable";

    /**
     * is_pick=2 表示非游客采摘（普通采收，字典 djs_yes_no：1=是 / 2=否）。
     * mp 工人采收全链路（listMyTasks/todaySummary/listCropTasks/listCropPlots）一律取 is_pick=2，
     * 排除 is_pick=1 的游客采摘活动。
     */
    private static final int IS_PICK_NORMAL = 2;
    /** harvest_activity：采摘计划设为「采摘活动」(is_pick=1) 的采摘行为农事类型（djs_farm_work_type）。 */
    private static final String HARVEST_FARM_TYPE = "harvest_activity";
    /** harvest：普通采收 (is_pick=2) 农事类型，区别于游客采摘活动（FIX-PLT-AD-PICK-FARMTYPE-001）。 */
    private static final String HARVEST_NORMAL_FARM_TYPE = "harvest";

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
        String targetStatus = finish ? "completed" : "picking";

        // SM-4 并发加固：地块「首次离开 pending（待采摘）」是发 PlantPickedEvent 的唯一闸门。
        // 用带条件乐观 UPDATE（SET harvest_status=target WHERE id=? AND harvest_status='pending'）原子推进，
        // affected>0 才是「本次调用赢得首次激活」——两并发请求只有一个 affected=1，另一个 affected=0，
        // 从而 CROSS-FLOW-002 待办事件保证只发一次（杜绝 read-modify-write 下并发重复发）。
        // wonActivation 同时覆盖 pending→picking（开始采摘）与 pending→completed（未开始直接完成）两路：
        // 谁把行从 pending 推走谁负责发事件。
        int affected = detailsMapper.update(null,
            new LambdaUpdateWrapper<PlantDetails>()
                .set(PlantDetails::getHarvestStatus, targetStatus)
                .eq(PlantDetails::getId, detail.getId())
                .eq(PlantDetails::getHarvestStatus, "pending"));
        boolean wonActivation = affected > 0;

        // 1. 首次采收回填 begin_harvestdate
        if (detail.getBeginHarvestdate() == null) {
            detail.setBeginHarvestdate(bo.getHarvestDate());
        }

        // 2. harvest_status 流转：pending → picking；finish 时 → completed。
        //    pending→target 已由上面乐观 UPDATE 原子完成；这里 entity 同步成 target，
        //    并补齐 picking→completed（行已 picking 时再完成）的状态推进——该路 wonActivation=false（CAS 命不中 pending），
        //    不重发事件但仍正确落 completed。
        detail.setHarvestStatus(targetStatus);
        if (finish) {
            // end_actualdate 归「种植完成」finishPlant 独占，采摘完成不再写
            detail.setEndHarvestdate(bo.getHarvestDate());
            // 3. average_yield = actual_yield / plot_area（actual_yield 由采摘活动管理累加；为空 / area 为 0 时跳过，不抛）
            BigDecimal yield = detail.getActualYield();
            BigDecimal area = detail.getPlotArea();
            if (yield != null && area != null && area.compareTo(BigDecimal.ZERO) > 0) {
                detail.setAverageYield(yield.divide(area, 3, RoundingMode.HALF_UP));
            }
        } else if (wonActivation) {
            // 「开始采摘」首次 pending→picking（赢得乐观推进）：把关联地块 plot_status 从 2（种植）置 3（采摘），
            // 字典 djs_plot_status：1=空闲/2=种植/3=采摘。只在首次激活时翻一次，避免并发重复翻。
            if (detail.getPlotId() != null) {
                PlotInfo plot = plotMapper.selectById(detail.getPlotId());
                if (plot != null) {
                    plot.setPlotStatus(3);
                    plotMapper.updateById(plot);
                }
            }
        }
        // 决策①：绝不动 is_pick
        detailsMapper.updateById(detail);

        // 5. INSERT 一行 t_plant_farm_records（farm_type='harvest_activity'，可追溯）
        //    复用 IFarmRecordsService.submitGrow（内含 record_no 生成 + plot_type/crop_name 冗余）。
        //    采收按班组记录：所选「采收班组」落 farm_by；operator_user_id 留空，
        //    且不覆盖 admin 采摘计划已分配的 detail.harvest_by（仅记录本次采收班组）。
        GrowRecordBo grow = new GrowRecordBo();
        // 口径（FIX-PLT-AD-PICK-FARMTYPE-001）：is_pick=1 设为采摘活动 → harvest_activity；其余普通采收 → harvest（采收）。
        grow.setFarmType(detail.getIsPick() != null && detail.getIsPick() == 1
            ? HARVEST_FARM_TYPE : HARVEST_NORMAL_FARM_TYPE);
        grow.setPlantId(detail.getPlantId());
        grow.setPlotId(detail.getPlotId());
        grow.setCropId(detail.getCropId());
        grow.setFarmBy(bo.getTeamId());   // 采收班组落 farm_by
        grow.setFarmDate(bo.getHarvestDate());
        grow.setProofOssIds(joinOssIds(bo.getProofOssIds()));
        grow.setRemark(bo.getRemark());
        farmRecordsService.submitGrow(grow);

        // 6. CROSS-FLOW-002：地块首次进入活跃采摘态（待采摘 → 采摘中/已完成）时发 PlantPickedEvent，
        //    warehouse 域 listener 在 AFTER_COMMIT 写 1 行 t_warehouse_planting_record(handle_status='pending') 待办，
        //    使「开始采摘」即在仓库毛菜处理出现该批次（0619 客户口径）。
        //    wonActivation（上面乐观 UPDATE affected>0，即本次调用真把行从 pending 推走）保证只发一次：
        //    后续逐次采收 / 完成采摘 affected=0 不重发，避免同 plot+crop 多行重复待办，并发也只有一个赢家。
        if (wonActivation) {
            PlantPickedPayload payload = new PlantPickedPayload();
            payload.setPlotId(detail.getPlotId());
            payload.setCropId(detail.getCropId());
            payload.setPlotName(resolvePlotName(detail.getPlotId()));
            payload.setCropName(resolveCropName(detail.getCropId()));
            payload.setPlantDate(detail.getBeginActualdate());
            // 采摘日期：完成采摘取 end_harvestdate；开始采摘(尚未完成)取 begin_harvestdate；皆空由 listener 兜今天
            payload.setHarvestDate(detail.getEndHarvestdate() != null
                ? detail.getEndHarvestdate() : detail.getBeginHarvestdate());
            payload.setHarvestWeight(detail.getActualYield());   // 累计采摘权威值（开始采摘时多为 null，listener 兜 0，真实重量由毛菜处理称重录入）
            payload.setTeamId(detail.getHarvestBy());
            payload.setTeamName(resolveTeamName(detail.getHarvestBy()));
            eventPublisher.publishEvent(new PlantPickedEvent(this, payload));
        }
    }

    @Override
    public PickSummaryVo todaySummary() {
        // FIX-PLT-MP-PICK-SUMMARY-001：KPI 口径由「今日」改「当月」，与采收列表（listCropTasks）当月窗口同口径。
        //   完成率 = 当月完成地块数 / 当月总地块数；当月采摘品种数 = 当月明细 distinct crop；
        //   当月采摘量 = 当月 t_plant_plant_activity.daily_weight 合计（235 客户权威口径）。
        // VO 字段名沿用（taskCompletionRate / todayCropKindCount / todayWeight），前端同步改文案。
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay = today.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

        // 当月地块 = is_pick=2（非游客）且采摘窗口 [earliest, last] 与当月 [firstDay, lastDay] 相交的明细。
        List<PlantDetails> monthly = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getIsPick, IS_PICK_NORMAL)
                .le(PlantDetails::getEarliestHarvestdate, lastDay)
                .ge(PlantDetails::getLastHarvestdate, firstDay)
                .select(PlantDetails::getHarvestStatus, PlantDetails::getCropId));

        long total = monthly.size();
        long completed = monthly.stream().filter(d -> "completed".equals(d.getHarvestStatus())).count();
        int rate = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);

        int cropKindCount = (int) monthly.stream()
            .map(PlantDetails::getCropId).filter(Objects::nonNull).distinct().count();

        // 当月采摘量 = 当月活动 daily_weight 合计（与作物卡 actualYield 同口径，避免与 actual_yield 累计值口径漂移）。
        BigDecimal monthWeight = plantActivityMapper.selectTotalWeightInRange(firstDay, lastDay);
        if (monthWeight == null) {
            monthWeight = BigDecimal.ZERO;
        }

        PickSummaryVo vo = new PickSummaryVo();
        vo.setTaskCompletionRate(rate);
        vo.setTodayCropKindCount(cropKindCount);
        vo.setTodayWeight(monthWeight);
        return vo;
    }

    @Override
    public List<PickCropTaskVo> listCropTasks(Long zoneId, Integer isPick) {
        // 当月采摘任务 = 按 isPick 过滤（默认 is_pick=2 正常采收，隐藏游客采摘活动；
        // 「采摘活动管理」页传 isPick=1 只看游客采摘活动）且采摘窗口与当月有交集的明细，
        // 应用层按片区过滤 + 作物聚合（V1 数据量小）。
        // 窗口与当月有交集判定：earliest_harvestdate ≤ 当月最后一天 AND last_harvestdate ≥ 当月第一天
        //   （即采摘窗口 [earliest, last] 与 [firstDay, lastDay] 区间相交），跨月在采的作物也展示。
        int pickFlag = isPick == null ? IS_PICK_NORMAL : isPick;
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay = today.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        List<PlantDetails> all = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getIsPick, pickFlag)
                .le(PlantDetails::getEarliestHarvestdate, lastDay)
                .ge(PlantDetails::getLastHarvestdate, firstDay));
        if (CollUtil.isEmpty(all)) {
            return Collections.emptyList();
        }

        // 取地块 → 片区映射（用于 zone 过滤）
        Set<Long> plotIds = all.stream().map(PlantDetails::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PlotInfo> plotMap = plotIds.isEmpty() ? Map.of()
            : plotMapper.selectByIds(plotIds).stream()
                .collect(Collectors.toMap(PlotInfo::getId, p -> p, (a, b) -> a));

        List<PlantDetails> filtered = all.stream()
            // r19：只展示地块 plot_status IN(2 种植,3 采摘)，排除 1 空闲（字典 djs_plot_status）。
            //   plot 查无视为不在采摘态，排除。
            .filter(d -> {
                PlotInfo plot = plotMap.get(d.getPlotId());
                return plot != null && plot.getPlotStatus() != null
                    && (plot.getPlotStatus() == 2 || plot.getPlotStatus() == 3);
            })
            // r21：采摘完成（harvest_status='completed'）的地块只在「完成当天」展示，
            //   end_harvestdate != 今天则次日起消失；非 completed 不受此限。
            .filter(d -> !"completed".equals(d.getHarvestStatus())
                || today.equals(d.getEndHarvestdate()))
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
            // 已采重量 = SUM(t_plant_details.actual_yield)，与作物详情头同源同口径（采摘重量录入累加进同一列）
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
    public List<PickTaskVo> listCropPlots(Long planId, Long cropId, Integer isPick) {
        if (cropId == null) {
            throw new ServiceException("作物 id 必填");
        }
        // 与采摘任务列表卡（listCropTasks）同口径按 isPick 过滤（默认 is_pick=2 普通采收；
        // 「采摘活动管理」页传 isPick=1 游客采摘活动），点卡进详情看到的是同一批地块。
        // 同样只展示采摘窗口与「当月」相交的地块：earliest_harvestdate ≤ 当月最后一天
        //   AND last_harvestdate ≥ 当月第一天（窗口 [earliest, last] 与 [firstDay, lastDay] 相交）。
        //   否则会把次月 / 跨月地块也带进详情，与列表卡口径不一致（FIX row4）。
        int pickFlag = isPick == null ? IS_PICK_NORMAL : isPick;
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay = today.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        List<PlantDetails> entities = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getIsPick, pickFlag)
                .eq(PlantDetails::getCropId, cropId)
                .eq(planId != null, PlantDetails::getPlantId, planId)
                .le(PlantDetails::getEarliestHarvestdate, lastDay)
                .ge(PlantDetails::getLastHarvestdate, firstDay)
                .orderByAsc(PlantDetails::getEarliestHarvestdate)
                .orderByAsc(PlantDetails::getId));
        // r19：与列表卡（listCropTasks）同口径，只展示地块 plot_status IN(2 种植,3 采摘)，排除 1 空闲。
        //   plot 查无视为不在采摘态，排除。
        if (!entities.isEmpty()) {
            Set<Long> detailPlotIds = entities.stream()
                .map(PlantDetails::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, PlotInfo> detailPlotMap = detailPlotIds.isEmpty() ? Map.of()
                : plotMapper.selectByIds(detailPlotIds).stream()
                    .collect(Collectors.toMap(PlotInfo::getId, p -> p, (a, b) -> a));
            entities = entities.stream()
                .filter(d -> {
                    PlotInfo plot = detailPlotMap.get(d.getPlotId());
                    return plot != null && plot.getPlotStatus() != null
                        && (plot.getPlotStatus() == 2 || plot.getPlotStatus() == 3);
                })
                .toList();
        }
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
            vo.setHarvestTeamName(d.getHarvestBy() == null ? null : teamMap.get(d.getHarvestBy()));
            vo.setMemberCount(d.getHarvestBy() == null ? null
                : Math.toIntExact(teamMemberCountMap.getOrDefault(d.getHarvestBy(), 0L)));
            return vo;
        }).collect(Collectors.toList());
    }
}
