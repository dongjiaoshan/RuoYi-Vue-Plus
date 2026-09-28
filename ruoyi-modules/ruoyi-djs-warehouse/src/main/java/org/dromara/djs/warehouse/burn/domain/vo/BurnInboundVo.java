package org.dromara.djs.warehouse.burn.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/** 燎毛入库事实；出库后仍保留，不以在库余额替代累计接收量。 */
@Data
public class BurnInboundVo {
    private Long barInfoId;
    private Long productId;
    private String whiteBarNo;
    private BigDecimal weight;
    private Date flowTime;
}
