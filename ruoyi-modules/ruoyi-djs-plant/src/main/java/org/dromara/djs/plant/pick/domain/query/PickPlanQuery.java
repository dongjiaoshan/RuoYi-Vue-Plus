package org.dromara.djs.plant.pick.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

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

    /** 作物 id（原型主筛选「作物名称」下拉）。 */
    private Long cropId;

    /** 采摘状态 djs_pick_status。可选过滤 details.harvest_status。 */
    private String harvestStatus;
}
