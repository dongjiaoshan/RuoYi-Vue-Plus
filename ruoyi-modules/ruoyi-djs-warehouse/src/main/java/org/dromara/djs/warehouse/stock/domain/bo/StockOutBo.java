package org.dromara.djs.warehouse.stock.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

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
     * 这一次出库作用的库存篮 id 组，<b>先进先出序</b>（{@code t_warehouse_location_stock.id}）。
     *
     * <p>V6 row223 / D-0068 起库存查询一行不再等于一个篮：同 (产品, 库位, 耳号, 地块, 三期, 白条流水号)
     * 的多个篮合并成一行显示，出库时整组带上来、服务端按本列表顺序跨篮扣减
     * （「出库先进先出自动，工人不再手选哪一篮」）。只出一个篮的调用方传单元素列表。</p>
     *
     * <p>只收篮 id、不收 location/product：那两个前端可篡改，篮 id 一查就能反解出真实库位与产品。</p>
     */
    @NotEmpty(message = "{stock.id.required}")
    private List<Long> stockIds;

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
    @Digits(integer = 9, fraction = 3, message = "{stock.out.quantity.scale}")
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
