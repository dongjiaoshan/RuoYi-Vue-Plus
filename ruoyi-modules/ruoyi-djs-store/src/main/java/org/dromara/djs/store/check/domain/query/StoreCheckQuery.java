package org.dromara.djs.store.check.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 门店盘点查询参数（admin 盘点单列表筛选 + 明细 line 查询）。
 *
 * @author djs
 * @since STR-STOCK-001
 */
@Data
public class StoreCheckQuery {

    /**
     * 盘点单业务码精确匹配。
     */
    private String checkId;

    /**
     * 门店 ID 精确匹配。
     */
    private Long storeId;

    /**
     * 盘点单状态字典 {@code djs_check_status}（draft / in_progress / completed）。
     */
    private String checkStatus;

    /**
     * 盘点日期起。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date checkDateFrom;

    /**
     * 盘点日期止。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date checkDateTo;

}
