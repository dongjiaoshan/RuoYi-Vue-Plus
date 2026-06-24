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

    /**
     * 选中篮子 ID（可空；= {@code location_stock.id}，snowflake，前端按 string 传）。
     *
     * <p>非空 = 「按源手选」退回（对齐领用，原型「仓库&gt;分拣发货&gt;物资领用」）：把退回量回补到用户选中的
     * 那一篮（猪肉耳号篮 / 自产果蔬地块篮）。service 走 by-batch 分支：今日额度仍按 product 校验，
     * 回补走 {@code addStockById(batchId)} + LIFO 减今天该篮 inhouse。为空 = 现状路径，不回归。</p>
     */
    private Long batchId;

    /**
     * 库位 ID（可空）。
     *
     * <p>为空时 service 按 {@code productId} 解析默认库位（投喂等无库位语义场景，工人不选库位）；
     * 其他调用方传了 locationId 仍按传入值走，不受影响。</p>
     */
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

    /**
     * 退回来源场景（可空；{@code warehouse} / {@code breed} / {@code plant}，FIX-WMS-FLOWDICT-003）。
     *
     * <p>与 {@code MatPickBo.sourceScene} 同语义：mp 各页按自己模块显式传，service 据此强制赋退回
     * {@code flow_type}：</p>
     * <ul>
     *   <li>{@code warehouse}（仓库打包领用退回，{@code matPack/*}）→ flow_type={@code prod_return_in}（生产退回）；</li>
     *   <li>{@code breed} / {@code plant}（养殖 / 种植领用退回，{@code matIssue/*}）→
     *       flow_type={@code pick_return_in}（领用退回）。</li>
     * </ul>
     *
     * <p>为空（存量 mp 调用未传）→ service 兜底 {@code pick_return_in}（领用退回，保持前一轮行为不回归）。</p>
     */
    private String sourceScene;

}
