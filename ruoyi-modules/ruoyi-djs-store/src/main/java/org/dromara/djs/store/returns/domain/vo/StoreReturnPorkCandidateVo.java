package org.dromara.djs.store.returns.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

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
     * 产品单位（kg / 个 等）。
     */
    private String productUnit;

}
