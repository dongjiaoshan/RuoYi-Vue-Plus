package org.dromara.djs.store.returns.domain.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 门店退回管理查询 query（STR-RETURN-001）。
 *
 * @author djs
 * @since STR-RETURN-001
 */
@Data
public class StoreReturnQuery {

    /** 退回单号模糊。 */
    private String returnNo;

    /** 门店精确。 */
    private Long storeId;

    /** 产品精确。 */
    private Long productId;

    /** 退回方向精确。 */
    private String returnDirection;

    /** 退货状态精确（djs_store_return_status：pending/received）。 */
    private String returnStatus;

    /** 退回日期下界（含）。 */
    private LocalDate returnDateFrom;

    /** 退回日期上界（含）。 */
    private LocalDate returnDateTo;
}
