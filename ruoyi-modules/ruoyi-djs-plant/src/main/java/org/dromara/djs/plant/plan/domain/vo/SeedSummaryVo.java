package org.dromara.djs.plant.plan.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 播种首页 3 KPI VO（FIX-PLT-MP-SEED-001，#6.5）。
 *
 * <p>原型播种首页「种植任务」tab 顶部 3 KPI：当月完成率 / 当日种植品种数 / 当日种植地块数。
 * 取代此前借用 {@code getPickSummary} 的采摘口径（品种数 / 采摘 kg）。</p>
 *
 * <ul>
 *   <li>{@code monthCompletionRate}：当月明细 已开工(begin_actualdate 非空) / 当月明细总数 × 100（整数百分比）</li>
 *   <li>{@code todayCropKindCount}：{@code begin_actualdate=今日} 的明细中 distinct crop_id 数</li>
 *   <li>{@code todayPlotCount}：{@code begin_actualdate=今日} 的明细中 distinct plot_id 数</li>
 * </ul>
 *
 * @author djs
 * @since FIX-PLT-MP-SEED-001
 */
@Data
public class SeedSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当月完成率（整数百分比，0-100；当月无明细返 0）。 */
    private Integer monthCompletionRate;

    /** 当日种植品种数（今日开工明细 distinct crop_id）。 */
    private Integer todayCropKindCount;

    /** 当日种植地块数（今日开工明细 distinct plot_id）。 */
    private Integer todayPlotCount;
}
