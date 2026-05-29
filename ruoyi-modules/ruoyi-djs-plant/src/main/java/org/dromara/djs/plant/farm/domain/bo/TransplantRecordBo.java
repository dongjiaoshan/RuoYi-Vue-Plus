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
 * <p>{@code farmType} 服务端强制 {@code "transplant"}。{@code transplantPercent} 业务规则 ≤60
 * （xlsx 笔录 + doc/06 实现思路 #3），后端兜底校验。</p>
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

    /** 移栽百分比 0-100（业务规则 ≤60）。 */
    @NotNull(message = "{plant.farm.transplant_percent.required}")
    @Min(value = 0, message = "{plant.farm.transplant_percent.range}")
    @Max(value = 60, message = "{plant.farm.transplant_percent.max}")
    private Integer transplantPercent;

    @Size(max = 500)
    private String proofOssIds;

    @Size(max = 500)
    private String remark;
}
