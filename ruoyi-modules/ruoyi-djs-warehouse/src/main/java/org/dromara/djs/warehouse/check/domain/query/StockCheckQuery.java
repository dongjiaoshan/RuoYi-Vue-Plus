package org.dromara.djs.warehouse.check.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 盘点查询参数（admin 盘点单列表筛选 + mp 明细 line 查询）。
 *
 * @author djs
 * @since WMS-STOCK-001
 */
@Data
public class StockCheckQuery {

    /**
     * 盘点单业务码精确匹配。
     */
    private String checkId;

    /**
     * 库位 ID 精确匹配。
     */
    private Long locationId;

    /**
     * 盘点单状态字典 {@code djs_check_status}（draft / in_progress / completed）。
     */
    private String checkStatus;

    /**
     * 盘点人姓名（按发起人 {@code sys_user.nick_name} 模糊匹配；admin 列表筛选）。
     */
    private String checkByName;

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
