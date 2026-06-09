package org.dromara.djs.warehouse.pigbuy.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 外购猪只到货查询参数（mp「我的到货」/ 外购待处理列表）。
 *
 * @author djs
 * @since FIX-WMS-MP-PIGBUY-001
 */
@Data
public class PigPurchaseQuery {

    /**
     * 来源类型字典 {@code djs_pig_source}（live / white_bar）。
     */
    private String sourceType;

    /**
     * 处理状态字典 {@code djs_pig_purchase_status}（pending / done）。
     */
    private String purchaseStatus;

    /**
     * 登记人（mp「我的」查询自动填）。
     */
    private Long operatorId;

    /**
     * 到货时间起。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date arriveTimeFrom;

    /**
     * 到货时间止。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date arriveTimeTo;

}
