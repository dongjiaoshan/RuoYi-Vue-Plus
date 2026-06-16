package org.dromara.djs.plant.activity.service;

import org.dromara.djs.plant.activity.domain.PlantActivity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采摘活动 Service（FIX-PLT-HARVEST-ACTIVITY-001）。
 *
 * <p>采摘重量流水读写基建：</p>
 * <ul>
 *   <li>{@link #recordDailyWeight}：mp 采摘重量录入入口，按 UNIQUE 幂等累加
 *       （被 {@code FarmRecordsServiceImpl.submitHarvestWeight} 调用，签名稳定）</li>
 *   <li>{@link #listRecords}：mp 采摘活动记录列表（倒序）</li>
 * </ul>
 *
 * @author djs
 * @since FIX-PLT-HARVEST-ACTIVITY-001
 */
public interface IPlantActivityService {

    /**
     * 记录当日采摘重量（幂等累加）。
     *
     * <p>按 UNIQUE(tenant_id, crop_id, activity_date, activity_by) 定位：
     * 已存在同作物 + 同日 + 同班组的活动行则在原 {@code daily_weight} 上累加，
     * 否则 INSERT 一行新流水。供 {@code FarmRecordsServiceImpl.submitHarvestWeight} 调用，
     * 接口签名稳定。</p>
     *
     * @param cropId       作物 id（{@code t_plant_crop_info.id}，非空）
     * @param activityDate 采摘日期（非空）
     * @param dailyWeight  本次采摘重量 kg（非空，> 0）
     * @param activityBy   记录班组 id（{@code t_plant_work_team.id}，可空）
     */
    void recordDailyWeight(Long cropId, LocalDate activityDate, BigDecimal dailyWeight, Long activityBy);

    /**
     * 采摘活动记录列表（mp 采摘活动记录，按采摘日期倒序）。
     *
     * @param cropId 作物 id（可空 = 全场查询，不按作物过滤；非空则按作物过滤）
     * @param begin  起始采摘日期（含，可空 = 不限下界）
     * @param end    结束采摘日期（含，可空 = 不限上界）
     * @return 采摘活动流水列表（无数据返空列表，按 {@code activity_date} 倒序）
     */
    List<PlantActivity> listRecords(Long cropId, LocalDate begin, LocalDate end);
}
