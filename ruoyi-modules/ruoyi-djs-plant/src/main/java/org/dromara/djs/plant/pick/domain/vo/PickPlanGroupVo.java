package org.dromara.djs.plant.pick.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采摘计划列表行 VO（PLT-PLAN-002）。
 *
 * <p>按作物聚合 {@code t_plant_plant_details}（doc/10 §F-PLT-05）：</p>
 * <pre>
 *   SELECT crop_id, plant_id,
 *          COUNT(DISTINCT plot_id) AS plot_count,
 *          MIN(begin_harvestdate / earliest_harvestdate) AS earliest,
 *          MAX(end_harvestdate   / last_harvestdate)     AS latest,
 *          SUM(expected_yield) AS expected,
 *          SUM(actual_yield)   AS actual,
 *          SUM(is_pick=1)      AS activity_plot
 *   GROUP BY plant_id, crop_id;
 * </pre>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Data
public class PickPlanGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long plantId;
    private String planNo;
    private Integer planYear;
    private String planSeason;

    private Long cropId;
    private String cropName;

    /** 涉及地块数（COUNT DISTINCT plot_id）。 */
    private Integer plotCount;

    /** 计划最早采摘（MIN earliest）。 */
    private LocalDate planEarliest;

    /** 计划最晚采摘（MAX last）。 */
    private LocalDate planLatest;

    /** 实际开始采摘（MIN begin_harvestdate）。 */
    private LocalDate actualBegin;

    /** 实际结束采摘（MAX end_harvestdate）。 */
    private LocalDate actualEnd;

    /** 预计产量（SUM expected_yield，kg）。 */
    private BigDecimal expectedYield;

    /** 实际产量（SUM actual_yield，kg）。 */
    private BigDecimal actualYield;

    /** 已设为采摘活动的地块数（SUM is_pick=1）。 */
    private Integer activityPlotCount;
}
