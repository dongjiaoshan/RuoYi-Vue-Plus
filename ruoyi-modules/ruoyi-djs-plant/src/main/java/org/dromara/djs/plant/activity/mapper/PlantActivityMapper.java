package org.dromara.djs.plant.activity.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.activity.domain.PlantActivity;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 采摘活动 Mapper（FIX-PLT-HARVEST-ACTIVITY-001）。
 *
 * <p>主体走 {@link BaseMapperPlus} 通用 CRUD；
 * 列表 {@code cropName} / {@code teamName} 由 service 层批量 enrich。</p>
 *
 * @author djs
 * @since FIX-PLT-HARVEST-ACTIVITY-001
 */
public interface PlantActivityMapper extends BaseMapperPlus<PlantActivity, PlantActivity> {

    /**
     * 批量按 (crop_id, activity_date) 聚合采摘活动重量（235 采摘活动管理「已采重量」取数）。
     *
     * <p>一次 {@code IN(cropIds)} + 全局日期窗口 {@code [minDate, maxDate]} + {@code GROUP BY crop_id, activity_date}，
     * 避免 N+1。各作物各自的采摘窗口（[earliestHarvestdate, lastHarvestdate]）粒度在 service 端按 (cropId, activityDate)
     * 行过滤累加（各作物窗口不同，故按日粒度返回让 service 精确聚合）。显式 {@code tenant_id='1001'} + {@code del_flag='0'}
     * （V1 单农场，关租户行注入）。</p>
     *
     * @param cropIds 作物 id 集合（空集时调用方需自行短路，勿传空）
     * @param minDate 全局窗口下界（所有作物窗口并集的最小起始日，缩小扫描）
     * @param maxDate 全局窗口上界（所有作物窗口并集的最大结束日）
     * @return 每行 {@code {cropId, activityDate, daySum}}
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        <script>
        SELECT crop_id AS cropId, activity_date AS activityDate, COALESCE(SUM(daily_weight), 0) AS daySum
          FROM t_plant_plant_activity
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND activity_date BETWEEN #{minDate} AND #{maxDate}
           AND crop_id IN
           <foreach collection="cropIds" item="cid" open="(" separator="," close=")">#{cid}</foreach>
         GROUP BY crop_id, activity_date
        </script>
        """)
    List<Map<String, Object>> selectDailyWeightByCropInRange(@Param("cropIds") Collection<Long> cropIds,
                                                             @Param("minDate") LocalDate minDate,
                                                             @Param("maxDate") LocalDate maxDate);

    /**
     * 采摘量合计（kg）：activity_date 落在 {@code [startDate, endDate]} 区间内的 daily_weight 全量求和
     * （mp 采收概览 KPI「当月采摘量」取数，FIX-PLT-MP-PICK-SUMMARY-001）。
     *
     * <p>不限作物，口径与采摘活动管理「已采重量」一致（取 {@code t_plant_plant_activity.daily_weight}）。
     * 显式 {@code tenant_id='1001'} + {@code del_flag='0'}（V1 单农场，关租户行注入）。</p>
     *
     * @param startDate 区间下界（含），如当月第一天
     * @param endDate   区间上界（含），如当月最后一天
     * @return 区间内 daily_weight 合计（无记录返 0）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT COALESCE(SUM(daily_weight), 0)
          FROM t_plant_plant_activity
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND activity_date BETWEEN #{startDate} AND #{endDate}
        """)
    java.math.BigDecimal selectTotalWeightInRange(@Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    /**
     * 作物未结算销售量合计（kg，row176「已采产量」补计销售去向）。
     *
     * <p>销售去向（{@code pick_dest='sale'}）录入时 {@code plot_id} 留空、只落 per-event 流水
     * （{@code settle_round=0}），不即时累加进 {@code plant_details.actual_yield}，待「录入完成」
     * {@code settlePickActivity} 才按地块均分。故「已采产量 = Σactual_yield」会漏掉这批未结算销售量，
     * 本方法求和补回该差额（{@code pick_dest='sale' AND (settle_round=0 OR settle_round IS NULL)}）。
     * 只有 {@code is_pick=1} 采摘活动会录销售去向，{@code is_pick=2} 采收无 → 合计 0（加法 no-op，采收页安全）。
     * 显式 {@code tenant_id='1001'} + {@code del_flag='0'}（V1 单农场，关租户行注入）。</p>
     *
     * @param cropId 作物 id（非空）
     * @return 未结算销售量合计（无记录返 0）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT COALESCE(SUM(pick_weight), 0)
          FROM t_plant_plant_activity
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND crop_id = #{cropId}
           AND pick_dest = 'sale'
           AND (settle_round = 0 OR settle_round IS NULL)
        """)
    java.math.BigDecimal sumUnsettledSaleWeight(@Param("cropId") Long cropId);
}
