package org.dromara.djs.plant.pick.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 采摘计划"按计划批量调整"BO（PLT-PLAN-002）。
 *
 * <p>从 admin 列表点"调整" → 进入按 (plantId, cropId) 限定的明细表格 → 修改 N 行 → 一次性提交。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Data
public class PickAdjustBatchBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "种植计划 id 必填")
    private Long plantId;

    @NotNull(message = "作物 id 必填")
    private Long cropId;

    @Valid
    @NotEmpty(message = "至少调整 1 行明细")
    private List<PickDetailAdjustBo> rows;
}
