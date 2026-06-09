package org.dromara.djs.store.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店看板 - 4 业态当日销售分布单行（FIX-MGMT-MP-STORE-001）。
 *
 * <p>来源：{@code t_store_sale_record} JOIN {@code t_warehouse_product_info} 按
 * {@code belong_type} 聚合。产品 6 belong_type（pork/vegetable/white_bar/dry_good/egg/gift_box）
 * + null 由 service 归并为 4 业态对齐 {@code djs_demand_product_type} 字典（white_bar / vegetable /
 * gift_box / other）：</p>
 * <ul>
 *   <li>{@code pork} + {@code white_bar} → 猪肉（belongType=white_bar，对齐字典）</li>
 *   <li>{@code vegetable} → 蔬菜（belongType=vegetable）</li>
 *   <li>{@code gift_box} → 礼盒（belongType=gift_box）</li>
 *   <li>{@code dry_good} + {@code egg} + null → 其他（belongType=other）</li>
 * </ul>
 *
 * <p>mapper 阶段 {@code belongType} 承载原始产品 belong_type（可空）；service 归并后改写为
 * 4 业态 key。返给前端固定 4 行，无销售业态补 0。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-STORE-001
 */
@Data
public class StoreSaleCategoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业态 key（4 业态：white_bar=猪肉 / vegetable=蔬菜 / gift_box=礼盒 / other=其他，
     * 对齐 {@code djs_demand_product_type} 字典）。
     */
    private String belongType;

    /**
     * 业态中文名（mp 硬编码中文文案后端给：猪肉 / 蔬菜 / 礼盒 / 其他）。
     */
    private String categoryName;

    /**
     * 当日销售额（元，无销售返 0）。
     */
    private BigDecimal saleAmount;

    /**
     * 当日订单数（无销售返 0）。
     */
    private Integer orderCount;

    /**
     * 当月累计销售额（元，无累计返 0）。
     */
    private BigDecimal monthAccum;

}
