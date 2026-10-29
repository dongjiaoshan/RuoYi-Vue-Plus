package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 订货单打印明细（V7-R8）：抬头（订货日期 / 订货门店 / 订货品类总数）+ 产品明细行。
 *
 * @author djs
 * @since V7-R8
 */
@Data
public class DemandPrintDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订货日期（= 需求日期）。 */
    private LocalDate demandDate;

    /** 门店 FK。 */
    private Long storeId;

    /** 门店名。 */
    private String storeName;

    /** 订货品类总数（= items 行数）。 */
    private Integer categoryCount;

    /** 明细行。 */
    private List<DemandPrintItemVo> items;
}
