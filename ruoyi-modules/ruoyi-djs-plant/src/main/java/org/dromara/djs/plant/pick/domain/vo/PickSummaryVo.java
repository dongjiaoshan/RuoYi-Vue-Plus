package org.dromara.djs.plant.pick.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * mp 采收概览 KPI 出参（PLT-PICK-001，按作物聚合）。
 *
 * <p>3 项：采收任务完成率（completed / 全部非游客任务）、今日采摘品种数
 * （{@code COUNT(DISTINCT crop_id)}）、今日采摘重量（今日 harvest_activity 农事记录中
 * 由 plant_details 增量累加；V1 取 plant_details 当日 begin/end actualdate 命中明细的
 * actual_yield 之和，口径见 ServiceImpl 注释）。游客采摘活动（is_pick=1）全程排除。</p>
 *
 * @author djs
 * @since PLT-PICK-001
 */
@Data
public class PickSummaryVo {

    /** 采收任务完成率（0-100 整数百分比）。 */
    private Integer taskCompletionRate;

    /** 今日采摘品种数（distinct 作物）。 */
    private Integer todayCropKindCount;

    /** 今日采摘重量 kg。 */
    private BigDecimal todayWeight;
}
