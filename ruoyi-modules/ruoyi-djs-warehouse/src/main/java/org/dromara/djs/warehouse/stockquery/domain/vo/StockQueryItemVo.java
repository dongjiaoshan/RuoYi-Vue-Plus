package org.dromara.djs.warehouse.stockquery.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 库存查询行 VO（FIX-WMS-MP-STOCKQUERY-001，原型图27）。
 *
 * <p>产品 / 库位 / 库存量 / 单位 4 列。{@code locationName} 由 service 层 JOIN 回填
 * （沿 LocationStockServiceImpl.fillLocationNames 模式）。{@code id / productId / locationId}
 * 跨层 snowflake string（前端不 Number）。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-STOCKQUERY-001
 */
@Data
public class StockQueryItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库存行主键。
     */
    private Long id;

    /**
     * 产品 ID。
     */
    private Long productId;

    /**
     * 产品名称。
     */
    private String productName;

    /**
     * 库位 ID。
     */
    private Long locationId;

    /**
     * 库位名称（service JOIN 回填）。
     */
    private String locationName;

    /**
     * 当前库存量。
     */
    private BigDecimal productStock;

    /**
     * 单位。
     */
    private String productUnit;

}
