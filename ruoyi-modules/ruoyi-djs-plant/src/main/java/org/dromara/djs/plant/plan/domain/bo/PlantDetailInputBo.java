package org.dromara.djs.plant.plan.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 种植计划明细子项输入 BO（PLT-PLAN-001 向导 step3 单元）。
 *
 * <p>3 步向导提交时由 {@link PlantPlanCreateBo} 持有，list 每元素对应 1 行 {@code t_plant_plant_details}。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
public class PlantDetailInputBo {

    /** 编辑场景下传 id（新增行为空）。 */
    private Long id;

    /** 地块 FK。 */
    @NotNull(message = "{plant.plan.plotId.required}")
    private Long plotId;

    /** 计划月份 1-12。 */
    @NotNull(message = "{plant.plan.plantMonth.required}")
    @Min(value = 1, message = "{plant.plan.plantMonth.range}")
    @Max(value = 12, message = "{plant.plan.plantMonth.range}")
    private Integer plantMonth;

    /** 计划阶段 CHAR(2) 字典 djs_plant_period（05/15/25）。 */
    @NotBlank(message = "{plant.plan.plantPeriod.required}")
    @Pattern(regexp = "^(05|15|25)$", message = "{plant.plan.plantPeriod.invalid}")
    private String plantPeriod;

    /** 种植班组（可空）。 */
    private Long plantBy;

    /** 采摘班组（可空）。 */
    private Long harvestBy;
}
