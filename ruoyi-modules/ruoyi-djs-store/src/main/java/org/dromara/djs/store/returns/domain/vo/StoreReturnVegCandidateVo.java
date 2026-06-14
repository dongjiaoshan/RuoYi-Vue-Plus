package org.dromara.djs.store.returns.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 退回操作「果蔬产品」tab 候选行 VO。
 *
 * <p>对齐原型「可退回的果蔬商品 = 当天已确认到店的需求产品」：取
 * {@code t_warehouse_demand_manage} 中该门店、当天、{@code product_type='vegetable'}、
 * 已确认（{@code demand_status='CONFIRMED'}）且已门店收货（{@code received_time IS NOT NULL}）
 * 的需求，按 {@code product_id} 去重。{@code productId} 为产品雪花主键，前端按行录入退回量 +
 * 退回重量后回传供 {@code batchCreate} 校验入库。</p>
 *
 * @author djs
 * @since STORE-RETURN-VEG-CANDIDATE
 */
@Data
public class StoreReturnVegCandidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品雪花 ID（= t_warehouse_product_info.id，提交退回时作 productId）。
     */
    private Long productId;

    /**
     * 产品名称（需求冗余 product_name）。
     */
    private String productName;

    /**
     * 产品单位（kg / 把 / 份 等，需求冗余 product_unit）。
     */
    private String productUnit;

}
