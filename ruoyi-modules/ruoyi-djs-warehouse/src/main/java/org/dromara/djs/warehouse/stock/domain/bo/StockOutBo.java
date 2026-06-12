package org.dromara.djs.warehouse.stock.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存查询行「产品出库」入参（DJS-FIX-WMS-RALN-B）。
 *
 * <p>admin 库存查询页行操作「产品出库」→ 弹窗（出库日期 / 出库量 / 出库方式）提交。</p>
 *
 * <p>Service 同事务（照 {@code MatFlowServiceImpl.pick} 范式）：</p>
 * <ol>
 *   <li>校验产品存在 + 库位未被盘点锁定</li>
 *   <li>INSERT stock_flow（{@code inout_type='OT'} / {@code flow_type='other'} → 列表派生「后台出库」，
 *       与商品详情业务流水 {@code backend_out} 口径一致）</li>
 *   <li>UPDATE location_stock 原子扣减（{@code product_stock >= quantity} 行锁 + 数量校验）</li>
 * </ol>
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN-B
 */
@Data
public class StockOutBo {

    /**
     * 库存行 ID（库存查询行主键，service 按此取 locationId + productId）。
     */
    @NotNull(message = "{stock.id.required}")
    private Long id;

    /**
     * 出库日期（默认当天；前端可改）。
     */
    @NotNull(message = "{stock.out.date.required}")
    private Date outDate;

    /**
     * 出库量（必填，&gt; 0；service 校验 ≤ location_stock.product_stock）。
     */
    @NotNull(message = "{stock.out.quantity.required}")
    @DecimalMin(value = "0.001", message = "{stock.out.quantity.positive}")
    private BigDecimal quantity;

    /**
     * 出库方式 / 去向（必填，{@code djs_stock_out_dest} 字典 value）。
     */
    @NotNull(message = "{stock.out.dest.required}")
    @Size(max = 32, message = "{stock.out.dest.size}")
    private String stockOutDest;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "{stock.remark.size}")
    private String remark;

}
