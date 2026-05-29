package org.dromara.djs.plant.pick.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采摘活动创建/编辑 BO（PLT-PLAN-002）。
 *
 * <p>{@code activityNo} 由 service 端 inline 生成，不接收客户端入参。
 * {@code totalYield} 由活动结束后聚合 plant_details 回填，编辑时也允许 admin 手填覆盖。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Data
public class PickActivityBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 编辑时必填，创建时为 null。 */
    private Long id;

    @NotBlank(message = "活动名称必填")
    private String activityName;

    @NotNull(message = "活动日期必填")
    private LocalDate activityDate;

    /** 创建时默认 upcoming，更新时允许切换至 ongoing/ended。 */
    private String activityStatus;

    @NotNull(message = "作物必选")
    private Long cropId;

    private Integer totalPlot;

    private BigDecimal totalYield;

    private Integer visitorCount;

    private String remark;
}
