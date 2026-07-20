package org.dromara.djs.warehouse.burn.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 燎毛记录查询参数（admin 列表筛选 + mp 我的列表）。
 *
 * @author djs
 * @since WMS-PIG-001
 */
@Data
public class PigBurnRecordQuery {

    /**
     * 耳号精确匹配。
     */
    private String earNo;

    /**
     * 燎毛单号精确匹配。
     */
    private String burnId;

    /**
     * 状态字典 {@code djs_burn_status}（pending / done）。
     */
    private String burnStatus;

    /**
     * 操作人（mp 端"我的"查询自动填）。
     */
    private Long operatorId;

    /**
     * 燎毛时间起。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date burnTimeFrom;

    /**
     * 燎毛时间止。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date burnTimeTo;

}
