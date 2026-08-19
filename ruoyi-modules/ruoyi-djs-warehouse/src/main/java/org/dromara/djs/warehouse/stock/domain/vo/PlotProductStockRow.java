package org.dromara.djs.warehouse.stock.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 「种植记录 × 产品」库存合计中间行（V6 row102 毛菜保鲜库剩余重量，不持久化）。
 *
 * <p>一条种植记录下的同一个产品在同一库位可能有多个篮子（每次采摘录入建一篮），本行是它们的合计。</p>
 *
 * <p><b>聚合键是种植记录不是地块</b>：同一地块可能同时有两条种植记录（两季 / 补录）、
 * 还可能有采摘活动直送进来的货，按地块聚合会把它们混成一个数，mp 头卡 {@code Σ remainWeight}
 * 随之翻倍（实测同地块两条记录各显 65、真实库存共 65）。</p>
 *
 * @author djs
 */
@Data
public class PlotProductStockRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 来源业务 id（= {@code t_warehouse_location_stock.source_biz_id} = 种植记录 id）。 */
    private Long sourceBizId;

    /** 产品 ID（= t_warehouse_product_info.id）。 */
    private Long productId;

    /** 该 (种植记录, 产品) 在指定库位的库存合计（kg）。 */
    private BigDecimal stockWeight;
}
