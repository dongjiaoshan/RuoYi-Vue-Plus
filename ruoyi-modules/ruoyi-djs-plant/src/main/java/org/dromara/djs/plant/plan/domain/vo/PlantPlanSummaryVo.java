package org.dromara.djs.plant.plan.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 种植计划聚合摘要 VO（跨模块只读）。
 *
 * <p>给非种植域（如 {@code ruoyi-djs-warehouse} 需求确认 SummaryBar）使用，按"进行中"计划聚合 4 指标：</p>
 * <ul>
 *   <li>{@code plotCount}：去重地块数（COUNT DISTINCT details.plot_id）</li>
 *   <li>{@code expectedYieldKg}：未完成预计产量 SUM(expected_yield) - SUM(actual_yield)</li>
 *   <li>{@code earliestPickDate}：最早采摘日 MIN(plan.earliest_harvestdate)</li>
 *   <li>{@code currentStockKg}：当前蔬菜成品库存（warehouse 端补充，本 VO 不计）</li>
 * </ul>
 *
 * <p>"进行中"语义：{@code plan.plant_status IN ('pending','ongoing')}，已 completed/delayed 不计。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-003
 */
@Data
public class PlantPlanSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 进行中计划的去重地块数。 */
    private Integer plotCount;

    /** 进行中计划预计待产量（kg），已实采的会从中扣除。 */
    private BigDecimal expectedYieldKg;

    /** 进行中计划中最早的采摘日期。 */
    private LocalDate earliestPickDate;
}
