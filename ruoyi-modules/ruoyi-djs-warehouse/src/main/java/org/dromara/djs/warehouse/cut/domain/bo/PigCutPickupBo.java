package org.dromara.djs.warehouse.cut.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 阶段 1 白条领用 BO（WMS-PIG-002）。
 *
 * <p>service 同事务：</p>
 * <ol>
 *   <li>SELECT bar_info FOR UPDATE，校验 status='in_stock'</li>
 *   <li>UPDATE bar_info SET status='pending_cut'（乐观锁）</li>
 *   <li>INSERT cut_record (cut_status='picked', pickup_time=NOW, pickup_weight=bar.in_weight)</li>
 * </ol>
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Data
public class PigCutPickupBo {

    /**
     * 白条 ID（FK → {@code t_warehouse_bar_info.id}）。
     */
    @NotNull(message = "{cut.bar_info_id.required}")
    private Long barInfoId;

    /**
     * 入冻品库库位（FK → {@code t_warehouse_location_info.id}）。
     */
    @NotNull(message = "{cut.location_id.required}")
    private Long locationId;

    /**
     * 指定目标门店（V1 可空）。
     */
    private Long targetStoreId;

    /**
     * 关联需求（V1 可空）。
     */
    private Long targetDemandId;

    /**
     * 是否半扇分割 1=是 / 2=否（整只），默认 2。
     */
    private Integer isHalf;

    /**
     * 备注。
     */
    @Size(max = 500, message = "{cut.remark.size}")
    private String remark;

}
