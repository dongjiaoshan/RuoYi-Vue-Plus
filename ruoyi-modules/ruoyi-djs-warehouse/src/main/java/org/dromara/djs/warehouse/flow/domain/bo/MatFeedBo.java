package org.dromara.djs.warehouse.flow.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 仓库饲料饲喂入参（mp 端 POST {@code /applet/warehouse/mat/feed}）。
 *
 * <p>行64「饲喂来源②=仓库领用饲喂」/ 行55「果蔬产品多一个『饲料饲喂』操作」：工人在仓库直接把某产品
 * （果蔬 / 饲料等）饲喂掉。Service 同事务：</p>
 * <ol>
 *   <li>INSERT stock_flow(flow_type='feed_out', inout_type='OT', change_num=-quantity)</li>
 *   <li>{@code deductByProductLocation} 扣 location_stock（饲喂 = 不可逆消耗，同 loss 语义；
 *       affected==0 打 warn 不抛——账实倒挂留痕）</li>
 *   <li>INSERT t_warehouse_feed_log(feed_type='warehouse', feed_date=今天)</li>
 * </ol>
 *
 * @author djs
 */
@Data
public class MatFeedBo {

    @NotNull(message = "{mat.product_id.required}")
    private Long productId;

    /**
     * 选中篮子 ID（可空；= {@code location_stock.id}，snowflake，前端按 string 传）。
     *
     * <p>非空 = 「按源手选」饲喂（对齐领用 / 退回 / 损耗）：从用户选中的那一篮（自产果蔬地块篮）扣饲喂量。
     * service 走 by-batch 分支：命中自产果蔬地块篮 → 按 {@code (product, plot)} 校验今日领用剩余 + 剥离今天
     * 待打包 {@code product_inhouse}（与 {@code lossVegPlot} 对称），不再二次扣 {@code location_stock}。
     * 为空 = 产品级路径（包材 / 鸡蛋 / 干货等非果蔬物资），不回归。</p>
     */
    private Long batchId;

    /**
     * 自产果蔬地块 ID（可空；= {@code plot_info.id}，snowflake，前端按 string 传）。
     *
     * <p>与 {@link MatLossBo}/{@link MatReturnBo} 的地块标签对齐：产品级饲喂分支（{@code batchId} 空）时
     * 带 {@code plotId} 让「本行今日领用剩余」按 {@code (product, location, plot)} 篮维度校验（自产果蔬带 plot_id
     * 的领用 / 退回 / 损耗 / 饲喂同口径），且饲喂 {@code feed_out} 流水写 {@code plot_id} 保持追溯口径一致。</p>
     */
    private Long plotId;

    @NotNull(message = "{mat.location_id.required}")
    private Long locationId;

    @NotNull(message = "{mat.quantity.required}")
    @DecimalMin(value = "0.001", message = "{mat.quantity.positive}")
    private BigDecimal quantity;

    @Size(max = 500, message = "{mat.proof_oss_ids.size}")
    private String proofOssIds;

    @Size(max = 500, message = "{mat.remark.size}")
    private String remark;

    /**
     * 记录人 user_id（mp 饲喂弹层「记录人」选了用所选、代他人登记，否则取当前登录人兜底）。
     */
    private Long operatorId;

}
