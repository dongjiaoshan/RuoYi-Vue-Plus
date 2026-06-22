package org.dromara.djs.warehouse.purchase.domain.query;

import lombok.Data;

/**
 * 采购入库「商品维度」列表查询参数（WMS-OUTSOURCE-001 需求1）。
 *
 * <p>以 {@code product_type=2}（外购商品）为骨架的聚合分页查询参数，区别于
 * {@link PurchaseInQuery}（流水维度）。</p>
 *
 * @author djs
 * @since WMS-OUTSOURCE-001
 */
@Data
public class PurchaseInProductQuery {

    /**
     * 商品名称（LIKE 模糊匹配）。
     */
    private String productName;

    /**
     * 商品分类（字典 {@code djs_buy_class} 的 value，精确匹配）。
     */
    private String buyClass;

    /**
     * 供应商 ID（精确匹配，snowflake；前端按 string 传，Long 接收解析）。
     */
    private Long supplierId;

    /**
     * 存储库位 ID（精确匹配；商品 {@code store_location_id} 为 CSV 多库位，用 FIND_IN_SET 命中）。
     */
    private Long storeLocationId;

}
