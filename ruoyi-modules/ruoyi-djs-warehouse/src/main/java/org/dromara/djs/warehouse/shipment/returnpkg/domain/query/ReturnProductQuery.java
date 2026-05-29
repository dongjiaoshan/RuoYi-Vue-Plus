package org.dromara.djs.warehouse.shipment.returnpkg.domain.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 退货管理查询 query。
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Data
public class ReturnProductQuery {

    /** 退货单号模糊。 */
    private String returnNo;

    /** 门店精确。 */
    private Long storeId;

    /** 产品精确。 */
    private Long productId;

    /** 是否已确认 1/0。 */
    private Integer isConfirm;

    /** 退货方向精确。 */
    private String returnDirection;

    /** 退货状态精确。 */
    private String returnStatus;

    /** 申请日期下界（含）。 */
    private LocalDate applyDateFrom;

    /** 申请日期上界（含）。 */
    private LocalDate applyDateTo;
}
