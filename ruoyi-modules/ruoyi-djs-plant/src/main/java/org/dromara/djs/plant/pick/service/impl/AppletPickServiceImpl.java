package org.dromara.djs.plant.pick.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
    private final org.dromara.djs.plant.farm.mapper.FarmRecordsMapper farmRecordsMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlResolver imageUrlResolver;
    private final org.dromara.djs.plant.team.service.PlantTeamLinkService teamLinkService;
    /** row260：取「已录入未落地块」的采摘量（销售去向未结算部分），与 Σactual_yield 相加成已采产量。 */
    private final org.dromara.djs.plant.activity.mapper.PlantActivityMapper activityMapper;

    /** 作物 L2 默认图统一走果蔬（IMG-LIB-001）。 */
    private static final String CROP_BELONG_TYPE = "vegetable";

    /**
     * is_pick=2 表示非游客采摘（普通采收，字典 djs_yes_no：1=是 / 2=否）。
     * mp 工人采收全链路（listMyTasks/todaySummary/listCropTasks/listCropPlots）一律取 is_pick=2，
     * 排除 is_pick=1 的游客采摘活动。
     */
    private static final int IS_PICK_NORMAL = 2;

    /**
     * 游客采摘活动渠道（{@code plant_details.is_pick=1}）。row260：销售去向未结算量只属于本渠道
     * （{@code settlePickActivity} 只结算进 is_pick=1 地块），普通采收视图不得并入。
     */
    private static final int IS_PICK_ACTIVITY = 1;
    /** harvest_activity：采摘计划设为「采摘活动」(is_pick=1) 的采摘行为农事类型（djs_farm_work_type）。 */
    private static final String HARVEST_FARM_TYPE = "harvest_activity";
    /** harvest：普通采收 (is_pick=2) 农事类型，区别于游客采摘活动（FIX-PLT-AD-PICK-FARMTYPE-001）。 */
    private static final String HARVEST_NORMAL_FARM_TYPE = "harvest";
    /**
     * 育苗（保育）地块类型（{@code djs_plot_type='nursery'}）：92.1 育苗地块须先移栽才进采收，
     * 采收列表卡 / 作物详情地块列表均排除此类地块（与移栽域「保育地块=移栽源」口径一致）。
     */
    private static final String PLOT_TYPE_NURSERY = "nursery";

    @Override
    public List<PickTaskVo> listMyTasks(String status) {
        List<String> statusList = parseStatus(status);

        List<PlantDetails> entities = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getIsPick, IS_PICK_NORMAL)   // PLT-PICK-001 决策①：隐藏游客采摘活动
                // 只列「已经种下去」的地块（V6 row36）：后台把地块改回待种植后，种植日期与采摘窗口都被置空，
                // 而 MySQL 的 ASC 把 NULL 排在最前 —— 不加这条过滤，一块根本没种的地会顶到采收工任务列表第一条、
                // 且日期全是空白。row36 之前这两列 NOT NULL，不可能为空，所以以前没暴露。
                .isNotNull(PlantDetails::getBeginActualdate)
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
        // 不录重量。采摘重量由采摘去向录入 recordPickActivity 落账（非销售即时累加 actual_yield，销售结算时分摊）。
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

        // 1.1 首次「开始采摘」（pending→picking，赢得乐观激活）把本次所选采收班组写进
        //     plant_details.harvest_by，作为「采收班组」权威源。完成采摘（picking→completed）不再改写，
        //     使第二次录入时前端能反显出「开始采摘时所选班组」并锁定（保证反显=开始所选，不与本次脱节）。
        // row40：采收班组多选 —— 旧单列 harvest_by = 第一个（过渡兼容），全集 sync 中间表 role=harvest
        java.util.List<Long> effPickTeams = CollUtil.isNotEmpty(bo.getTeamIds())
            ? bo.getTeamIds().stream().filter(Objects::nonNull).distinct().collect(Collectors.toList())
            : (bo.getTeamId() == null ? Collections.emptyList() : java.util.List.of(bo.getTeamId()));
        if (wonActivation && !finish) {
            detail.setHarvestBy(effPickTeams.isEmpty() ? null : effPickTeams.get(0));
            teamLinkService.syncDetailTeams(detail.getId(),
                org.dromara.djs.plant.team.service.PlantTeamLinkService.ROLE_HARVEST, effPickTeams);
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
        }
        if (wonActivation) {
            // 首次离开 pending（开始采摘 pending→picking，或未开始直接完成 pending→completed）：
            // 把关联地块 plot_status 置 3（采摘），字典 djs_plot_status：1=空闲/2=种植/3=采摘。
            // 只在赢得乐观激活时翻一次，避免并发重复翻；直达 completed 也要翻，否则地块停在种植态、
            // 进不了退茬候选（submitRotation 要求 plot_status=3）。
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
        //    采收按班组记录：所选「采收班组」落 farm_records.farm_by（本次留痕）；operator_user_id 留空。
        //    首次开始采摘时同步写 plant_details.harvest_by（见 1.1），供完成采摘反显锁定同一班组。
        GrowRecordBo grow = new GrowRecordBo();
        // 口径（FIX-PLT-AD-PICK-FARMTYPE-001）：is_pick=1 设为采摘活动 → harvest_activity；其余普通采收 → harvest（采收）。
        grow.setFarmType(detail.getIsPick() != null && detail.getIsPick() == 1
            ? HARVEST_FARM_TYPE : HARVEST_NORMAL_FARM_TYPE);
        grow.setPlantId(detail.getPlantId());
        grow.setPlotId(detail.getPlotId());
        grow.setCropId(detail.getCropId());
        grow.setFarmBy(effPickTeams.isEmpty() ? bo.getTeamId() : effPickTeams.get(0));   // 采收班组落 farm_by（多选第一个）
        grow.setFarmByIds(effPickTeams);   // row40：farm_records 班组全集 sync 中间表
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
        // FIX-PLT-MP-PICK-SUMMARY-001：KPI 口径「当月」。完成率 = 当月完成地块数 / 当月总地块数（采摘窗口与当月相交）。
        //   当月采摘品种数 / 当月采摘量：只算「当月实际已采摘」明细（begin_harvestdate 落当月），排除仅计划采摘窗口
        //   与当月相交、但尚未真正开采的作物（row10①：pending 作物混入致品种数虚高）。采摘量 = 这些明细 actual_yield
        //   合计（actual_yield 是采摘量权威列；采摘流水表 t_plant_plant_activity 基地正常采收不写、恒空，故不以它为源，
        //   否则恒 0，见 row10②）。品种数与采摘量取自同一「已采摘」集，二者互相对齐。
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
                .select(PlantDetails::getHarvestStatus, PlantDetails::getCropId,
                    PlantDetails::getBeginHarvestdate, PlantDetails::getActualYield));

        long total = monthly.size();
        long completed = monthly.stream().filter(d -> "completed".equals(d.getHarvestStatus())).count();
        int rate = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);

        // 当月实际已采摘明细 = begin_harvestdate 落当月（真正开采过的，排除仅计划窗口相交的 pending 作物）。
        List<PlantDetails> picked = monthly.stream()
            .filter(d -> d.getBeginHarvestdate() != null
                && !d.getBeginHarvestdate().isBefore(firstDay)
                && !d.getBeginHarvestdate().isAfter(lastDay))
            .toList();

        int cropKindCount = (int) picked.stream()
            .map(PlantDetails::getCropId).filter(Objects::nonNull).distinct().count();

        // 当月采摘量 = 当月实际已采摘明细的 actual_yield 合计（本页 is_pick=2 采收）。
        //   采收无「销售」去向，不补未结算销售量——销售是采摘活动(is_pick=1)概念，且销售流水按作物聚合
        //   无 is_pick 维度，若在此补加会把同作物采摘活动的销售误算进采收当月 KPI（row176 clean-QA 命中）。
        BigDecimal monthWeight = picked.stream()
            .map(d -> d.getActualYield() == null ? BigDecimal.ZERO : d.getActualYield())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

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
                // r55：采摘只显「种植完成」地块——与详情页 listCropPlots 同口径（plant_status='completed'），
                // 保证列表卡 plotCount 与点进详情看到的地块数一致。
                .eq(PlantDetails::getPlantStatus, "completed")
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
            // 92.1：育苗（保育 plot_type='nursery'）地块须先移栽才进采收，采收列表卡排除。
            .filter(d -> {
                PlotInfo plot = plotMap.get(d.getPlotId());
                if (plot == null || PLOT_TYPE_NURSERY.equals(plot.getPlotType())) {
                    return false;
                }
                return plot.getPlotStatus() != null
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

        // row260：各作物「已录入未落地块」的采摘量（销售去向未结算部分），一次批量取避免 N+1。
        // ⚠️ 只在采摘活动渠道（is_pick=1）并入：settlePickActivity 只把销售量结算进 is_pick=1 地块，
        // 算到普通采收（is_pick=2）头上就是凭空多出一笔与该渠道无关的产量（QA 对抗验收实测复现：
        // 同一作物同时有 is_pick=1/2 两批地块时，isPick=2 的 cropPickedTotal 也返回了那 100kg）。
        Map<Long, BigDecimal> pendingByCrop = pickFlag == IS_PICK_ACTIVITY
            ? loadUnsettledPickedByCrops(cropIds)
            : Map.of();

        Map<String, BigDecimal> cropTaskLossMap = loadDisasterLossByPlotCrop(cropIds);
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
            // row267：**逐地块**先扣灾害再求和。先求和后扣的话，某地块损失超过它自己的理论产量时
            // 会把别的地块的产量一起吃掉（钳零要落在地块粒度上）。
            vo.setExpectedYield(rows.stream()
                .map(d -> netExpectedYield(d, cropTaskLossMap))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            // 已采重量 = Σ(本卡可见地块 actual_yield) + 本作物未结算销售量，与详情页头卡 cropPickedTotal 严格同源。
            //   row260（Kevin 2026-08-01 定「计入，按作物汇总」）：销售去向按 DENGBO-R4/R24 故意不即时分摊，
            //   要等整批地块采摘完成才均分进 actual_yield；结算前只看 Σactual_yield 会让「录了 100kg」显示成 0
            //   （客户原话「已采产量没有显示数据」）。故把未结算量并进来，录了即可见。
            //   注：未结算量按 **作物** 汇总（流水只带 crop_id 不带地块/批次），同一作物存在多批次时
            //   这部分会算在当前展示的批次卡上——这是取「即时可见」而接受的已知代价。
            vo.setActualYield(rows.stream()
                .map(d -> d.getActualYield() == null ? BigDecimal.ZERO : d.getActualYield())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(pendingByCrop.getOrDefault(e.getKey(), BigDecimal.ZERO)));
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
                // r55：采摘只显「种植完成」地块——明细 plant_status='completed'（种植阶段已结束才可采摘）。
                // 未完成种植（plant_status!='completed'）的明细不进采摘详情地块列表（邓博定：种植完成=completed）。
                .eq(PlantDetails::getPlantStatus, "completed")
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
                // 92.1：育苗（保育 plot_type='nursery'）地块须先移栽才进采收，作物详情地块列表排除。
                .filter(d -> {
                    PlotInfo plot = detailPlotMap.get(d.getPlotId());
                    if (plot == null || PLOT_TYPE_NURSERY.equals(plot.getPlotType())) {
                        return false;
                    }
                    return plot.getPlotStatus() != null
                        && (plot.getPlotStatus() == 2 || plot.getPlotStatus() == 3);
                })
                // r21：采摘完成（harvest_status='completed'）的地块只在「完成当天」展示，end_harvestdate != 今天
                //   则次日起消失；与列表卡 listCropTasks 同口径，保证列表卡 plotCount = 点进详情地块数。
                .filter(d -> !"completed".equals(d.getHarvestStatus())
                    || today.equals(d.getEndHarvestdate()))
                .toList();
        }
        return enrichToVoList(entities);
    }

    @Override
    public boolean isCropSettled(Long cropId, Integer isPick) {
        if (cropId == null) {
            throw new ServiceException("作物 id 必填");
        }
        int pickFlag = isPick == null ? IS_PICK_NORMAL : isPick;
        // row7（何涛 2026-07-17）：头卡「采摘重量录入」按钮置灰口径 = 该作物采摘活动地块**全部已结算**
        //   （无未结算地块）。原「存在任一已结算地块即置灰」太黏——首轮「录入完成」后再新增采摘活动地块
        //   （pick_settle_round=0 未结算）时按钮仍恒灰、无法为新地块继续录入。改为「无未结算地块才置灰」：
        //   新地块出现即 unsettled>0 → 返 false → 按钮重新可用，与 settlePickActivity 按 settle_round 隔离
        //   新地块单独结算同口径。仍刻意不带日期/当月窗口/plot_status 过滤，使跨完成窗口后（completed 地块行
        //   被列表日期驱逐、plots 变空）头卡按钮仍能从服务端恢复正确置灰（保留 row223 修复）。
        Long total = detailsMapper.selectCount(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getCropId, cropId)
                .eq(PlantDetails::getIsPick, pickFlag));
        if (total == null || total == 0) {
            return false;   // 无采摘活动地块 → 不置灰
        }
        Long unsettled = detailsMapper.selectCount(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getCropId, cropId)
                .eq(PlantDetails::getIsPick, pickFlag)
                .and(w -> w.eq(PlantDetails::getPickSettleRound, 0)
                    .or().isNull(PlantDetails::getPickSettleRound)));
        return unsettled != null && unsettled == 0;   // 无未结算地块 = 全部已结算 → 置灰
    }

    @Override
    public BigDecimal cropPickedTotal(Long cropId, Integer isPick) {
        if (cropId == null) {
            throw new ServiceException("作物 id 必填");
        }
        // Σ(当前展示地块 actual_yield)：复用 listCropPlots 同口径地块集，与 mp 头卡地块卡合计、
        //   以及列表卡 listCropTasks 三者严格同源（row204：列表与详情必须一致，只算详情页展示的地块）。
        // row260：再并上本作物「已录入未落地块」的未结算销售量，与 listCropTasks 同步（否则列表 100、详情 0 打架）。
        // 同样只在采摘活动渠道（is_pick=1）并入 —— 结算只落 is_pick=1 地块，普通采收视图不该看到这笔。
        int pickFlag = isPick == null ? IS_PICK_NORMAL : isPick;
        BigDecimal pending = pickFlag == IS_PICK_ACTIVITY
            ? loadUnsettledPickedByCrops(List.of(cropId)).getOrDefault(cropId, BigDecimal.ZERO)
            : BigDecimal.ZERO;
        return listCropPlots(null, cropId, isPick).stream()
            .map(v -> v.getActualYield() == null ? BigDecimal.ZERO : v.getActualYield())
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(pending);
    }

    /**
     * 批量取各作物「已录入但还没落到地块」的采摘量（row260）。
     *
     * <p>= 未结算的销售流水（{@code plot_id IS NULL} 且 {@code settle_round∈{0,NULL}}）。
     * 谓词两条缺一不可的原因见 {@code PlantActivityMapper#selectUnsettledPickedByCrops} 注释
     * （非销售去向已即时写进 actual_yield，只按 settle_round 过滤会双算）。</p>
     *
     * @param cropIds 作物 id 集合；空集直接返空 map（不打库）
     * @return {@code cropId → 未结算量}；无未结算流水的作物不在 map 内（调用方用 getOrDefault(.., ZERO)）
     */
    private Map<Long, BigDecimal> loadUnsettledPickedByCrops(Collection<Long> cropIds) {
        if (cropIds == null || cropIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Map<String, Object> row : activityMapper.selectUnsettledPickedByCrops(cropIds)) {
            Object cid = row.get("cropId");
            Object sum = row.get("pendingSum");
            if (cid == null || sum == null) {
                continue;
            }
            map.put(((Number) cid).longValue(), new BigDecimal(sum.toString()));
        }
        return map;
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


    /**
     * 「预计产量」统一扣灾害损失（小程序 row267）。
     *
     * <p>甲方口径：预计产量 = 理论产量 − 该地块该作物累计灾害损失，且不显负数。
     * 毛菜处理域（{@code VegetableHandleMapper} / {@code PlantingRecordMapper}）与 admin 采摘计划
     * （{@code PickPlanMapper}）早已按此实现，采摘域此前漏扣 → 同一作物在不同页面显示两个数。</p>
     *
     * @param cropIds 本批涉及的作物
     * @return {@code "plotId|cropId" → 灾害损失产量}
     */
    private Map<String, BigDecimal> loadDisasterLossByPlotCrop(Set<Long> cropIds) {
        if (CollUtil.isEmpty(cropIds)) {
            return Map.of();
        }
        Map<String, BigDecimal> map = new HashMap<>();
        for (Map<String, Object> row : farmRecordsMapper.selectDisasterLossByPlotCrop(cropIds)) {
            Object plotId = row.get("plotId");
            Object cropId = row.get("cropId");
            Object loss = row.get("lossYield");
            if (plotId == null || cropId == null || loss == null) {
                continue;
            }
            map.put(plotId + "|" + cropId, new BigDecimal(loss.toString()));
        }
        return map;
    }

    /** 单地块预计净产量 = max(0, 理论产量 − 该地块该作物灾害损失)。 */
    private BigDecimal netExpectedYield(PlantDetails d, Map<String, BigDecimal> lossMap) {
        BigDecimal gross = d.getExpectedYield() == null ? BigDecimal.ZERO : d.getExpectedYield();
        BigDecimal loss = lossMap.getOrDefault(d.getPlotId() + "|" + d.getCropId(), BigDecimal.ZERO);
        return gross.subtract(loss).max(BigDecimal.ZERO);
    }

    private List<PickTaskVo> enrichToVoList(List<PlantDetails> entities) {
        if (CollUtil.isEmpty(entities)) {
            return Collections.emptyList();
        }
        Set<Long> planIds = entities.stream().map(PlantDetails::getPlantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> plotIds = entities.stream().map(PlantDetails::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> cropIds = entities.stream().map(PlantDetails::getCropId).filter(Objects::nonNull).collect(Collectors.toSet());
        // row267：预计产量按地块扣灾害损失，与毛菜处理 / admin 采摘计划同口径
        Map<String, BigDecimal> lossMap = loadDisasterLossByPlotCrop(cropIds);
        Set<Long> teamIds = new HashSet<>();
        entities.forEach(d -> {
            if (d.getHarvestBy() != null) { teamIds.add(d.getHarvestBy()); }
            if (d.getPlantBy() != null) { teamIds.add(d.getPlantBy()); }   // row40：种植班组预填
        });
        // 班组多选中间表 enrich（row40）：批量取各明细 role → 名/id
        Set<Long> detailIds = entities.stream().map(PlantDetails::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Map<String, java.util.List<String>>> teamNamesMap = teamLinkService.detailTeamNames(detailIds);
        Map<Long, Map<String, java.util.List<Long>>> teamIdsMap = teamLinkService.detailTeamIds(detailIds);

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
            vo.setExpectedYield(netExpectedYield(d, lossMap));
            vo.setActualYield(d.getActualYield());
            vo.setIsPick(d.getIsPick());
            // row223：采摘活动「录入完成/称重完成」持久标志——pick_settle_round>0 = 已结算（已录入完成）。
            //   mp 详情头卡据此从服务端恢复「采摘重量录入」按钮置灰态（重进页亦保持置灰）。
            vo.setPickSettleRound(d.getPickSettleRound());
            vo.setHarvestBy(d.getHarvestBy());
            vo.setHarvestTeamName(d.getHarvestBy() == null ? null : teamMap.get(d.getHarvestBy()));
            vo.setMemberCount(d.getHarvestBy() == null ? null
                : Math.toIntExact(teamMemberCountMap.getOrDefault(d.getHarvestBy(), 0L)));
            // row40：种植班组预填字段 + 多班组全集（中间表优先，回落旧单列）
            vo.setPlantBy(d.getPlantBy());
            vo.setPlantTeamName(d.getPlantBy() == null ? null : teamMap.get(d.getPlantBy()));
            Map<String, java.util.List<String>> roleNames = teamNamesMap.getOrDefault(d.getId(), Map.of());
            Map<String, java.util.List<Long>> roleIds = teamIdsMap.getOrDefault(d.getId(), Map.of());
            java.util.List<String> plantNames = roleNames.get(org.dromara.djs.plant.team.service.PlantTeamLinkService.ROLE_PLANT);
            java.util.List<String> harvestNames = roleNames.get(org.dromara.djs.plant.team.service.PlantTeamLinkService.ROLE_HARVEST);
            java.util.List<Long> plantIds = roleIds.get(org.dromara.djs.plant.team.service.PlantTeamLinkService.ROLE_PLANT);
            java.util.List<Long> harvestIds = roleIds.get(org.dromara.djs.plant.team.service.PlantTeamLinkService.ROLE_HARVEST);
            vo.setPlantTeamNames(CollUtil.isNotEmpty(plantNames) ? plantNames
                : (vo.getPlantTeamName() == null ? Collections.emptyList() : java.util.List.of(vo.getPlantTeamName())));
            vo.setPlantByIds(CollUtil.isNotEmpty(plantIds) ? plantIds
                : (d.getPlantBy() == null ? Collections.emptyList() : java.util.List.of(d.getPlantBy())));
            vo.setHarvestTeamNames(CollUtil.isNotEmpty(harvestNames) ? harvestNames
                : (vo.getHarvestTeamName() == null ? Collections.emptyList() : java.util.List.of(vo.getHarvestTeamName())));
            vo.setHarvestByIds(CollUtil.isNotEmpty(harvestIds) ? harvestIds
                : (d.getHarvestBy() == null ? Collections.emptyList() : java.util.List.of(d.getHarvestBy())));
            return vo;
        }).collect(Collectors.toList());
    }
}
