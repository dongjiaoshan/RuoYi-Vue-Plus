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

    /**
     * 选中篮子 ID（可空；= {@code location_stock.id}，snowflake，前端按 string 传）。
     *
     * <p>非空 = 「按源手选」损耗（对齐领用，原型「仓库&gt;分拣发货&gt;物资领用」）：从用户选中的那一篮
     * （猪肉耳号篮 / 自产果蔬地块篮）扣损耗量。service 走 by-batch 分支：今日额度仍按 product 校验，
     * 扣减走 {@code deductStockById(batchId)}（affected==0 打 warn 不抛，与现状 loss 语义一致）。
     * 为空 = 现状路径，不回归。</p>
     */
    private Long batchId;

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
     * 记录人 user_id（可空）。
     *
     * <p>mp 损耗弹层「记录人」字段：默认当前登录人、可改选仓库部门人员（EmployeePicker）。
     * 非空 = 代他人登记损耗，写入 {@code stock_flow.operator_id} + {@code loss_flow.operator_id}
     * （管理者审计该损耗归属谁）；为空 = service 取当前登录人（{@code LoginHelper.getUserId()}）兜底
     * （现状行为，老调用方不传不回归）。snowflake，前端按 string 传。</p>
     */
    private Long operatorId;

}
