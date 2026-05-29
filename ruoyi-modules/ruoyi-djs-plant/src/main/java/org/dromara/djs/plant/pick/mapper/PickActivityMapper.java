package org.dromara.djs.plant.pick.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.pick.domain.PickActivity;
import org.dromara.djs.plant.pick.domain.vo.PickActivityVo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采摘活动 Mapper（PLT-PLAN-002）。
 *
 * @author djs
 * @since PLT-PLAN-002
 */
public interface PickActivityMapper extends BaseMapperPlus<PickActivity, PickActivityVo> {

    /**
     * 取指定年份指定序号前缀下的最大序号（用于 activity_no 序号生成）。
     *
     * <p>例：{@code PA20260520NNN} —— prefix='PA20260520'，
     * 查 {@code MAX(CAST(SUBSTRING(activity_no, 11, 3) AS UNSIGNED))}。</p>
     *
     * @param tenantId 租户编号
     * @param prefix   PA + yyyyMMdd（10 字符）
     * @return 最大序号；无记录时返回 0
     */
    @Select("""
        SELECT COALESCE(MAX(CAST(SUBSTRING(activity_no, 11, 3) AS UNSIGNED)), 0)
          FROM t_plant_pick_activity
         WHERE tenant_id = #{tenantId}
           AND activity_no LIKE CONCAT(#{prefix}, '%')
        """)
    Integer selectMaxSeqByPrefix(@Param("tenantId") String tenantId, @Param("prefix") String prefix);

    /**
     * 活动结束后聚合当日 plant_details.actual_yield 总和（同 cropId + activity_date 当天）。
     *
     * @param cropId       作物 id
     * @param activityDate 活动日期
     * @return SUM(actual_yield)；无数据返回 0
     */
    @Select("""
        SELECT COALESCE(SUM(actual_yield), 0)
          FROM t_plant_plant_details
         WHERE crop_id = #{cropId}
           AND end_harvestdate = #{activityDate}
           AND is_pick = 1
           AND del_flag = '0'
        """)
    BigDecimal sumYieldForActivity(@Param("cropId") Long cropId, @Param("activityDate") LocalDate activityDate);
}
