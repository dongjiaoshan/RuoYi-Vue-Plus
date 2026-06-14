package org.dromara.djs.plant.pick.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 采摘计划「设置计划」单行 BO（PLT-PLAN-002）。
 *
 * <p>admin 采摘计划调整抽屉行内「设置计划」modal 提交：仅 开始 / 结束采摘日期。
 * 按 {@code plant_details.id}（PK）单行定位，无需 plantId。</p>
 *
 * <ul>
 *   <li>{@code earliestHarvestdate}：计划最早采摘日期（必填）</li>
 *   <li>{@code lastHarvestdate}：计划最晚采摘日期（可空；为空时按作物采摘周期窗口由最早派生重算）</li>
 * </ul>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Data
public class PickSetScheduleBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "明细 id 必填")
    private Long id;

    /** 计划最早采摘日期（必填）。 */
    @NotNull(message = "开始采摘日期必填")
    private LocalDate earliestHarvestdate;

    /** 计划最晚采摘日期（可空，后端按窗口派生）。 */
    private LocalDate lastHarvestdate;
}
