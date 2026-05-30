package org.dromara.djs.plant.pick.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 采摘计划调整单行 BO（PLT-PLAN-002）。
 *
 * <p>admin 调整页表格的 1 行 = {@code t_plant_plant_details} 的 1 行。</p>
 *
 * <p>调整字段（仅 4 项）：</p>
 * <ul>
 *   <li>{@code beginHarvestdate} / {@code endHarvestdate}：实际采摘起止</li>
 *   <li>{@code isPick}：1=游客采摘活动 / 2=否（admin "设为采摘活动" 即改此字段）</li>
 *   <li>{@code harvestBy}：采摘班组</li>
 * </ul>
 *
 * <p>其他字段（earliest/last/plot/crop/plant_status 等）一律不允许通过此接口改。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Data
public class PickDetailAdjustBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "明细 id 必填")
    private Long id;

    private LocalDate beginHarvestdate;
    private LocalDate endHarvestdate;

    /** 1=是 / 2=否（字典 djs_yes_no）。 */
    private Integer isPick;

    private Long harvestBy;
}
