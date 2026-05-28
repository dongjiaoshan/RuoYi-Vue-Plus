package org.dromara.djs.plant.plan.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 种植计划更新 BO（PLT-PLAN-001 编辑场景）。
 *
 * <p>service 约束：</p>
 * <ul>
 *   <li>{@code plant_status='ongoing'} 时禁止修改 {@code cropId}（业务规则）</li>
 *   <li>{@code details} 中已 {@code beginActualdate IS NOT NULL} 的行不允许修改 plot/month/period（仅允许改班组/采摘字段）</li>
 *   <li>{@code details} 中已 {@code beginActualdate IS NOT NULL} 的行不允许删除</li>
 *   <li>详细策略：service 收到 update list 时 diff existing：</li>
 *   <li>  - 旧行不在新 list 中 + 已开始 → 抛 ServiceException；旧行不在新 list + 未开始 → softDelete</li>
 *   <li>  - 新行无 id → INSERT</li>
 *   <li>  - 既有 id：未开始 → 全字段 UPDATE；已开始 → 只 UPDATE plant_by/harvest_by</li>
 * </ul>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
public class PlantPlanUpdateBo {

    @NotNull(message = "{plant.plan.id.required}")
    private Long id;

    /** 可改字段。 */
    private String planSeason;
    private Long cropId;
    private String plantDate;

    /** 明细 list（参类 javadoc 处理策略）。 */
    @Valid
    private List<PlantDetailInputBo> details;
}
