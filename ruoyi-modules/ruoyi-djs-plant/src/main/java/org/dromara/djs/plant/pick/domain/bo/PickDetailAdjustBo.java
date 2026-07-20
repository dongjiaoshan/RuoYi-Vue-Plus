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
 * <p>调整字段（admin 仅放开计划侧 + 采摘活动 + 班组）：</p>
 * <ul>
 *   <li>{@code earliestHarvestdate}：计划最早采摘日期（用户可改）</li>
 *   <li>{@code lastHarvestdate}：计划最晚采摘日期（前端按窗口天数派生；后端按作物采摘周期兜底重算）</li>
 *   <li>{@code isPick}：1=游客采摘活动 / 2=否（admin "设为采摘活动" 即改此字段）</li>
 *   <li>{@code harvestBy}：采摘班组</li>
 * </ul>
 *
 * <p>实际采摘起止（begin/end_harvestdate）由小程序采收录入回写，admin 调整接口不接收、不改。
 * 其他字段（plot/crop/plant_status 等）一律不允许通过此接口改。</p>
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

    /** 计划最早采摘日期（用户可改）。 */
    private LocalDate earliestHarvestdate;

    /** 计划最晚采摘日期（前端派生；后端兜底重算）。 */
    private LocalDate lastHarvestdate;

    /** 1=是 / 2=否（字典 djs_yes_no）。 */
    private Integer isPick;

    private Long harvestBy;
}
