package org.dromara.djs.plant.farm.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 结束移栽 BO（PLT-TRANSPLANT-REDO-001）。
 *
 * <p>育苗苗损 / 不再继续移栽时的逃生口：按<b>当前累计</b>触发与「累计 100%」同一落地逻辑——
 * 已收苗的目标地块各建满种明细、源育苗地块转退茬。避免部分移栽（&lt;100%）后源地块永远卡在移栽候选、
 * 进不了退茬。无移栽记录 / 源明细已采摘完成（已落地）时拒绝 / 幂等返 0。</p>
 *
 * @author djs
 * @since PLT-TRANSPLANT-REDO-001
 */
@Data
public class TransplantFinalizeBo {

    /** 关联种植计划。 */
    @NotNull(message = "{plant.farm.plant_id.required}")
    private Long plantId;

    /** 源育苗地块 ID。 */
    @NotNull(message = "{plant.farm.plot.required}")
    private Long plotId;

    /** 作物 ID。 */
    @NotNull(message = "{plant.farm.crop.required}")
    private Long cropId;

    /** 结束移栽日期（写源育苗明细 end_harvestdate）。 */
    @NotNull(message = "{plant.farm.date.required}")
    private LocalDate farmDate;
}
