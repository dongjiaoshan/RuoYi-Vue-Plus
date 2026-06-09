package org.dromara.djs.warehouse.veg.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * mp 端采摘重量录入 BO（WMS-VEG-001 /harvest）。
 *
 * <p>地块维度采摘过磅：INSERT 一条 record_type=1 的 handle_record，聚合到 vegetable_handle.picked_weight，
 * 推进状态 pending → processing；weighFinish=1 时将汇总 is_weighed 置 1（不动 is_finish，不推 done）。</p>
 *
 * <p>字段名严格对齐 mp 契约 {@code miniapp/src/api/warehouse/vegHandle.ts} 的 {@code HarvestSubmitBody}。</p>
 *
 * <p>跨层契约：snowflake Long 字段（plantingRecordId / weighUserId）前端传 string，Jackson 反序列化为 Long。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
public class HarvestSubmitBo {

    /**
     * 上游种植记录 ID（FK → t_warehouse_planting_record.id）。
     */
    @NotNull(message = "{veg.planting_record_id.required}")
    private Long plantingRecordId;

    /**
     * 采摘重量(kg)，> 0。
     */
    @NotNull(message = "{veg.record_weight.required}")
    @DecimalMin(value = "0.001", message = "{veg.record_weight.positive}")
    private BigDecimal harvestWeight;

    /**
     * 地块是否称重完成 1=是 / 0=否（是时把该地块 weighStatus 推 done，即汇总 is_weighed=1）。
     */
    @NotNull(message = "{veg.weigh_finish.required}")
    private Integer weighFinish;

    /**
     * 称重人 userId（FK → sys_user.user_id）。
     */
    @NotNull(message = "{veg.weigh_user.required}")
    private Long weighUserId;

}
