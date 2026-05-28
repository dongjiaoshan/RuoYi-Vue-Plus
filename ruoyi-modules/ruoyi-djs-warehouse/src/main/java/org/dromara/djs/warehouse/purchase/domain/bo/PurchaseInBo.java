package org.dromara.djs.warehouse.purchase.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购入库简化版入参（D9 hotfix）。
 *
 * <p>D9 testing-human §5 暴露包材领用前置库存缺失 — V1 真实采购流程（含供应商 / 单价 / 验收）延后到
 * D11+ WMS 实采购模块，本 hotfix 仅给 admin 一个"补一笔入库流水 + 累加库存"的最简路径，让测试与
 * 包材 / 采食 / 种子等 9 类物资领用都能跑通。</p>
 *
 * <h3>Service 同事务</h3>
 * <ol>
 *   <li>UPSERT location_stock：先按 productId + locationId + tenant_id 查；存在 → 原子 add；不存在 → INSERT</li>
 *   <li>INSERT stock_flow（flow_type='purchase_in', inout_type='IN', change_num=+quantity, change_quantity=quantity）</li>
 * </ol>
 *
 * <h3>跨层契约</h3>
 * <ul>
 *   <li>{@code productId} / {@code locationId} 必填</li>
 *   <li>{@code quantity} BigDecimal &gt; 0</li>
 *   <li>{@code remark} 可选</li>
 * </ul>
 *
 * @author djs
 * @since D9 hotfix purchase-in
 */
@Data
public class PurchaseInBo {

    /**
     * 产品 ID（FK → t_warehouse_product_info.id）。
     */
    @NotNull(message = "{purchase.product_id.required}")
    private Long productId;

    /**
     * 库位 ID（FK → t_warehouse_location_info.id）。
     */
    @NotNull(message = "{purchase.location_id.required}")
    private Long locationId;

    /**
     * 入库数量（必填，&gt; 0）。
     */
    @NotNull(message = "{purchase.quantity.required}")
    @DecimalMin(value = "0.001", message = "{purchase.quantity.positive}")
    private BigDecimal quantity;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "{purchase.remark.size}")
    private String remark;

}
