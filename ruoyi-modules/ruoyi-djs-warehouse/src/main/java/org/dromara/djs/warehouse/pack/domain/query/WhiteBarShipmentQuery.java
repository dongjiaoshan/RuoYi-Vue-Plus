package org.dromara.djs.warehouse.pack.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 白条发货记录查询参数（WS12 row133，admin「产品生产记录」下「白条发货记录」页）。
 *
 * <p>数据源 = {@code t_warehouse_product_production} 中 {@code belong_type='white_bar'} 的出库记录
 * （白条整只/半只出库到发货月台 或 仓库出库）。每行 = 一次白条出库事件。</p>
 *
 * <p>「出库方式」/「出库去向」由生产记录派生：{@code remark LIKE '后台出库%'} → 仓库出库
 * （去向解析自 remark「去向=xxx」段，字典 {@code djs_stock_out_dest}）；否则 → 发货月台
 * （去向 = ship_dock）。发货月台记录的门店由 {@code store_id} 标识。</p>
 *
 * @author djs
 * @since WS12
 */
@Data
public class WhiteBarShipmentQuery {

    /**
     * 发货日期起（按 {@code produce_date} 过滤，默认前端传近 1 月起）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date beginDate;

    /**
     * 发货日期止（按 {@code produce_date} 过滤）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    /**
     * 猪只耳号模糊匹配（{@code product_production.ear_no}）。
     */
    private String earNo;

    /**
     * 出库方式多选（字典 {@code djs_bar_out_method} 派生值：1=发货月台 / 3=仓库出库）。
     * 非空时按 IN 过滤（派生列，mapper 内下推）。
     */
    private List<String> outMethods;

    /**
     * 出库去向多选（字典 {@code djs_stock_out_dest}：ship_dock=发货月台 / mine=矿山 / kitchen=厨房 等）。
     * 非空时按 IN 过滤（派生列，mapper 内下推）。
     */
    private List<String> outDests;

}
