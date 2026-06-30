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

    /** 采摘去向「销售」value（djs_pick_dest）：不选地块、不在录入时分摊产量。 */
    private static final String PICK_DEST_SALE = "sale";

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

        // 2. 非销售去向：当场把本次重量累加进所选地块产量（plant_details.actual_yield）
        //    销售去向：不在录入时分摊（plot_id 为空，结算时由地块完成平均分摊到活动地块）
        if (!sale) {
            accumulateActualYield(bo.getPlotId(), bo.getCropId(), weight);
        }
        return activity.getId();
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
