package org.dromara.djs.plant.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 种植看板汇总 VO（PLT-DASH-001）。
 *
 * <p>一个 {@code /summary} 端点返回三块：</p>
 * <ul>
 *   <li>土地总览：空闲 / 种植中 / 采摘中 / 待种 / 总数 各地块计数 + 总面积（亩）。</li>
 *   <li>今日农事：按 {@code farm_type} 分桶计数列表 + 今日总条数。</li>
 *   <li>当月完成率：按作物分组的实际 / 预期产量列表（完成率前端算）。</li>
 * </ul>
 *
 * <p>无数据时各计数返 0、总面积返 ZERO、列表返空，不抛错（service 用 null-safe 兜底）。</p>
 *
 * @author djs
 * @since PLT-DASH-001
 */
@Data
public class PlantDashboardSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 空闲地块数（{@code plot_status = 1}）。
     */
    private Integer idlePlotCount;

    /**
     * 种植中地块数（{@code plot_status = 2}）。
     */
    private Integer plantingPlotCount;

    /**
     * 采摘中地块数（{@code plot_status = 3}）。
     */
    private Integer harvestingPlotCount;

    /**
     * 待种地块数（存在 {@code plant_status = 'pending'} 且 {@code begin_actualdate IS NULL}
     * 明细的不重复地块数）。
     */
    private Integer pendingPlotCount;

    /**
     * 地块总数（全部未删地块）。
     */
    private Integer totalPlotCount;

    /**
     * 土地总面积（亩，{@code SUM(plot_area)}）。
     */
    private BigDecimal totalPlotArea;

    /**
     * 今日农事按类型分桶列表（{@code farm_date = CURDATE()} GROUP BY {@code farm_type}）。
     */
    private List<FarmWorkCountVo> todayFarmWork;

    /**
     * 今日农事总条数（{@code farm_date = CURDATE()}）。
     */
    private Integer todayFarmWorkTotal;

    /**
     * 当月种植任务完成率列表（按作物分组，前端按 actual/expected 算柱高）。
     */
    private List<MonthCompletionItemVo> monthCompletion;

    /**
     * 全空兜底实例（无任何数据时返回，避免前端空指针）。
     *
     * @return 计数全 0、面积 ZERO、列表空的 VO
     */
    public static PlantDashboardSummaryVo empty() {
        PlantDashboardSummaryVo vo = new PlantDashboardSummaryVo();
        vo.setIdlePlotCount(0);
        vo.setPlantingPlotCount(0);
        vo.setHarvestingPlotCount(0);
        vo.setPendingPlotCount(0);
        vo.setTotalPlotCount(0);
        vo.setTotalPlotArea(BigDecimal.ZERO);
        vo.setTodayFarmWork(List.of());
        vo.setTodayFarmWorkTotal(0);
        vo.setMonthCompletion(List.of());
        return vo;
    }

}
