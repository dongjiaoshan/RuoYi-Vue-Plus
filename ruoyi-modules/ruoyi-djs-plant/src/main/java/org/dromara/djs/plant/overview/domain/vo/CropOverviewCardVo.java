package org.dromara.djs.plant.overview.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 种植总览-单作物卡片 VO（FIX-PLT-AD-OVERVIEW-001 图1 作物卡片网格）。
 *
 * <p>每作物一卡，整卡可点击下钻到作物详情页（携 {@code cropId}）。指标分两组：</p>
 * <ul>
 *   <li>「计划」组：planPlotCount / planArea / planExpectedYield（按该作物全部明细计划口径）</li>
 *   <li>「已完成」组：donePlotCount / doneArea / doneHarvestYield（已实际开始种植 / 已采摘口径）</li>
 * </ul>
 *
 * <p>单位：面积亩，产量 <b>kg</b>（卡片层不换算吨，决策#7：卡片/明细用 kg）。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-OVERVIEW-001
 */
@Data
public class CropOverviewCardVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物 id（下钻 query 参数 cropId）。 */
    private Long cropId;

    /** 作物名称。 */
    private String cropName;

    /** 作物缩略图 ossId（卡片缩略图）。 */
    private String cropImageOssId;

    /** 当前地已种（已实际开始种植的地块数，= 已完成组 donePlotCount，卡片头展示用）。 */
    private Integer currentPlantedPlotCount;

    /** 当前已种面积（亩，已实际开始种植地块面积合计）。 */
    private BigDecimal currentPlantedArea;

    // ============== 「计划」组 ==============

    /** 计划地块数（该作物全部明细行去重地块数）。 */
    private Integer planPlotCount;

    /** 计划面积（亩，SUM(plot_area)）。 */
    private BigDecimal planArea;

    /** 计划预计产量（kg，SUM(expected_yield)）。 */
    private BigDecimal planExpectedYield;

    // ============== 「已完成」组 ==============

    /** 已种地数（已实际开始种植 begin_actualdate IS NOT NULL 的地块数）。 */
    private Integer donePlotCount;

    /** 已种面积（亩，已开始种植地块面积合计）。 */
    private BigDecimal doneArea;

    /** 已摘产量（kg，SUM(actual_yield)）。 */
    private BigDecimal doneHarvestYield;
}
