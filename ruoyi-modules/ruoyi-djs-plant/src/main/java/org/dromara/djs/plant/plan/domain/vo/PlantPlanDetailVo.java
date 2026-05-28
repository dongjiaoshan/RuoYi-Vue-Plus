package org.dromara.djs.plant.plan.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 种植计划详情 VO（PLT-PLAN-001）：主表 + 明细 list。
 *
 * <p>用于 {@code GET /djs/plant/plan/{id}} 端点。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
public class PlantPlanDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private PlantPlanVo plan;

    private List<PlantDetailsVo> details;
}
