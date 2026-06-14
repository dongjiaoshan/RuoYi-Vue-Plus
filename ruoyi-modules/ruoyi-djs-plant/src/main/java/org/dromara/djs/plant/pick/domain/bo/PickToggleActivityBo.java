package org.dromara.djs.plant.pick.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 采摘计划「设为/取消采摘活动」单行 BO（PLT-PLAN-002）。
 *
 * <p>admin 采摘计划调整抽屉行内「设为/取消采摘活动」切换：仅改 {@code is_pick}。
 * 按 {@code plant_details.id}（PK）单行定位，无需 plantId。</p>
 *
 * <ul>
 *   <li>{@code isPick}：1=游客采摘活动 / 2=否（字典 djs_yes_no）</li>
 * </ul>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Data
public class PickToggleActivityBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "明细 id 必填")
    private Long id;

    /** 1=是 / 2=否（字典 djs_yes_no）。 */
    @NotNull(message = "采摘活动标记必填")
    private Integer isPick;
}
