package org.dromara.djs.warehouse.shipment.domain.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 发货流水查询 query（admin 列表用）。
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Data
public class ShipmentQuery {

    /** 发货单号模糊匹配。 */
    private String shipmentNo;

    /** 关联需求 ID 精确匹配。 */
    private Long demandId;

    /** 业态精确匹配（white_bar/vegetable/gift_box/other）。 */
    private String productType;

    /** 目的门店精确匹配。 */
    private Long storeId;

    /** 发货状态精确匹配。 */
    private String shipmentStatus;

    /** 发货日期下界（含）。 */
    private LocalDate shipDateFrom;

    /** 发货日期上界（含）。 */
    private LocalDate shipDateTo;

    /** 清点员精确匹配。 */
    private Long checkerId;
}
