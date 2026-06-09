package org.dromara.djs.plant.dashboard.applet.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 管理·种植管理看板「年度果蔬规划与执行」6 KPI（FIX-MGMT-MP-PLT-001）。
 *
 * <p>按 {@code plan_year} 维度聚合 {@code t_plant_plant_plan} + {@code t_plant_plant_details}。
 * 产量以吨为单位（明细 expected_yield / actual_yield 为 kg，/1000 → 吨）。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-PLT-001
 */
@Data
public class PlantAnnualPlanVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总计划面积（亩，SUM plot_area 当年全部明细）。 */
    private BigDecimal planArea;

    /** 计划品种数（COUNT DISTINCT crop_id 当年计划）。 */
    private Integer planCropCount;

    /** 预计总产量（吨，SUM expected_yield/1000 当年全部明细）。 */
    private BigDecimal expectedYieldTon;

    /** 已种植面积（亩，SUM plot_area WHERE begin_actualdate IS NOT NULL）。 */
    private BigDecimal plantedArea;

    /** 已种植品种数（COUNT DISTINCT crop_id WHERE begin_actualdate IS NOT NULL）。 */
    private Integer plantedCropCount;

    /** 已采摘总产量（吨，SUM actual_yield/1000）。 */
    private BigDecimal harvestedYieldTon;

}
