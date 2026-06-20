package org.dromara.djs.plant.plot.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 地块派生统计聚合行。
 *
 * <p>按 {@code plot_id} 聚合 {@code t_plant_plant_details}：历史种植次数 COUNT /
 * 最高亩作物产量 MAX(average_yield) / 最高亩产作物名（取该地块 average_yield 最大行 crop_name）。
 * service 层批量 enrich 回填 {@link PlotInfoVo}，避免列表 N+1。</p>
 *
 * @author djs
 */
@Data
public class PlotPlantingStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 地块 ID。 */
    private Long plotId;

    /** 历史种植次数（明细行数）。 */
    private Long historyPlantCount;

    /** 最高亩作物产量 kg/亩。 */
    private BigDecimal maxYieldPerMu;

    /** 最高亩产作物名。 */
    private String maxYieldCropName;
}
