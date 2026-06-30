package org.dromara.djs.warehouse.cut.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

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
     * 状态多选（R70 分割状态下拉多选）。非空时按 IN 过滤，优先于单值 cutStatus。
     */
    private List<String> cutStatuses;

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
