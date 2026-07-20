package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 某产品「按门店聚合需求量」明细行 VO（D-FIX-24 决策 #8）。
 *
 * <p>供需求管理列表「详情」弹窗：展示某产品被哪几家门店需求、各门店需求量。
 * 按 product_id 聚合（非取消单），一行 = 一门店。</p>
 *
 * @author djs
 * @since D-FIX-24
 */
@Data
public class DemandProductStoreDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 门店 FK（{@code t_md_store.id}）。 */
    private Long storeId;

    /** 门店名（JOIN {@code t_md_store} 回填）。 */
    private String storeName;

    /** 该门店对本产品的需求量合计。 */
    private BigDecimal demandQuantity;

    /** 该门店本产品需求单数。 */
    private Integer demandCount;
}
