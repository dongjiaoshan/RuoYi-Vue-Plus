package org.dromara.djs.warehouse.vegout.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 毛菜间出库单列表查询入参（admin row187）。
 *
 * @author djs
 */
@Data
public class VegOutQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 出库日期 ≥（默认由前端置为近一个月起点）。 */
    private Date beginDate;

    /** 出库日期 ≤。 */
    private Date endDate;

    /** 出库去向（字典 djs_stock_out_dest）。 */
    private String outDest;

    /** 操作人 id。 */
    private Long operatorId;
}
