package org.dromara.djs.store.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店看板 - 当月 TOP10 产品排行单行（STR-DASH-001）。
 *
 * <p>来源：{@code t_store_sale_record} 按 {@code product_id} + {@code product_name}
 * 聚合 SUM(sale_amount) / SUM(sale_qty)，当月范围，按销售额倒序 LIMIT 10。</p>
 *
 * <p>{@code productId} 是 snowflake（Jackson 默认序列化 string），前端 TS interface 一律 string。</p>
 *
 * @author djs
 * @since STR-DASH-001
 */
@Data
public class StoreProductRankItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品 ID（snowflake，序列化为 string）。
     */
    private Long productId;

    /**
     * 产品名称。
     */
    private String productName;

    /**
     * 当月销售额合计（元）。
     */
    private BigDecimal saleAmount;

    /**
     * 当月销售数量合计（kg 或 件）。
     */
    private BigDecimal saleQty;

    /**
     * 当月订单数（{@code COUNT(*)}，原型「当月热门产品排行 TOP10（按订单数）」横向柱）。
     */
    private Integer orderCount;

}
