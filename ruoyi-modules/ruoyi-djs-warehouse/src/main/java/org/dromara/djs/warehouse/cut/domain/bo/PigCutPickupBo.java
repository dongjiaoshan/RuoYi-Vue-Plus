package org.dromara.djs.warehouse.cut.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

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
     * 燎毛产出行 ID（FK → {@code t_warehouse_product_inhouse.id}，FIX-WMS-CUTPICKUP-SPLIT-001）。
     *
     * <p>admin 白条领用页按燎毛产出行（半只/半扇）逐条领用时回传：service 按此行消耗（置 pickup_status=1
     * + 记 pickup_weight），该白条所有产出行领满才推 bar pending_cut + 建整猪 cut_record。</p>
     *
     * <p>{@code null} = 整猪兜底路径（mp 旧端 / 白条无燎毛产出行）：直接推 bar + 建 cut_record，行为同旧。</p>
     */
    private Long inhouseId;

    /**
     * 入冻品库库位（FK → {@code t_warehouse_location_info.id}，领用阶段可空）。
     *
     * <p>FIX-WMS-PACK-CASHIER：白条领用收银台不再在领用阶段采集库位（仅领用进分割车间，
     * 实际入冻品库位在后续「分割出库称重」cutOut 阶段采集）。领用阶段仅作为 cut_record 的领用提示，
     * 可空——故去 {@code @NotNull}。</p>
     */
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
     * 领用称重 kg（分割白条领用时现场过磅录入）。
     *
     * <p>可空：为空时回落 {@code bar_info.in_weight} 快照（兼容 mp 旧端 / 不录重场景）。
     * 非空时 service 校验 {@code pickupWeight <= bar_info.marketing_weight}（不应大于该白条出栏重量）。</p>
     */
    @DecimalMin(value = "0", inclusive = false, message = "领用称重须大于 0")
    private BigDecimal pickupWeight;

    /**
     * 出库人 / 领用人（FK → {@code sys_user.user_id}，可空）。
     *
     * <p>mp 白条出库页「出库去向=分割车间」时由 {@code <EmployeePicker>} 选（默认当前登录人）。
     * 只决定 {@code cut_record.operator_id} 与预冷 {@code loss_flow.operator_id} 这两个业务归属字段；
     * 审计字段（{@code create_by} / {@code update_by}）与库存扣减、状态机流转仍恒用登录人，不受影响。
     * 为空（admin 白条领用页 / mp 旧端）→ 回落登录人，行为同旧。</p>
     */
    private Long operatorId;

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
