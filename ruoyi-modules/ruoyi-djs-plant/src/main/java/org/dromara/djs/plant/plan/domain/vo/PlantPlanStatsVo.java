package org.dromara.djs.plant.plan.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 种植计划列表顶部 5 KPI 统计卡 VO（FIX-PLT-AD-PLAN-001）。
 *
 * <p>对齐原型 {@code 7b44cd24} 列表上方一排 5 张卡：</p>
 * <ul>
 *   <li>{@code idlePlot}：空地块数 = COUNT(plot_info: plot_status=1 空闲)</li>
 *   <li>{@code plantedPlot}：已种植地块数 = COUNT(plot_info: plot_status IN (2,3) 种植/采摘)</li>
 *   <li>{@code plannedPlot}：计划种植地块数 = COUNT(DISTINCT details.plot_id)（进行中计划，去重）</li>
 *   <li>{@code plotUsageFreq}：计划地块使用频次 = 计划明细行数 / 去重地块数（保留 1 位小数）</li>
 *   <li>{@code cropVarietyCount}：计划种植作物品种数 = COUNT(DISTINCT plan.crop_id)</li>
 * </ul>
 *
 * @author djs
 * @since FIX-PLT-AD-PLAN-001
 */
@Data
public class PlantPlanStatsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 空地块数（plot_status=1 空闲）。 */
    private Integer idlePlot;

    /** 已种植地块数（plot_status IN (2,3)）。 */
    private Integer plantedPlot;

    /** 计划种植地块数（去重 details.plot_id，仅 pending/ongoing 计划）。 */
    private Integer plannedPlot;

    /** 计划地块使用频次（明细行数 / 去重地块数，1 位小数）。 */
    private BigDecimal plotUsageFreq;

    /** 计划种植作物品种数（去重 plan.crop_id，仅 pending/ongoing 计划）。 */
    private Integer cropVarietyCount;
}
