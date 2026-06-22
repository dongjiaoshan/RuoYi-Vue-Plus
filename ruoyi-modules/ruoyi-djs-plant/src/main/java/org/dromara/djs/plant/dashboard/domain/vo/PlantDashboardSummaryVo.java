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
 *   <li>今日工作：固定 6 格（种植 / 采摘 / 空地管理 / 种植管理 / 灾害损失 各地块数 + 采摘活动 kg）。</li>
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
     * 今日工作 - 种植（今日 {@code begin_actualdate = CURDATE()} 的不重复地块数）。
     */
    private Integer todayPlantingPlotCount;

    /**
     * 今日工作 - 采摘（今日 {@code begin_harvestdate = CURDATE()} 的不重复地块数）。
     */
    private Integer todayHarvestPlotCount;

    /**
     * 今日工作 - 空地管理（今日农事 {@code farm_type IN (tillage_break, tillage_prepare, fertilize)}
     * 的不重复地块数）。
     */
    private Integer todayIdleMgmtPlotCount;

    /**
     * 今日工作 - 种植管理（今日农事 {@code farm_type IN (transplant, water_fertilize, irrigation,
     * weed, pruning, pest_control, rotation)} 的不重复地块数）。
     */
    private Integer todayPlantMgmtPlotCount;

    /**
     * 今日工作 - 灾害损失（今日农事 {@code farm_type = 'disaster'} 的不重复地块数）。
     */
    private Integer todayDisasterPlotCount;

    /**
     * 今日工作 - 采摘活动量 kg（V1 恒 0）。
     *
     * <p>采摘活动已改只读报表，原数据源 {@code t_plant_pick_activity} 已废弃，故该指标 V1 恒返 0；
     * 字段保留供前端继续读取，不删。</p>
     */
    private BigDecimal todayPickActivityWeight;

    /**
     * 当前种植面积（亩，{@code SUM(plot_area)} WHERE {@code plot_status = 2} 种植中）。
     *
     * <p>区块 ① "当前种植面积亩"。无数据返 ZERO。</p>
     */
    private BigDecimal currentPlantingArea;

    /**
     * 当前预计产量（kg，{@code SUM(expected_yield)} WHERE {@code plant_status != 'completed'}）。
     *
     * <p>区块 ① "当前预计产量"。前端按 kg → 万斤（{@code kg × 2 / 10000}）换算展示。无数据返 ZERO。</p>
     */
    private BigDecimal currentExpectedYield;

    /**
     * 当月种植任务完成率列表（按作物分组，前端按 actual/expected 算柱高）。
     */
    private List<MonthCompletionItemVo> monthCompletion;

    /**
     * 实时种植物统计列表（区块 ④，按作物分组：在种地块数 bar + 预计产量 line）。
     */
    private List<CropPlantStatItemVo> cropPlantStat;

    /**
     * 有机证书情况一览（区块 ③，土地 / 作物证书最早到期天数 + 无证书 / 已建档品类数）。
     */
    private OrganicCertOverviewVo organicCertOverview;

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
        vo.setCurrentPlantingArea(BigDecimal.ZERO);
        vo.setCurrentExpectedYield(BigDecimal.ZERO);
        vo.setTodayPlantingPlotCount(0);
        vo.setTodayHarvestPlotCount(0);
        vo.setTodayIdleMgmtPlotCount(0);
        vo.setTodayPlantMgmtPlotCount(0);
        vo.setTodayDisasterPlotCount(0);
        vo.setTodayPickActivityWeight(BigDecimal.ZERO);
        vo.setMonthCompletion(List.of());
        vo.setCropPlantStat(List.of());
        vo.setOrganicCertOverview(new OrganicCertOverviewVo());
        return vo;
    }

}
