package org.dromara.djs.store.returns.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 退回操作「猪肉产品」tab 候选行 VO。
 *
 * <p>候选 = <b>该门店当日盘点台账</b>里「期初 + 入库 − 销售 − 赠送 &gt; 0」且业态属
 * {@code pork} / {@code white_bar} 的产品（甲方 row205；不减损坏）——<b>强门店相关</b>，
 * 与果蔬 / 其他产品两个 tab 同一套取数。{@code productId} 为产品雪花主键，
 * 前端按行录入退回重量后回传供 batchCreate 校验入库（提交闸与候选同口径）。</p>
 *
 * @author djs
 * @since STR-RETURN-PORK-CANDIDATE
 */
@Data
public class StoreReturnPorkCandidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品雪花 ID（= t_warehouse_product_info.id，提交退回时作 productId）。
     */
    private Long productId;

    /**
     * 产品名称（如 五花肉 / 大排 / 排骨）。
     */
    private String productName;

    /**
     * 产品单位（kg / 个 / 份 等）。
     * <p>猪肉产品(pork)=产品自身单位（份）；白条产品(white_bar)=对应产品原材料的单位（DENGBO-R11）。</p>
     */
    private String productUnit;

    /**
     * 产品子类（DENGBO-R11）：{@code pork}=猪肉产品（按份退回，显示退回量+单位+退回产品重量）
     * / {@code white_bar}=白条产品（按重量退货，单位取原材料单位）。两者候选来源同为门店当日盘点台账。
     */
    private String subCategory;

    /**
     * 归属类型（字典 {@code djs_belong_type}）。
     *
     * <p>row178：礼盒（{@code gift_box}）无法按单一原材料退回入库，服务端已从候选里剔除；
     * 本字段回传给前端做二次过滤，前后端同一判据。</p>
     */
    private String belongType;

    /**
     * 可退量（退回量上限，row205）= 门店当日盘点台账 {@code 期初 + 入库 − 销售 − 赠送}（不减损坏）。
     * <b>pork 与 white_bar 两个子类同一口径</b>，与果蔬 / 其他产品 tab 也一致。空 → 不封顶。
     */
    private BigDecimal arrivedQuantity;

    /**
     * 今日已退量（row119）：该门店该产品当日已提交的门店退仓量 {@code SUM(return_quantity)}。
     * 剩余可退 = {@code arrivedQuantity - returnedQuantity}，前端 el-input-number 以此作 {@code :max}，
     * 后端 {@code batchCreate} 用同一口径把关（前端 max 只是体验）。
     */
    private BigDecimal returnedQuantity;

}
