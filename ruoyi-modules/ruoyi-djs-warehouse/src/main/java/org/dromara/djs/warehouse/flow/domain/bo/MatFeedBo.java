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
