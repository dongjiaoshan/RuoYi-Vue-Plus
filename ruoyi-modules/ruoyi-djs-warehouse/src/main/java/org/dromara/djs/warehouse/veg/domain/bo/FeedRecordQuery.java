package org.dromara.djs.warehouse.veg.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 有机饲喂记录列表查询条件（WMS-FEED-RECORD-001，仓库-admin 行21）。
 *
 * @author djs
 * @since WMS-FEED-RECORD-001
 */
@Data
public class FeedRecordQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作物名称（模糊）。
     */
    private String cropName;

    /**
     * 提供位置（字典 djs_feed_type：veg_handle / warehouse，精确）。
     */
    private String feedType;

    /**
     * 起始日期（含，可空；yyyy-MM-dd）。
     */
    private Date dateFrom;

    /**
     * 截止日期（含，可空；yyyy-MM-dd）。
     */
    private Date dateTo;
}
