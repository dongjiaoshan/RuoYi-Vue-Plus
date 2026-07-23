package org.dromara.djs.store.returns.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 退回操作「猪肉产品」tab 候选行 VO。
 *
 * <p>对齐原型「可退回的猪肉商品列表」固定展示：取 {@code t_warehouse_product_info} 中
 * {@code belong_type IN ('pork','white_bar')} 的产品，与门店关联无关。{@code productId}
 * 为产品雪花主键，前端按行录入退回重量后回传供 batchCreate 校验入库。</p>
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
     * 产品子类（DENGBO-R11）：{@code pork}=猪肉产品（到店成品，按份退回，显示退回量+单位+退回产品重量）
     * / {@code white_bar}=白条产品（djs_white_bar_return_product 字典，按重量退货，单位取原材料单位）。
     */
    private String subCategory;

    /**
     * 到店量（退回量上限，row40）。口径按子类分流：
     * <ul>
     *   <li>猪肉产品(pork,按份)：当日到店该产品需求订购份数 {@code SUM(demand_quantity)}；</li>
     *   <li>材料外售原材料(kg)：对应成品当日到店重量（kg）；</li>
     *   <li>白条产品(white_bar,kg)：当日到店白条总重（kg，池化上限）。</li>
     * </ul>
     * 前端 el-input-number 以此作 {@code :max}；空 → 不封顶。
     */
    private BigDecimal arrivedQuantity;

}
