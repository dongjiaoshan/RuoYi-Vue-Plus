package org.dromara.djs.plant.plan.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 种植计划查询参数（PLT-PLAN-001）。
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlantPlanQuery extends BaseEntity {

    /** 业务码模糊。 */
    private String planNo;

    /** 计划年份精确。 */
    private Integer planYear;

    /** 季节字典 djs_planting_season。 */
    private String planSeason;

    /** 作物 id 精确。 */
    private Long cropId;

    /** 计划状态字典 djs_plant_plan_status。 */
    private String plantStatus;
}
