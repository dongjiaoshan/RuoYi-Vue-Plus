package org.dromara.djs.store.operation.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 门店销售流水查询参数（STR-OP-001 admin 经营明细列表筛选）。
 *
 * @author djs
 * @since STR-OP-001
 */
@Data
public class StoreSaleRecordQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店 ID 精确匹配。
     */
    private Long storeId;

    /**
     * 产品 ID 精确匹配。
     */
    private Long productId;

    /**
     * 数据来源字典 {@code djs_sale_source}（manual / excel_import）。
     */
    private String source;

    /**
     * 销售日期起。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date saleDateFrom;

    /**
     * 销售日期止。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date saleDateTo;

}
