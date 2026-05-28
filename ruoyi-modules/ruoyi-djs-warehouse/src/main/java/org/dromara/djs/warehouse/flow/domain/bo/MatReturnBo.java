package org.dromara.djs.warehouse.flow.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 物资退回入参（mp 端 POST {@code /applet/warehouse/mat/return}）。
 *
 * <p>Service 同事务：</p>
 * <ol>
 *   <li>校验今日额度：{@code SUM(change_quantity)} WHERE flow_type=pick_out AND flow_by=user AND product_id AND CURDATE() ≥ 已退回 + 已损耗 + bo.quantity</li>
 *   <li>INSERT stock_flow(flow_type='return_in', inout_type='IN', change_num=+quantity)</li>
 *   <li>UPDATE location_stock SET product_stock = product_stock + quantity</li>
 * </ol>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Data
public class MatReturnBo {

    @NotNull(message = "{mat.product_id.required}")
    private Long productId;

    @NotNull(message = "{mat.location_id.required}")
    private Long locationId;

    /**
     * 退回数量（必填，&gt; 0；service 校验 ≤ 今日已领额度）。
     */
    @NotNull(message = "{mat.quantity.required}")
    @DecimalMin(value = "0.001", message = "{mat.quantity.positive}")
    private BigDecimal quantity;

    /**
     * 凭证图 OSS IDs CSV（可选）。
     */
    @Size(max = 500, message = "{mat.proof_oss_ids.size}")
    private String proofOssIds;

    @Size(max = 500, message = "{mat.remark.size}")
    private String remark;

}
