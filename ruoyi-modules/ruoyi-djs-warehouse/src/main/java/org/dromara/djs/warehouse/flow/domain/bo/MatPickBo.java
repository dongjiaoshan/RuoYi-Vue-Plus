package org.dromara.djs.warehouse.flow.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 物资领用入参（mp 端 POST {@code /applet/warehouse/mat/pick}）。
 *
 * <p>Service 同事务：</p>
 * <ol>
 *   <li>SELECT location_stock FOR UPDATE 校验 product_stock ≥ quantity</li>
 *   <li>INSERT stock_flow(flow_type='pick_out', inout_type='OT', change_num=-quantity, change_quantity=quantity, stock_out_dest)</li>
 *   <li>UPDATE location_stock SET product_stock = product_stock - quantity（WHERE 兜底数量）</li>
 * </ol>
 *
 * <p>跨层契约：</p>
 * <ul>
 *   <li>{@code productId} / {@code locationId} 全链路 string（snowflake 19 位防截断）</li>
 *   <li>{@code quantity} BigDecimal 不能为空且 &gt; 0</li>
 *   <li>{@code stockOutDest} 必填（djs_stock_out_dest 字典）</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Data
public class MatPickBo {

    /**
     * 产品 ID（mp 端从 ProductPicker 选）。
     */
    @NotNull(message = "{mat.product_id.required}")
    private Long productId;

    /**
     * 库位 ID（mp 端从 LocationPicker 选）。
     */
    @NotNull(message = "{mat.location_id.required}")
    private Long locationId;

    /**
     * 领用数量（必填，&gt; 0；service 校验 ≤ location_stock.product_stock）。
     */
    @NotNull(message = "{mat.quantity.required}")
    @DecimalMin(value = "0.001", message = "{mat.quantity.positive}")
    private BigDecimal quantity;

    /**
     * 出库去向（必填，djs_stock_out_dest 字典 value，如"内部消耗" / "门店发货"）。
     */
    @NotNull(message = "{mat.stock_dest.required}")
    @Size(max = 32, message = "{mat.stock_dest.size}")
    private String stockOutDest;

    /**
     * 凭证图 OSS IDs CSV（可选，bizType=warehouse_mat_pick）。
     */
    @Size(max = 500, message = "{mat.proof_oss_ids.size}")
    private String proofOssIds;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "{mat.remark.size}")
    private String remark;

}
