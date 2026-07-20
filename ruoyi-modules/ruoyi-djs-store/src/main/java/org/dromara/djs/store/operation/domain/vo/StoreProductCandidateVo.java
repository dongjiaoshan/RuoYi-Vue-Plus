package org.dromara.djs.store.operation.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * el-transfer 左侧候选 SKU VO（STR-OP-001）。
 *
 * <p>从 {@code t_warehouse_product_info} 取「在售」产品供 admin 产品关联页左侧选择；
 * {@code id} 为产品雪花主键（与 {@code StoreProductRelation.productId} 对应）。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Data
public class StoreProductCandidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品雪花 ID（= StoreProductRelation.productId）。
     */
    private Long id;

    /**
     * 产品业务码。
     */
    private String productCode;

    /**
     * 产品名称。
     */
    private String productName;

    /**
     * 产品单位。
     */
    private String productUnit;

    /**
     * 产品规格。
     */
    private String productSpec;

}
