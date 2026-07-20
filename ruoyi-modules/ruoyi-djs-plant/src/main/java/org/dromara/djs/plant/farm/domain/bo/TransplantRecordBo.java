package org.dromara.djs.plant.farm.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 移栽农事录入 BO（PLT-WORK-001 独立画布）。
 *
 * <p>{@code farmType} 服务端强制 {@code "transplant"}。{@code transplantPercent} 单次区间 1-100，
 * 且同 (作物, 源地块) 多次移栽累计不超过 100%（service 层读历史累计兜底校验）。</p>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Data
public class TransplantRecordBo {

    /** 关联种植计划。 */
    @NotNull(message = "{plant.farm.plant_id.required}")
    private Long plantId;

    /** 源地块 ID。 */
    @NotNull(message = "{plant.farm.plot.required}")
    private Long plotId;

    /** 作物 ID。 */
    @NotNull(message = "{plant.farm.crop.required}")
    private Long cropId;

    /** 处理班组 ID。 */
    @NotNull(message = "{plant.farm.team.required}")
    private Long farmBy;

    /** 农事日期。 */
    @NotNull(message = "{plant.farm.date.required}")
    private LocalDate farmDate;

    /** 转移地块 ID。 */
    @NotNull(message = "{plant.farm.transplant_plot.required}")
    private Long transplantPlot;

    /** 移栽百分比 1-100（单次；多次累计 ≤100% 由 service 层校验）。 */
    @NotNull(message = "{plant.farm.transplant_percent.required}")
    @Min(value = 1, message = "{plant.farm.transplant_percent.range}")
    @Max(value = 100, message = "{plant.farm.transplant_percent.max}")
    private Integer transplantPercent;

    @Size(max = 500)
    private String proofOssIds;

    @Size(max = 500)
    private String remark;
}
