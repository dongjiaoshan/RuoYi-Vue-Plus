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
     *
     * <p>外购果蔬 / 包材等 product 维度领用必填；自产果蔬（{@code plotId} 非空）按地块维度领用时
     * 可空（库存按 plot 建账，无 product_id）—— 见 {@link #plotId}。{@code @NotNull} 已移除，
     * 改由 service 层校验「productId 与 plotId 至少其一」。</p>
     */
    private Long productId;

    /**
     * 地块 ID（可空）。
     *
     * <p>非空 = 自产果蔬「按地块维度」领用（步11 偏差修复 · 决策 a）：自产果蔬入库时库存按
     * {@code (plot_id, location)} 维度建账（无 product_id，见 {@code VegReceiveServiceImpl.insertPlotStockRow}），
     * 故领用也必须按 plot 维度扣减。service 此时走 {@code deductByPlotLocation}，并把 plotId 写入
     * pick_out 流水的 {@code plot_id}；同时按 plot→crop→{@code crop.related_product} 解析果蔬成品
     * product_id 写入流水（与 admin 打包 {@code belong_type='vegetable'} 统计 join 的契约）。</p>
     *
     * <p>为空 = 外购果蔬 / 包材等 product 维度领用（现有路径，不回归）。</p>
     */
    private Long plotId;

    /**
     * 库位 ID（可空）。
     *
     * <p>为空时 service 按 {@code productId} 解析默认库位（投喂等无库位语义场景，工人不选库位）；
     * 其他调用方传了 locationId 仍按传入值走，不受影响。{@code plotId} 维度领用时建议显式传库位
     * （自产果蔬常在 L0003/L0004/L0006），为空则按 plot 解析默认库位。</p>
     */
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
