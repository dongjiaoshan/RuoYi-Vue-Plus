package org.dromara.djs.plant.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.plant.activity.domain.PlantActivity;
import org.dromara.djs.plant.activity.domain.bo.PickActivityRecordBo;
import org.dromara.djs.plant.activity.mapper.PlantActivityMapper;
import org.dromara.djs.plant.activity.service.IPlantActivityService;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * 采摘活动 Service 实现（FIX-PLT-HARVEST-ACTIVITY-001 + DENGBO-R4 采摘去向）。
 *
 * @author djs
 * @since FIX-PLT-HARVEST-ACTIVITY-001
 */
@Slf4j
@Service
public class PlantActivityServiceImpl extends DjsBaseServiceImpl<PlantActivityMapper, PlantActivity>
    implements IPlantActivityService {

    /** 采摘去向「销售」value（djs_pick_dest）：不选地块、按作物维度累加产量。 */
    private static final String PICK_DEST_SALE = "sale";

    /** 种植状态「已完成」value（djs_plant_status / t_plant_plant_details.plant_status）：可采摘前置。 */
    private static final String PLANT_STATUS_COMPLETED = "completed";

    /** 采摘状态「已完成」value（djs_pick_status / t_plant_plant_details.harvest_status）：终态明细不再作累加首选。 */
    private static final String PICK_STATUS_COMPLETED = "completed";

    private final CropInfoMapper cropInfoMapper;
    private final PlantDetailsMapper plantDetailsMapper;
    private final org.dromara.djs.plant.team.service.PlantTeamLinkService teamLinkService;

    public PlantActivityServiceImpl(PlantActivityMapper baseMapper,
                                    CropInfoMapper cropInfoMapper,
                                    PlantDetailsMapper plantDetailsMapper,
                                    org.dromara.djs.plant.team.service.PlantTeamLinkService teamLinkService) {
        super(baseMapper);
        this.cropInfoMapper = cropInfoMapper;
        this.plantDetailsMapper = plantDetailsMapper;
        this.teamLinkService = teamLinkService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordDailyWeight(Long cropId, LocalDate activityDate, BigDecimal dailyWeight, Long activityBy) {
        if (cropId == null) {
            throw new ServiceException("作物 id 不能为空");
        }
        if (activityDate == null) {
            throw new ServiceException("采摘日期不能为空");
        }
        if (dailyWeight == null || dailyWeight.signum() <= 0) {
            throw new ServiceException("采摘重量必须大于 0");
        }

        // DENGBO-R4 起 per-event：原 UNIQUE(crop,date,班组) 已 DROP，每次 INSERT 一行新流水
        // （pick_dest 留 NULL = 历史销售口径；旧采摘重量录入端点不带去向，走此路径）。
        PlantActivity activity = new PlantActivity();
        activity.setCropId(cropId);
        activity.setActivityDate(activityDate);
        activity.setDailyWeight(dailyWeight);
        activity.setPickWeight(dailyWeight);
        activity.setActivityBy(activityBy);
        baseMapper.insert(activity);
        // row40：采摘活动班组多选中间表同步（单值写入路径，保持中间表与旧单列一致）
        if (activityBy != null) {
            teamLinkService.syncActivityTeams(activity.getId(), List.of(activityBy));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordPickActivity(PickActivityRecordBo bo) {
        if (bo == null) {
            throw new ServiceException("采摘去向录入参数为空");
        }
        if (bo.getCropId() == null) {
            throw new ServiceException("作物 id 不能为空");
        }
        BigDecimal weight = bo.getPickWeight();
        boolean finish = bo.getFinishFlag() != null && bo.getFinishFlag() == 1;
        boolean hasWeight = weight != null && weight.signum() > 0;
        // DENGBO-R24：勾了「录入完成」但没填本次采摘重量 → 只做结算分摊、不录入新流水（结算-only）。
        if (finish && !hasWeight) {
            settlePickActivity(bo.getCropId());
            return null;
        }
        if (bo.getActivityDate() == null) {
            throw new ServiceException("采摘日期不能为空");
        }
        if (weight == null || weight.signum() <= 0) {
            throw new ServiceException("采摘重量必须大于 0");
        }
        String dest = bo.getPickDest();
        if (dest == null || dest.isBlank()) {
            throw new ServiceException("请选择采摘去向");
        }
        boolean sale = PICK_DEST_SALE.equals(dest);
        if (!sale && bo.getPlotId() == null) {
            throw new ServiceException("非销售去向必须选择地块");
        }

        // 1. INSERT per-event 行（携带去向 + 地块 + 产品 + 记录人）
        Long productId = resolveProductIdByCrop(bo.getCropId());
        PlantActivity activity = new PlantActivity();
        activity.setCropId(bo.getCropId());
        activity.setActivityDate(bo.getActivityDate());
        activity.setDailyWeight(weight);
        activity.setPickWeight(weight);
        activity.setPickDest(dest);
        activity.setProductId(productId);
        // 销售去向 plot_id 留空（结算时分摊）；非销售记所选地块
        activity.setPlotId(sale ? null : bo.getPlotId());
        activity.setRecorderId(bo.getRecorderId());
        baseMapper.insert(activity);

        // 1.1 row129 绩效班组多选：落 junction t_plant_activity_team。
        //     teamIds 为 null（旧调用方不带）→ syncActivityTeams 内部跳过；空 list → 清空（新建无历史，等价 no-op）。
        //     绩效归属口径（各组全额 vs 均分）本次不管，此处只做存储；记录展示由 AppletPlantActivityController 走 activityTeamNames。
        teamLinkService.syncActivityTeams(activity.getId(), bo.getTeamIds());

        // 2. 累加已采产量（plant_details.actual_yield）。
        //    - 非销售去向：即时累加进所选地块（= 各地块「原始采摘量」）。
        //    - 销售去向（DENGBO-R21/R24）：**不再整笔堆进单条地块**（旧 accumulateActualYieldByCrop 是 row21 bug）；
        //      只落 t_plant_plant_activity(settle_round=0) 流水，待「录入完成」按地块均分本批次未结算销售量。
        if (!sale) {
            accumulateActualYield(bo.getPlotId(), bo.getCropId(), weight);
        }

        // 3. DENGBO-R24：本次录入勾选「录入完成」→ 结算当前批次销售量分摊（前置：本批次全部地块采摘完成）。
        if (bo.getFinishFlag() != null && bo.getFinishFlag() == 1) {
            settlePickActivity(bo.getCropId());
        }
        return activity.getId();
    }

    /**
     * DENGBO-R24「录入完成」结算：把当前批次未结算销售量按地块均分到当前批次全部已采摘完成地块的
     * {@code actual_yield}（S/N 截 3 位小数 DOWN，尾差 S−base×(N−1) 进最后一块），并把本批次销售流水
     * 与参与地块标记 {@code settle_round=N}。
     *
     * <p>批次口径：当前批次 = 该作物 {@code is_pick=1}、种植完成、采摘窗口与当月相交、{@code pick_settle_round=0}
     * 的地块 + {@code pick_dest=sale}、{@code settle_round=0} 的销售流水。已结算(round&gt;0)不再参与；
     * 「录入完成后新增地块」及其新增销售流水 round=0，归入下一批次单独结算（row24 场景②）。</p>
     *
     * <p>前置门（row24 规则1）：当前批次全部地块必须 {@code harvest_status='completed'}，否则拒绝。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settlePickActivity(Long cropId) {
        if (cropId == null) {
            throw new ServiceException("作物 id 不能为空");
        }
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay = today.with(TemporalAdjusters.lastDayOfMonth());
        // 当前批次地块（未结算）：与采摘活动头卡可见集同口径 + pick_settle_round=0；按 id 升序，最后一块 = 最新地块承接尾差。
        List<PlantDetails> plots = plantDetailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getCropId, cropId)
                .eq(PlantDetails::getIsPick, 1)
                .eq(PlantDetails::getPlantStatus, PLANT_STATUS_COMPLETED)
                .le(PlantDetails::getEarliestHarvestdate, lastDay)
                .ge(PlantDetails::getLastHarvestdate, firstDay)
                .and(w -> w.eq(PlantDetails::getPickSettleRound, 0).or().isNull(PlantDetails::getPickSettleRound))
                .orderByAsc(PlantDetails::getId));
        if (plots.isEmpty()) {
            throw new ServiceException("本批次没有可结算的地块（请确认地块已设为采摘活动且种植完成）");
        }
        boolean allDone = plots.stream().allMatch(p -> PICK_STATUS_COMPLETED.equals(p.getHarvestStatus()));
        if (!allDone) {
            throw new ServiceException("请先完成本批次全部地块的采摘，再点「录入完成」");
        }
        // 当前批次未结算销售流水合计
        List<PlantActivity> sales = baseMapper.selectList(
            new LambdaQueryWrapper<PlantActivity>()
                .eq(PlantActivity::getCropId, cropId)
                .eq(PlantActivity::getPickDest, PICK_DEST_SALE)
                .and(w -> w.eq(PlantActivity::getSettleRound, 0).or().isNull(PlantActivity::getSettleRound)));
        BigDecimal totalSale = sales.stream()
            .map(a -> a.getPickWeight() == null ? BigDecimal.ZERO : a.getPickWeight())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int round = nextSettleRound(cropId);
        Long userId = LoginHelper.getUserId();
        int n = plots.size();
        // 均分：base = S/N 截 3 位（DOWN），前 N-1 块 += base，最后一块 += S − base×(N−1)（尾差进最后块）。
        BigDecimal base = totalSale.signum() > 0
            ? totalSale.divide(BigDecimal.valueOf(n), 3, RoundingMode.DOWN) : BigDecimal.ZERO;
        BigDecimal lastShare = totalSale.subtract(base.multiply(BigDecimal.valueOf(n - 1L)));
        for (int i = 0; i < n; i++) {
            PlantDetails p = plots.get(i);
            BigDecimal share = (i == n - 1) ? lastShare : base;
            LambdaUpdateWrapper<PlantDetails> upd = new LambdaUpdateWrapper<PlantDetails>()
                .eq(PlantDetails::getId, p.getId())
                .set(PlantDetails::getPickSettleRound, round)
                .set(PlantDetails::getUpdateBy, userId);
            if (share.signum() > 0) {
                BigDecimal current = p.getActualYield() == null ? BigDecimal.ZERO : p.getActualYield();
                upd.set(PlantDetails::getActualYield, current.add(share));
            }
            plantDetailsMapper.update(null, upd);
        }
        // 标记本批次销售流水已结算（round=N）
        if (!sales.isEmpty()) {
            baseMapper.update(null,
                new LambdaUpdateWrapper<PlantActivity>()
                    .eq(PlantActivity::getCropId, cropId)
                    .eq(PlantActivity::getPickDest, PICK_DEST_SALE)
                    .and(w -> w.eq(PlantActivity::getSettleRound, 0).or().isNull(PlantActivity::getSettleRound))
                    .set(PlantActivity::getSettleRound, round));
        }
    }

    /** 该作物下一结算轮次 = 已结算地块最大 round + 1（首次结算 → 1）。 */
    private int nextSettleRound(Long cropId) {
        Integer maxRound = plantDetailsMapper.selectList(
                new LambdaQueryWrapper<PlantDetails>()
                    .select(PlantDetails::getPickSettleRound)
                    .eq(PlantDetails::getCropId, cropId)
                    .eq(PlantDetails::getIsPick, 1))
            .stream().map(p -> p.getPickSettleRound() == null ? 0 : p.getPickSettleRound())
            .max(Integer::compareTo).orElse(0);
        return maxRound + 1;
    }

    /**
     * 累加所选地块产量（{@code plant_details.actual_yield += weight}）。
     *
     * <p>按 (plotId, cropId) 定位，优先未结束种植（{@code end_actualdate IS NULL}）最新一行，
     * 无则取最新一行。无匹配明细 → warn 跳过，不阻断录入。</p>
     */
    private void accumulateActualYield(Long plotId, Long cropId, BigDecimal weight) {
        if (plotId == null || cropId == null || weight == null || weight.signum() <= 0) {
            return;
        }
        PlantDetails target = locatePlantDetails(plotId, cropId);
        if (target == null) {
            log.warn("recordPickActivity: 未找到 plant_details plotId={} cropId={}，跳过 actual_yield 累加", plotId, cropId);
            return;
        }
        BigDecimal current = target.getActualYield() == null ? BigDecimal.ZERO : target.getActualYield();
        plantDetailsMapper.update(null,
            new LambdaUpdateWrapper<PlantDetails>()
                .eq(PlantDetails::getId, target.getId())
                .set(PlantDetails::getActualYield, current.add(weight))
                .set(PlantDetails::getUpdateBy, LoginHelper.getUserId()));
    }

    /**
     * 按 (plotId, cropId) 定位 plant_details：优先未结束种植最新一行，无则取最新一行。
     */
    private PlantDetails locatePlantDetails(Long plotId, Long cropId) {
        PlantDetails target = plantDetailsMapper.selectOne(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlotId, plotId)
                .eq(PlantDetails::getCropId, cropId)
                .isNull(PlantDetails::getEndActualdate)
                .orderByDesc(PlantDetails::getId)
                .last("LIMIT 1"));
        if (target == null) {
            target = plantDetailsMapper.selectOne(
                new LambdaQueryWrapper<PlantDetails>()
                    .eq(PlantDetails::getPlotId, plotId)
                    .eq(PlantDetails::getCropId, cropId)
                    .orderByDesc(PlantDetails::getId)
                    .last("LIMIT 1"));
        }
        return target;
    }

    /**
     * 按作物 {@code crop.related_product} 解析果蔬成品 product_id（未配置返 null，不阻断录入）。
     */
    private Long resolveProductIdByCrop(Long cropId) {
        if (cropId == null) {
            return null;
        }
        CropInfo crop = cropInfoMapper.selectById(cropId);
        if (crop == null || crop.getRelatedProduct() == null) {
            log.warn("作物 related_product 未配置，product_id 留空 — cropId={}", cropId);
            return null;
        }
        return crop.getRelatedProduct();
    }

    @Override
    public List<PlantActivity> listRecords(Long cropId, LocalDate begin, LocalDate end) {
        // cropId 可空 = 全场查询（不按作物过滤）；条件化拼接
        LambdaQueryWrapper<PlantActivity> lqw = new LambdaQueryWrapper<PlantActivity>()
            .eq(cropId != null, PlantActivity::getCropId, cropId)
            .ge(begin != null, PlantActivity::getActivityDate, begin)
            .le(end != null, PlantActivity::getActivityDate, end)
            .orderByDesc(PlantActivity::getActivityDate)
            .orderByDesc(PlantActivity::getId);
        return baseMapper.selectList(lqw);
    }
}
