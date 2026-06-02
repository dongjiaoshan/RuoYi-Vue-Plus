package org.dromara.djs.plant.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 种植看板 - 当月完成率柱状图单行（PLT-DASH-001）。
 *
 * <p>来源：{@code t_plant_plant_details} 当月开始种植的明细，按 {@code crop_id} 分组，
 * JOIN {@code t_plant_crop_info} 取作物名。be 只返原始 {@code actualYield} / {@code expectedYield}
 * 两值，完成率（{@code actual / expected}，分母 0 → 0）在前端算，避免后端 BigDecimal
 * 除法精度纠结。</p>
 *
 * @author djs
 * @since PLT-DASH-001
 */
@Data
public class MonthCompletionItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作物名称。
     */
    private String cropName;

    /**
     * 当月该作物实际产量合计（{@code SUM(actual_yield)}，无则 0）。
     */
    private BigDecimal actualYield;

    /**
     * 当月该作物预期产量合计（{@code SUM(expected_yield)}，无则 0）。
     */
    private BigDecimal expectedYield;

}
