package org.dromara.djs.store.returns.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 退回操作「果蔬产品」tab 候选行 VO。
 *
 * <p>候选 = 门店当日盘点台账里「期初 + 入库 − 销售 − 赠送 &gt; 0」的果蔬业态产品
 * （甲方 row205；不减损坏）。{@code productId} 为产品雪花主键，前端按行录入退回量 +
 * 退回重量后回传供 {@code batchCreate} 校验入库（提交闸与候选同口径）。</p>
 *
 * <p>本 VO 同时被「其他产品」tab（干货 / 蛋类 / 其他）复用，口径完全一致。</p>
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
     * 归属类型（字典 {@code djs_belong_type}）。
     *
     * <p>row178：礼盒（{@code gift_box}）无法按单一原材料退回入库，服务端已从候选里剔除；
     * 本字段回传给前端做二次过滤，前后端同一判据。</p>
     */
    private String belongType;

    /**
     * 可退量（退回量上限，row205）= 门店当日盘点台账 {@code 期初 + 入库 − 销售 − 赠送}（不减损坏）。
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
