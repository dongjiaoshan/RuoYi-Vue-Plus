package org.dromara.djs.warehouse.vegdock.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 外购果蔬果蔬间入库确认入参（FIX-WMS-MP-VEGDOCK-001，原型图 43 实际入库量）。
 *
 * <p>mp 工人在「果蔬间入库」对某笔外购到货记录录入「实际入库量」并选库位确认。service 同事务：
 * 累加 {@code actual_weight} / 递减 {@code pending_weight} / 推进 status → UPSERT location_stock +
 * INSERT stock_flow（flow_type={@code veg_purchase_in}, inout_type=IN）。</p>
 *
 * <h3>跨层契约</h3>
 * <ul>
 *   <li>{@code id} 外购到货记录 ID（snowflake，必填）</li>
 *   <li>{@code locationId} 入库库位 ID（snowflake，必填）</li>
 *   <li>{@code actualWeight} BigDecimal &gt; 0，且不得超过该记录当前 {@code pendingWeight}（service 校验）</li>
 * </ul>
 *
 * @author djs
 * @since FIX-WMS-MP-VEGDOCK-001
 */
@Data
public class VegPurchaseInboundBo {

    /**
     * 外购到货记录 ID（{@code t_warehouse_veg_purchase.id}，必填）。
     */
    @NotNull(message = "缺少入库记录 ID")
    private Long id;

    /**
     * 入库库位 ID（必填）。
     */
    @NotNull(message = "请选择入库库位")
    private Long locationId;

    /**
     * 本次实际入库量(kg)（必填，&gt; 0，≤ 当前待入库量）。
     */
    @NotNull(message = "请填写实际入库量")
    @DecimalMin(value = "0.001", message = "实际入库量必须大于 0")
    private BigDecimal actualWeight;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "备注过长")
    private String remark;

}
