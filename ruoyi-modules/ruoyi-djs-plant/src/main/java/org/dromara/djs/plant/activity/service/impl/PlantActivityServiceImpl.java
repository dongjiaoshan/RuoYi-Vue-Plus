package org.dromara.djs.plant.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.plant.activity.domain.PlantActivity;
import org.dromara.djs.plant.activity.mapper.PlantActivityMapper;
import org.dromara.djs.plant.activity.service.IPlantActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采摘活动 Service 实现（FIX-PLT-HARVEST-ACTIVITY-001）。
 *
 * @author djs
 * @since FIX-PLT-HARVEST-ACTIVITY-001
 */
@Slf4j
@Service
public class PlantActivityServiceImpl extends DjsBaseServiceImpl<PlantActivityMapper, PlantActivity>
    implements IPlantActivityService {

    public PlantActivityServiceImpl(PlantActivityMapper baseMapper) {
        super(baseMapper);
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

        // 按 UNIQUE(tenant_id, crop_id, activity_date, activity_by) 定位活动行（tenant 由 MP 拦截器注入）
        LambdaQueryWrapper<PlantActivity> lqw = new LambdaQueryWrapper<PlantActivity>()
            .eq(PlantActivity::getCropId, cropId)
            .eq(PlantActivity::getActivityDate, activityDate);
        if (activityBy == null) {
            lqw.isNull(PlantActivity::getActivityBy);
        } else {
            lqw.eq(PlantActivity::getActivityBy, activityBy);
        }
        PlantActivity existing = baseMapper.selectOne(lqw);

        if (existing == null) {
            // 新流水：INSERT 不显式赋 tenant_id（InjectionMetaObjectHandler 自动 fill）
            PlantActivity activity = new PlantActivity();
            activity.setCropId(cropId);
            activity.setActivityDate(activityDate);
            activity.setDailyWeight(dailyWeight);
            activity.setActivityBy(activityBy);
            baseMapper.insert(activity);
        } else {
            // 同 crop+date+班组已存在：累加 daily_weight
            BigDecimal base = existing.getDailyWeight() == null ? BigDecimal.ZERO : existing.getDailyWeight();
            existing.setDailyWeight(base.add(dailyWeight));
            baseMapper.updateById(existing);
        }
    }

    @Override
    public List<PlantActivity> listRecords(Long cropId, LocalDate begin, LocalDate end) {
        if (cropId == null) {
            return List.of();
        }
        LambdaQueryWrapper<PlantActivity> lqw = new LambdaQueryWrapper<PlantActivity>()
            .eq(PlantActivity::getCropId, cropId)
            .ge(begin != null, PlantActivity::getActivityDate, begin)
            .le(end != null, PlantActivity::getActivityDate, end)
            .orderByDesc(PlantActivity::getActivityDate)
            .orderByDesc(PlantActivity::getId);
        return baseMapper.selectList(lqw);
    }
}
