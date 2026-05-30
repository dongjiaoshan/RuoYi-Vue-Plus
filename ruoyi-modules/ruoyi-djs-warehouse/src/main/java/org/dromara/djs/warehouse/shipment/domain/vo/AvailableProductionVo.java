package org.dromara.djs.warehouse.shipment.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 待清点产品 VO（{@code GET /applet/warehouse/ship/productions}）。
 *
 * <p>mp 工人按 demand_id 拉本需求所有 {@code is_delivery_check=0} 的产品。展示
 * produceNo / productName / produceQuantity / produceLocation 等关键字段。</p>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Data
public class AvailableProductionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String produceNo;

    private Date produceDate;

    private Long productId;

    /** 产品名称（service 层 JOIN product_info 填充）。 */
    private String productName;

    private BigDecimal produceQuantity;

    private Long produceLocation;

    /** 库位名称（service 层 JOIN location_info 填充）。 */
    private String produceLocationName;

    private String earNo;

    private Long whiteBarId;

    private Long demandId;
}
