package org.dromara.djs.plant.farm.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 农事「采摘活动管理」采摘重量录入 BO（FIX-PLT-MP-HARVEST-001，#3=a）。
 *
 * <p>原型采摘重量录入弹窗：采摘日期 + 采摘重量(kg) + 记录班组。提交后累加对应
 * {@code t_plant_plant_details.actual_yield}（按 plantId+plotId+cropId 定位未结束明细），
 * 并 INSERT 一行 {@code t_plant_farm_records}（farm_type=harvest_activity，携带 harvest_weight）。
 * 采收 tab 已去重量（#3=a），重量唯一录入口为本端点，无双计。</p>
 *
 * @author djs
 * @since FIX-PLT-MP-HARVEST-001
 */
@Data
public class HarvestWeightBo {

    /** 关联种植计划 id（{@code t_plant_plant_plan.id}）。 */
    @NotNull(message = "{plant.farm.plant_id.required}")
    private Long plantId;

    /** 地块 id（{@code t_plant_plot_info.id}）。 */
    @NotNull(message = "{plant.farm.plot.required}")
    private Long plotId;

    /** 作物 id（{@code t_plant_crop_info.id}）。 */
    @NotNull(message = "{plant.farm.crop.required}")
    private Long cropId;

    /** 处理班组 id（{@code t_plant_work_team.id}）。 */
    @NotNull(message = "{plant.farm.team.required}")
    private Long farmBy;

    /** 采摘日期。 */
    @NotNull(message = "{plant.farm.date.required}")
    private LocalDate farmDate;

    /** 采摘重量 kg（> 0，累加进 plant_details.actual_yield）。 */
    @NotNull(message = "请输入采摘重量")
    @DecimalMin(value = "0.001", message = "采摘重量必须大于 0")
    private BigDecimal harvestWeight;

    /** OSS 凭证图 ossIds，逗号分隔（可空）。 */
    @Size(max = 500)
    private String proofOssIds;

    /** 备注（可空）。 */
    @Size(max = 500)
    private String remark;
}
