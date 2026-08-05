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
     * 批量按作物聚合「已录入但还没落到地块」的采摘量（小程序 row260，Kevin 2026-08-01 定：按作物汇总计入已采产量）。
     *
     * <p><b>为什么需要它</b>：销售去向（{@code pick_dest='sale'}）按 DENGBO-R4/R24 设计**故意不即时分摊**——
     * 只落一行 {@code settle_round=0} 且 {@code plot_id} 为空的流水，等整批地块采摘完成后 {@code settlePickActivity}
     * 才均分写进 {@code plant_details.actual_yield}。于是「录了 100kg 但整批没完成」时，
     * 页面 Σ{@code actual_yield} 恒 0，客户看到「录了却不显示」。本方法把这部分未落地的量单独捞出来，
     * 供展示侧与 Σ{@code actual_yield} 相加。</p>
     *
     * <p><b>谓词三条缺一不可</b>（{@code pick_dest='sale' AND plot_id IS NULL AND settle_round∈{0,NULL}}）：</p>
     * <ul>
     *   <li>{@code pick_dest='sale'} —— 只有销售去向才是「录了但还没落地块」。⚠️ 早期版本漏了这条，
     *       会把历史 {@code recordDailyWeight} 路径产生的行（{@code pick_dest} 为 NULL、无地块、
     *       {@code settle_round} 默认 0）也算进来；而它的唯一调用方
     *       {@code FarmRecordsServiceImpl.submitHarvestWeight} 在**同一事务里先** {@code accumulateActualYield}
     *       写了 {@code actual_yield}、**再** INSERT 这行流水 —— 计入即双算，且因 {@code pick_dest} 非
     *       {@code 'sale'}，{@code settlePickActivity} 的结算 UPDATE 永远标不到它，会**永久**卡在双算态。</li>
     *   <li>{@code plot_id IS NULL} —— 非销售去向录入时**当场**就 {@code actual_yield += pickWeight} 并记了地块，
     *       它的 {@code settle_round} 同样是 0；只按 settle_round 过滤会把它再加一遍（双算）。</li>
     *   <li>{@code settle_round∈{0,NULL}} —— 已结算的销售流水会被标成 {@code settle_round=N(>0)}，
     *       此时量已经进了 {@code actual_yield}，再算就是双算（结算只改 settle_round，不回填 plot_id，
     *       故第一条谓词拦不住它）。</li>
     * </ul>
     *
     * <p><b>归属渠道</b>：{@code settlePickActivity} 只把销售量结算进 {@code is_pick=1}（采摘活动）地块，
     * 故这笔未结算量**只属于采摘活动渠道**，消费方必须只在 {@code isPick=1} 视图里并入，
     * 不能算到 {@code is_pick=2}（普通采收）头上。</p>
     *
     * <p>显式 {@code tenant_id='1001'} + {@code del_flag='0'}（V1 单农场，关租户行注入），与本 mapper 其余查询一致。</p>
     *
     * @param cropIds 作物 id 集合（空集时调用方需自行短路，勿传空）
     * @return 每行 {@code {cropId, pendingSum}}；某作物无未结算流水时不返回该行（调用方按缺省 0 处理）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        <script>
        SELECT crop_id AS cropId, COALESCE(SUM(pick_weight), 0) AS pendingSum
          FROM t_plant_plant_activity
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND pick_dest = 'sale'
           AND plot_id IS NULL
           AND (settle_round = 0 OR settle_round IS NULL)
           AND crop_id IN
           <foreach collection="cropIds" item="cid" open="(" separator="," close=")">#{cid}</foreach>
         GROUP BY crop_id
        </script>
        """)
    List<Map<String, Object>> selectUnsettledPickedByCrops(@Param("cropIds") Collection<Long> cropIds);
}
