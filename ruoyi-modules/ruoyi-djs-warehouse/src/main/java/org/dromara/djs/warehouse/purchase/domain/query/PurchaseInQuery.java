package org.dromara.djs.warehouse.purchase.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 采购入库查询参数（D9 hotfix admin 端列表）。
 *
 * <p>本质是 stock_flow 上 {@code flow_type='purchase_in'} 这一类流水的子集查询，
 * 后端 service 强制把 {@code flowType} 锁为 {@code purchase_in}，前端无需传。</p>
 *
 * @author djs
 * @since D9 hotfix purchase-in
 */
@Data
public class PurchaseInQuery {

    /**
     * 产品 ID 精确匹配。
     */
    private Long productId;

    /**
     * 库位 ID 精确匹配（DDL 列名 {@code warehouse_id}，实为 location FK）。
     */
    private Long locationId;

    /**
     * 业务时间起。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dateFrom;

    /**
     * 业务时间止。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dateTo;

}
