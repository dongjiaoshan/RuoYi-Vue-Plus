package org.dromara.djs.plant.farm.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 退茬农事录入 BO（PLT-WORK-001）。
 *
 * <p>{@code farmType} 服务端强制 {@code "rotation"}。提交后触发：</p>
 * <ul>
 *   <li>{@code plot_info.plot_status} 置 1（空闲）— PlotInfoMapper.updateById</li>
 *   <li>同 plot 下未结束的 {@code plant_details}（end_actualdate IS NULL）→ {@code plant_status='completed'} + {@code end_actualdate=NOW()}</li>
 * </ul>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Data
public class RotationRecordBo {

    /** 关联种植计划。 */
    @NotNull(message = "{plant.farm.plant_id.required}")
    private Long plantId;

    /** 地块 ID（要回退状态的地块）。 */
    @NotNull(message = "{plant.farm.plot.required}")
    private Long plotId;

    /** 作物 ID（冗余下账）。 */
    @NotNull(message = "{plant.farm.crop.required}")
    private Long cropId;

    /** 处理班组 ID。 */
    @NotNull(message = "{plant.farm.team.required}")
    private Long farmBy;

    /** 农事日期（退茬日）。 */
    @NotNull(message = "{plant.farm.date.required}")
    private LocalDate farmDate;

    @Size(max = 500)
    private String proofOssIds;

    @Size(max = 500)
    private String remark;
}
