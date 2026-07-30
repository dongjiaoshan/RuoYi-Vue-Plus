package org.dromara.djs.plant.farm.domain.bo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 灾害农事录入 BO（PLT-WORK-001 独立画布）。
 *
 * <p>{@code farmType} 服务端强制写入 {@code "disaster"}，BO 不收。提交后触发
 * {@code plant_details.loss_yield} 累加（业务规则 doc/10 §F-PLT-06）。</p>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Data
public class DisasterRecordBo {

    /** 关联种植计划（灾害必关联到具体计划，方便累加 loss_yield 到对应 details）。 */
    @NotNull(message = "{plant.farm.plant_id.required}")
    private Long plantId;

    /** 地块 ID。 */
    @NotNull(message = "{plant.farm.plot.required}")
    private Long plotId;

    /** 作物 ID。 */
    @NotNull(message = "{plant.farm.crop.required}")
    private Long cropId;

    /** 处理班组 ID。 */
    @NotNull(message = "{plant.farm.team.required}")
    private Long farmBy;

    /** 处理班组全集（G1-TEAMS-MULTISELECT，row37）。非空时写中间表，旧单列 farmBy 取第一个；为 null 时退化为单值 farmBy。 */
    private java.util.List<Long> farmByIds;

    /** 农事日期（灾害发生日）。 */
    @NotNull(message = "{plant.farm.date.required}")
    private LocalDate farmDate;

    /** 灾害类型（字典 djs_disaster_type）。 */
    @NotNull(message = "{plant.farm.disaster_type.required}")
    @Size(max = 32)
    private String disasterType;

    /** 损失率 0-100。 */
    @NotNull(message = "{plant.farm.loss_rate.required}")
    @DecimalMin(value = "0", message = "{plant.farm.loss_rate.range}")
    @DecimalMax(value = "100", message = "{plant.farm.loss_rate.range}")
    private BigDecimal lossRate;

    /**
     * 损失产量（可选）：由服务端按 预计产量 × 损失率/100 统一算出并回写，前端无需传。
     * 历史调用方仍可传入但会被服务端计算值覆盖。
     */
    @PositiveOrZero(message = "{plant.farm.loss_yield.positive}")
    private BigDecimal lossYield;

    @Size(max = 500)
    private String proofOssIds;

    @Size(max = 500)
    private String remark;
}
