package org.dromara.djs.plant.activity.service;

import org.dromara.djs.plant.activity.domain.PlantActivity;
import org.dromara.djs.plant.activity.domain.bo.PickActivityRecordBo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采摘活动 Service（FIX-PLT-HARVEST-ACTIVITY-001 + DENGBO-R4 采摘去向）。
 *
 * <p>采摘重量流水读写基建：</p>
 * <ul>
 *   <li>{@link #recordDailyWeight}：旧 mp 采摘重量录入入口（DENGBO-R4 起改 per-event INSERT）</li>
 *   <li>{@link #recordPickActivity}：采摘去向录入入口（DENGBO-R4 决策 A，per-event + 去向 + 产量分摊）</li>
 *   <li>{@link #listRecords}：mp 采摘活动记录列表（倒序）</li>
 * </ul>
 *
 * @author djs
 * @since FIX-PLT-HARVEST-ACTIVITY-001
 */
public interface IPlantActivityService {

    /**
     * 记录当次采摘重量（DENGBO-R4 起 per-event INSERT，不再幂等累加）。
     *
     * <p>原 UNIQUE(crop,date,班组) 已 DROP，每次 INSERT 一行 {@code t_plant_plant_activity}
     * （pick_dest 留 NULL = 历史销售口径）。供 {@code FarmRecordsServiceImpl.submitHarvestWeight}
     * 旧采摘重量录入端点调用，接口签名稳定。</p>
     *
     * @param cropId       作物 id（{@code t_plant_crop_info.id}，非空）
     * @param activityDate 采摘日期（非空）
     * @param dailyWeight  本次采摘重量 kg（非空，> 0）
     * @param activityBy   记录班组 id（{@code t_plant_work_team.id}，可空）
     */
    void recordDailyWeight(Long cropId, LocalDate activityDate, BigDecimal dailyWeight, Long activityBy);

    /**
     * 采摘去向录入（DENGBO-R4 决策 A 主入口）。
     *
     * <p>同事务：</p>
     * <ol>
     *   <li>INSERT 一行 {@code t_plant_plant_activity}（per-event，携带去向 + 地块 + 产品 + 记录人）</li>
     *   <li>非销售去向：累加所选地块 {@code plant_details.actual_yield += pickWeight}</li>
     *   <li>销售去向：不在此分摊（plot_id 为空），结算时平均分摊到活动地块（由地块完成结算触发）</li>
     * </ol>
     *
     * <p>跨模块仓库入账（入库/月台/损耗/饲喂）由编排方（mp 采摘去向端点，在 warehouse 模块）
     * 调用 {@code IVegetableHandleService.recordPickDestination} 完成 —— plant 不反向依赖 warehouse。</p>
     *
     * @param bo 采摘去向录入入参
     * @return 新 activity 行 id
     */
    Long recordPickActivity(PickActivityRecordBo bo);

    /**
     * DENGBO-R24「录入完成」结算：把当前批次未结算销售量按地块均分到当前批次全部已采摘完成地块的
     * actual_yield（3 位小数、尾差进最后一块），并把本批次销售流水与地块标记 settle_round=N。
     * 前置：当前批次全部地块须采摘完成，否则抛异常。
     *
     * @param cropId 作物 id
     */
    void settlePickActivity(Long cropId);

    /**
     * 作物未结算销售量合计（kg，row176「已采产量」补计销售去向）。
     *
     * <p>销售去向录入时暂存流水（{@code plot_id} 空、{@code settle_round=0}），不即时累加进
     * {@code plant_details.actual_yield}，故「已采产量 = Σactual_yield」会漏；本方法补回
     * {@code pick_dest='sale' AND (settle_round=0 OR settle_round IS NULL)} 的 pick_weight 合计。
     * 只有 {@code is_pick=1} 采摘活动有销售去向，{@code is_pick=2} 采收 → 恒 0（加法 no-op）。</p>
     *
     * @param cropId 作物 id（空 → 返 0，不抛）
     * @return 未结算销售量合计（无记录 / cropId 为空返 0）
     */
    BigDecimal sumUnsettledSaleWeight(Long cropId);

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
