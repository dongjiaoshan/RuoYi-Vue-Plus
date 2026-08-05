package org.dromara.djs.warehouse.veg.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 「汇总行 × 产品」采收净额中间行（V6 row18，不持久化）。
 *
 * @author djs
 */
@Data
public class HandleProductNetRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 汇总行 ID（= t_warehouse_vegetable_handle.id）。 */
    private Long handleId;

    /** 产品 ID；存量未选产品的流水为 null。 */
    private Long productId;

    /** 采收累计 − 处理累计（kg）。 */
    private BigDecimal netWeight;
}
