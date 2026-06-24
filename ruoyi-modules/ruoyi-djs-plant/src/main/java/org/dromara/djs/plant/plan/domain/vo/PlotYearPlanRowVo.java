package org.dromara.djs.plant.plan.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 向导 step3「查看」弹框用：某地块在某年份的计划实际数据行（PLT-PLAN-001 / 测试反馈 row27）。
 *
 * <p>{@code GET /djs/plant/plan/plotPlanDetails?plotId=&planYear=}：返回该地块当年的全部种植明细，
 * 以列表展示：计划种植时间（plantMonth + plantPeriod）/ 计划种植作物（cropName）/
 * 最早采摘日期 / 最晚采摘日期 / 种植状态 / 采摘状态。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
public class PlotYearPlanRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 种植明细 id。 */
    private Long detailId;

    /** 计划种植月份（1-12）。 */
    private Integer plantMonth;

    /** 计划种植旬别（05/15/25，dict djs_plant_period）；与 plantMonth 拼成「6月中旬」。 */
    private String plantPeriod;

    /** 计划种植作物名（crop_info.crop_name）。 */
    private String cropName;

    /** 最早采摘日期。 */
    private LocalDate earliestHarvestdate;

    /** 最晚采摘日期。 */
    private LocalDate lastHarvestdate;

    /** 种植状态（dict djs_plant_plan_status）。 */
    private String plantStatus;

    /** 采摘状态（dict djs_pick_status）。 */
    private String harvestStatus;
}
