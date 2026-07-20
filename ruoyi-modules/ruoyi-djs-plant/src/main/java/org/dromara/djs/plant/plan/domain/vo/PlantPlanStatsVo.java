package org.dromara.djs.plant.plan.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 种植计划列表顶部 5 KPI 统计卡 VO（FIX-PLT-AD-PLAN-001）。
 *
 * <p>对齐原型 {@code 7b44cd24} 列表上方一排 5 张卡（row37，按计划年份过滤，默认当年）：</p>
 * <ul>
 *   <li>{@code idlePlot}：当前空地块数 = COUNT(plot_info: plot_status=1 空闲)（当前状态，不随年份变）</li>
 *   <li>{@code plantedPlot}：当年已种植地块数 = 当年种植行为次数之和（COUNT(*) 非去重，
 *       当年计划下 begin_actualdate IS NOT NULL 的明细行数）</li>
 *   <li>{@code plannedPlot}：当年计划种植地块数 = Σ计划地块（COUNT(*) 当年计划明细行数，非去重）</li>
 *   <li>{@code plotUsageFreq}：当年计划地块使用频次 = 当年计划种植地块数 / 地块总数（保留 2 位小数）</li>
 *   <li>{@code cropVarietyCount}：当年计划种植作物品种数 = COUNT(DISTINCT plan.crop_id)（当年计划）</li>
 * </ul>
 *
 * @author djs
 * @since FIX-PLT-AD-PLAN-001
 */
@Data
public class PlantPlanStatsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前空地块数（plot_status=1 空闲，当前状态）。 */
    private Integer idlePlot;

    /** 当年已种植地块数（当年计划下 begin_actualdate IS NOT NULL 的明细行数，COUNT(*) 非去重）。 */
    private Integer plantedPlot;

    /** 当年计划种植地块数（Σ计划地块 = 当年计划明细行数 COUNT(*)，非去重）。 */
    private Integer plannedPlot;

    /** 当年计划地块使用频次（当年计划种植地块数 / 地块总数，2 位小数）。 */
    private BigDecimal plotUsageFreq;

    /** 当年计划种植作物品种数（去重 plan.crop_id，当年计划）。 */
    private Integer cropVarietyCount;
}
