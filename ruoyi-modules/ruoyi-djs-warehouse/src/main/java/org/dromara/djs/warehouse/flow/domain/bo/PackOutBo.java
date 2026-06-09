package org.dromara.djs.warehouse.flow.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 包材库「产品出库」入参（mp 端 POST {@code /applet/warehouse/packing/out}，原型图54 per 项 4 动作之一）。
 *
 * <p>Service 同事务（参 {@code MatFlowServiceImpl#pick} 范式）：</p>
 * <ol>
 *   <li>INSERT stock_flow（{@code flow_type='pick_out'}, {@code inout_type='OT'}, {@code change_num=-quantity}）</li>
 *   <li>UPDATE location_stock 行锁扣减 + 数量校验（{@code product_stock >= quantity}）；affected=0 → 库存不足回滚</li>
 * </ol>
 *
 * <p>出库去向沿用 {@code stock_out_dest}（可空，包材出库默认内部消耗，不强制工人选）。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-MATISSUE-001
 */
@Data
public class PackOutBo {

    /**
     * 产品 ID（包材）。
     */
    @NotNull(message = "产品 ID 不能为空")
    private Long productId;

    /**
     * 库位 ID（可空，service 按 productId 解析默认库位）。
     */
    private Long locationId;

    /**
     * 出库数量（必填，&gt; 0；service 校验 ≤ location_stock.product_stock）。
     */
    @NotNull(message = "出库数量不能为空")
    @DecimalMin(value = "0.001", message = "出库数量必须大于 0")
    private BigDecimal quantity;

    /**
     * 出库去向（可选，djs_stock_out_dest 字典 value；为空时不写）。
     */
    @Size(max = 32, message = "出库去向过长")
    private String stockOutDest;

    /**
     * 凭证图 OSS IDs CSV（可选）。
     */
    @Size(max = 500, message = "凭证图过多")
    private String proofOssIds;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "备注最多 500 字")
    private String remark;

}
