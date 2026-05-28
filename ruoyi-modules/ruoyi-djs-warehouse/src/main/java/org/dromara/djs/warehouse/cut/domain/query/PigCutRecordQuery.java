package org.dromara.djs.warehouse.cut.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 分割记录查询参数（admin 列表筛选 + mp 我的列表）。
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Data
public class PigCutRecordQuery {

    /**
     * 分割单号精确匹配。
     */
    private String cutId;

    /**
     * 白条编号精确匹配。
     */
    private String barId;

    /**
     * 耳号精确匹配。
     */
    private String earNo;

    /**
     * 状态（pending_pickup / picked / cutting / done）。
     */
    private String cutStatus;

    /**
     * 操作人（mp 端"我的"查询自动填）。
     */
    private Long operatorId;

    /**
     * 领用时间起。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pickupTimeFrom;

    /**
     * 领用时间止。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pickupTimeTo;

}
