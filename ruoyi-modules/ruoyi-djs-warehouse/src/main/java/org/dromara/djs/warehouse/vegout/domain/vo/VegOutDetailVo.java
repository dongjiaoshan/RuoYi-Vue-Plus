package org.dromara.djs.warehouse.vegout.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 毛菜间出库单明细行（admin row187 详情弹框）。
 *
 * @author djs
 */
@Data
public class VegOutDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品名称。 */
    private String productName;

    /** 产品规格。 */
    private String productSpec;

    /** 出库重量（kg）。 */
    private BigDecimal outWeight;

    /** 地块编号（可空）。 */
    private String plotCode;
}
