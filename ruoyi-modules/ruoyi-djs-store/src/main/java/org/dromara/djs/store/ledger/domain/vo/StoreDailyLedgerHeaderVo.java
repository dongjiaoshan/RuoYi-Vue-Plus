package org.dromara.djs.store.ledger.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店盘点单表头 VO（STORE-LEDGER-001 盘点列表，按 门店 + 盘点日期 分组聚合）。
 *
 * <p>列表一行 = 某门店某盘点日的一次盘点（含盘点人 / 盘点时间 / 产品行数）。点查看进详情看明细行。</p>
 *
 * @author djs
 * @since STORE-LEDGER-001
 */
@Data
public class StoreDailyLedgerHeaderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 门店 ID。 */
    private Long storeId;

    /** 门店名称（service 内存聚合填）。 */
    private String storeName;

    /** 盘点日期。 */
    private LocalDate ledgerDate;

    /** 盘点产品行数。 */
    private Integer lineCount;

    /** 盘点人 user_id。 */
    private Long operatorId;

    /** 盘点人姓名（USER_ID_TO_NICKNAME）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /** 盘点时间（该组最早创建时间）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkTime;
}
