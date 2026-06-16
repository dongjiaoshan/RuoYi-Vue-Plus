package org.dromara.djs.plant.pick.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 采摘计划聚合查询（PLT-PLAN-002）。
 *
 * <p>查 {@code t_plant_plant_details} GROUP BY (plant_id, crop_id)。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Data
public class PickPlanQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物 id（保留：原型下拉 / 列表跳转回填）。 */
    private Long cropId;

    /** 作物名称（模糊匹配 crop.crop_name）。 */
    private String cropName;

    /** 采摘状态 djs_pick_status。可选过滤 details.harvest_status。 */
    private String harvestStatus;

    /** 最早开始时间范围起（含；过滤聚合后 MIN(earliest_harvestdate)）。 */
    private LocalDate beginEarliest;

    /** 最早开始时间范围止（含；过滤聚合后 MIN(earliest_harvestdate)）。 */
    private LocalDate endEarliest;
}
