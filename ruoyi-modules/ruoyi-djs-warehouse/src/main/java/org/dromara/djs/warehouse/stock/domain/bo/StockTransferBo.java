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
 * 库存查询行「猪肉转移」入参（WS13 / row143）。
 *
 * <p>admin 库存查询页：产品为猪肉（belong_type=pork）且库位为「猪肉鲜品库」的库存行，行操作「猪肉转移」
 * → 弹窗（当前库存只读 / 转移日期 / 转移量）提交，把该行库存从「猪肉鲜品库」转移到「冻品库」。</p>
 *
 * <p>Service 同一 {@code @Transactional}：</p>
 * <ol>
 *   <li>按 {@link #getId()} 取源库存行，校验其库位为「猪肉鲜品库」、产品业态为 pork、转移量 ≤ 当前库存；</li>
 *   <li>源侧：按行 id 原子扣减 + INSERT 转移出库流水（{@code flow_type=transfer_out}，去向记冻品库）；</li>
 *   <li>目标侧：冻品库同产品 UPSERT 加库存 + INSERT 转移入库流水（{@code flow_type=transfer_in}，来源记猪肉鲜品库）。</li>
 * </ol>
 *
 * @author djs
 * @since WS13
 */
@Data
public class StockTransferBo {

    /**
     * 源库存篮 id 组，<b>先进先出序</b>（{@code t_warehouse_location_stock.id}）。
     *
     * <p>与产品出库同一口径（V6 row223 / D-0068）：库存查询一行可能由多个篮合并而来，
     * 转移量按本列表顺序跨篮扣减。猪肉行通常带耳号与白条流水号、天然只有一个篮，
     * 但口径统一才不会哪天多出一个组就悄悄只转了第一篮。</p>
     */
    @NotEmpty(message = "{stock.id.required}")
    private List<Long> stockIds;

    /**
     * 转移日期（默认当天；前端可改）。
     */
    @NotNull(message = "{stock.transfer.date.required}")
    private Date transferDate;

    /**
     * 转移量（必填，&gt; 0；service 校验 ≤ 源库存行 product_stock）。
     */
    @NotNull(message = "{stock.transfer.quantity.required}")
    @DecimalMin(value = "0.001", message = "{stock.transfer.quantity.positive}")
    @Digits(integer = 9, fraction = 3, message = "{stock.transfer.quantity.scale}")
    private BigDecimal quantity;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "{stock.remark.size}")
    private String remark;

}
