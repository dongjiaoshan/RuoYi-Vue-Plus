package org.dromara.djs.warehouse.pack.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 批量「各产品各门店未发货需求份数」的扁平行（打包录入卡片网格「需求量」批量聚合用）。
 *
 * <p>与 {@link StoreDemandCopiesVo} 口径完全一致（{@code SUM(GREATEST(demand_quantity - shipped_count, 0))}），
 * 仅多带 {@code productId} 维度，便于 service 按产品分组成 {@code Map<productId, List<StoreDemandCopiesVo>>}。
 * 单产品端点仍用 {@link StoreDemandCopiesVo}（卡片选中看分门店份数）。</p>
 *
 * @author djs
 */
@Data
public class StoreDemandCopiesRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 ID（FK → t_warehouse_product_info.id；分组键）。 */
    private Long productId;

    /** 门店 ID（FK → t_md_store.id）。 */
    private Long storeId;

    /** 门店名称。 */
    private String storeName;

    /** 该产品在该门店的未发货需求量（份数 = SUM(GREATEST(demand_quantity - shipped_count, 0))）。 */
    private BigDecimal copies;
}
