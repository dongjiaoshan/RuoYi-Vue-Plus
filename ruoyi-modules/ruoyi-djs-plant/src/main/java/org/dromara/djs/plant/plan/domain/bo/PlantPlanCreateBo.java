package org.dromara.djs.plant.plan.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 种植计划新建 BO（PLT-PLAN-001 向导 3 步合一提交）。
 *
 * <p>step1 → {@link #planYear} + {@link #planSeason}；
 * step2 → {@link #cropId}；
 * step3 → {@link #details}（每地块一行）。</p>
 *
 * <p>service 单事务：INSERT plan + batch INSERT details + recalc 主表聚合字段。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
public class PlantPlanCreateBo {

    /** step1 年份（例 2026）。 */
    @NotNull(message = "{plant.plan.planYear.required}")
    @Min(value = 2020, message = "{plant.plan.planYear.range}")
    @Max(value = 2099, message = "{plant.plan.planYear.range}")
    private Integer planYear;

    /** step1 季节字典 djs_planting_season（spring/summer/autumn/winter）。 */
    @NotBlank(message = "{plant.plan.planSeason.required}")
    @Pattern(regexp = "^(spring|summer|autumn|winter)$", message = "{plant.plan.planSeason.invalid}")
    private String planSeason;

    /** step2 作物（单选）。 */
    @NotNull(message = "{plant.plan.cropId.required}")
    private Long cropId;

    /** 自由文本"计划种植时间"（如"4 月上旬"），可空，主表 plant_date 字段。 */
    private String plantDate;

    /** step3 明细 list（每地块一行）。 */
    @NotEmpty(message = "{plant.plan.details.required}")
    @Valid
    private List<PlantDetailInputBo> details;
}
