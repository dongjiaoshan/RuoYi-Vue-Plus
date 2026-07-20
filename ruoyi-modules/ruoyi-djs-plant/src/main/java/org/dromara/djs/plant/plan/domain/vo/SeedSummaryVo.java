package org.dromara.djs.plant.plan.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 播种首页 3 KPI VO（FIX-PLT-MP-SEED-001，#6.5）。
 *
 * <p>原型播种首页「种植任务」tab 顶部 3 KPI：当月完成率 / 当月种植品种数 / 当月完成种植地块数。
 * 取代此前借用 {@code getPickSummary} 的采摘口径（品种数 / 采摘 kg）。</p>
 *
 * <ul>
 *   <li>{@code monthCompletionRate}：当月明细 已完成种植(plant_status='completed') / 当月明细总数 × 100（整数百分比）</li>
 *   <li>{@code todayCropKindCount}（当月种植品种数）：当月 {@code begin_actualdate} 区间内明细的 distinct crop_id 数</li>
 *   <li>{@code todayPlotCount}（当月完成种植地块数）：当月 {@code begin_actualdate} 区间内且
 *       {@code plant_status='completed'} 明细的 distinct plot_id 数</li>
 * </ul>
 *
 * <p>字段名沿用 today* 前缀（前端 ts 已对齐），口径由「当日」改「当月」（测试 r58），实际语义见上。</p>
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

    /** 当月种植品种数（当月 begin_actualdate 区间内明细 distinct crop_id）。 */
    private Integer todayCropKindCount;

    /** 当月完成种植地块数（当月 begin_actualdate 区间内且 plant_status='completed' 明细 distinct plot_id）。 */
    private Integer todayPlotCount;
}
