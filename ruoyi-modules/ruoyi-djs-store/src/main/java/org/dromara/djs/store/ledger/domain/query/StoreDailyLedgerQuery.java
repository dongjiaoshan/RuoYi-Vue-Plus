package org.dromara.djs.store.ledger.domain.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 门店经营流水盘点查询 query（STORE-LEDGER-001）。
 *
 * @author djs
 * @since STORE-LEDGER-001
 */
@Data
public class StoreDailyLedgerQuery {

    /** 门店精确。 */
    private Long storeId;

    /** 产品精确（产品盘点历史用）。 */
    private Long productId;

    /** 盘点日期精确（详情按 storeId + ledgerDate 取一组明细）。 */
    private LocalDate ledgerDate;

    /** 盘点日期下界（含）。 */
    private LocalDate ledgerDateFrom;

    /** 盘点日期上界（含）。 */
    private LocalDate ledgerDateTo;
}
