package org.dromara.djs.store.returns.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

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

    /**
     * 到店量（退回量上限，row41）= 当日（今天+昨天两天累加）该产品需求订购份数 {@code SUM(demand_quantity)}。
     * 材料外售折叠为原材料时多成品共享同一原材料 → 汇总累加。空 → 不封顶。
     */
    private BigDecimal arrivedQuantity;

    /**
     * 今日已退量（row119）：该门店该产品当日已提交的门店退仓量 {@code SUM(return_quantity)}。
     * 剩余可退 = {@code arrivedQuantity - returnedQuantity}，前端 el-input-number 以此作 {@code :max}，
     * 后端 {@code batchCreate} 用同一口径把关（前端 max 只是体验）。
     */
    private BigDecimal returnedQuantity;

}
