package org.dromara.djs.warehouse.flow.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 物资损耗入参（mp 端 POST {@code /applet/warehouse/mat/loss}）。
 *
 * <p>Service 同事务：</p>
 * <ol>
 *   <li>校验今日额度：同 {@link MatReturnBo} 的校验逻辑（领用 ≥ 已退 + 已损 + bo.quantity）</li>
 *   <li>INSERT stock_flow(flow_type='loss', inout_type='OT', change_num=-quantity)</li>
 *   <li>UPDATE location_stock SET product_stock = product_stock - quantity（同时校验剩余库存够扣，避免账实倒挂）</li>
 * </ol>
 *
 * <p>注：损耗与退回不同——退回数量算"加回库存"，损耗数量算"扣减库存"。两者都要校验"今日已领足额"，否则越界。</p>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Data
public class MatLossBo {

    @NotNull(message = "{mat.product_id.required}")
    private Long productId;

    @NotNull(message = "{mat.location_id.required}")
    private Long locationId;

    @NotNull(message = "{mat.quantity.required}")
    @DecimalMin(value = "0.001", message = "{mat.quantity.positive}")
    private BigDecimal quantity;

    @Size(max = 500, message = "{mat.proof_oss_ids.size}")
    private String proofOssIds;

    @Size(max = 500, message = "{mat.remark.size}")
    private String remark;

}
