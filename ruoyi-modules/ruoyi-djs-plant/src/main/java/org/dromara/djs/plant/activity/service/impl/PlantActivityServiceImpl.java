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
import java.time.LocalDate;
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

    public PlantActivityServiceImpl(PlantActivityMapper baseMapper,
                                    CropInfoMapper cropInfoMapper,
                                    PlantDetailsMapper plantDetailsMapper) {
        super(baseMapper);
        this.cropInfoMapper = cropInfoMapper;
        this.plantDetailsMapper = plantDetailsMapper;
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
        if (bo.getActivityDate() == null) {
            throw new ServiceException("采摘日期不能为空");
        }
        BigDecimal weight = bo.getPickWeight();
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

        // 2. 累加已采产量（plant_details.actual_yield），保证头卡「已采产量」即时出数（row162）。
        //    - 非销售去向：累加进所选地块（plotId + cropId 定位）。
        //    - 销售去向：无地块，按【作物维度】定位该作物下一条明细累加，不依赖 plot_id
        //      （客户口径：销售录入后已采产量必须反映本次重量；否则 plot_id 空 + 不分摊 → 头卡恒 0）。
        if (!sale) {
            accumulateActualYield(bo.getPlotId(), bo.getCropId(), weight);
        } else {
            accumulateActualYieldByCrop(bo.getCropId(), weight);
        }
        return activity.getId();
    }

    /**
     * 销售去向（无地块）按作物维度累加已采产量。
     *
     * <p>定位该作物下【采摘窗口与当月相交、种植完成】明细，优先未采摘完成（harvest_status != completed）
     * 的最新一行累加 {@code actual_yield}；无「未完成」候选时退而取最新一行。目标集与采摘活动头卡可见集
     * （listCropPlots：plant_status='completed' + 采摘窗口与当月相交）对齐，使累加后头卡 Σactual_yield 立即出数。
     * 无匹配明细 → warn 跳过，不阻断录入。</p>
     */
    private void accumulateActualYieldByCrop(Long cropId, BigDecimal weight) {
        if (cropId == null || weight == null || weight.signum() <= 0) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay = today.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        // 该作物下、种植完成、采摘窗口与当月相交的明细（与头卡可见集同口径）
        List<PlantDetails> candidates = plantDetailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getCropId, cropId)
                .eq(PlantDetails::getPlantStatus, PLANT_STATUS_COMPLETED)
                .le(PlantDetails::getEarliestHarvestdate, lastDay)
                .ge(PlantDetails::getLastHarvestdate, firstDay)
                .orderByDesc(PlantDetails::getId));
        if (candidates.isEmpty()) {
            log.warn("recordPickActivity[销售]: 未找到当月可采摘 plant_details cropId={}，跳过 actual_yield 累加", cropId);
            return;
        }
        // 采摘活动头卡（source≠plant 的采摘活动入口）只看 is_pick=1 明细，优先落 is_pick=1 候选保证头卡出数；
        // 无 is_pick=1 候选（如从果蔬处理入口录入的普通作物）则回退全体候选。同类候选内优先未采摘完成，其次最新一行。
        PlantDetails target = pickAccumulateTarget(candidates.stream()
            .filter(d -> d.getIsPick() != null && d.getIsPick() == 1)
            .toList());
        if (target == null) {
            target = pickAccumulateTarget(candidates);
        }
        if (target == null) {
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
     * 从候选明细中挑累加目标：优先未采摘完成（harvest_status != completed），其次列表首元素（调用方已按 id 降序，即最新一行）。
     * 空列表返 null。
     */
    private PlantDetails pickAccumulateTarget(List<PlantDetails> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
            .filter(d -> !PICK_STATUS_COMPLETED.equals(d.getHarvestStatus()))
            .findFirst()
            .orElse(candidates.get(0));
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
