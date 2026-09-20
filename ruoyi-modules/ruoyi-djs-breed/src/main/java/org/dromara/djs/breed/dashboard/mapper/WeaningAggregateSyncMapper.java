package org.dromara.djs.breed.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 断奶记录汇总列回算（甲方 V6 需求和问题（测试环境） 行239 第 2 点，窗口口径 D-0102）。
 *
 * <p>{@code t_farm_pig_weaning} 的 {@code weaned_count / weaned_weight / avg_weaned_weight} 三列
 * 是逐头明细 {@code t_farm_pig_weaning_detail} 的冗余汇总。断奶从「整窝一起断」改成「按所选仔猪断」
 * （行238）之后，同一窝可能分几次断、事后也可能改明细，冗余列会跟明细脱节 —— 每晚回算一次拉齐。</p>
 *
 * <p><b>放在 dashboard 而不是 weaning 模块</b>：这是统计链路为了让自己的上游数据可信而做的维护动作，
 * 触发点、窗口、频率都由 {@code DashboardAggregateJob} 决定，跟着统计任务走才不会漏跑。
 * 断奶业务本身的写入路径不调它。</p>
 *
 * @author djs
 * @since BRD-STAT-NPD-001
 */
@Mapper
public interface WeaningAggregateSyncMapper {

    /**
     * 按断奶日期窗口回算断奶记录的三个汇总列。
     *
     * <p><b>只动「有明细的」记录</b>（{@code EXISTS} 守卫）：历史上存在只录了汇总、没有逐头明细的
     * 断奶记录，不加这道守卫会把它们的头数与重量一把清零 —— 那是删数据，不是同步。
     * 甲方原文「如果母猪ID存在，则按日统计」说的正是这个条件。</p>
     *
     * <p>{@code avg_weaned_weight} 由本次算出的总重除头数得到，不读旧值；头数为 0 的行进不来
     * （{@code EXISTS} 已经挡掉），所以不会除零。</p>
     *
     * @param tenantId 租户
     * @param from     断奶日期窗口起（含）
     * @param to       断奶日期窗口止（不含）
     * @return 实际更新行数
     */
    @Update("UPDATE t_farm_pig_weaning w "
        + "   SET w.weaned_count = ("
        + "         SELECT COUNT(*) FROM t_farm_pig_weaning_detail d "
        + "          WHERE d.weaning_id = w.id AND d.del_flag = '0'), "
        + "       w.weaned_weight = ("
        + "         SELECT COALESCE(SUM(d.weight),0) FROM t_farm_pig_weaning_detail d "
        + "          WHERE d.weaning_id = w.id AND d.del_flag = '0'), "
        + "       w.avg_weaned_weight = ("
        + "         SELECT COALESCE(SUM(d.weight),0) / COUNT(*) FROM t_farm_pig_weaning_detail d "
        + "          WHERE d.weaning_id = w.id AND d.del_flag = '0') "
        + " WHERE w.tenant_id = #{tenantId} "
        + "   AND w.del_flag = '0' "
        + "   AND w.weaning_date >= #{from} AND w.weaning_date < #{to} "
        + "   AND EXISTS (SELECT 1 FROM t_farm_pig_weaning_detail d "
        + "                WHERE d.weaning_id = w.id AND d.del_flag = '0')")
    int resyncFromDetail(@Param("tenantId") String tenantId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to);
}
