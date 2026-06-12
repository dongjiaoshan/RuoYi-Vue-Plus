package org.dromara.djs.store.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店看板 - 近 10 日趋势单点（STR-DASH-001）。
 *
 * <p>来源：{@code t_store_sale_record} 按 {@code sale_date} 分组，
 * COUNT(*) 订单数 + SUM(sale_amount) 销售额，近 10 日窗口。</p>
 *
 * <p>{@code avgPrice}（客单价 = saleAmount / orderCount）在 service 计算，
 * 订单数为 0 时返 ZERO，避免除零。</p>
 *
 * @author djs
 * @since STR-DASH-001
 */
@Data
public class StoreTrendPointVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日期（'YYYY-MM-DD' 字符串）。
     */
    private String date;

    /**
     * 当日订单数。
     */
    private Integer orderCount;

    /**
     * 当日销售额（元）。
     */
    private BigDecimal saleAmount;

    /**
     * 当日销售量（{@code SUM(sale_qty)}，原型「销售量与退货量趋势」柱）。
     */
    private BigDecimal saleQty;

    /**
     * 当日退货量（{@code t_store_return} 按 {@code return_date} 当日聚合 {@code SUM(return_quantity)}，
     * 原型「销售量与退货量趋势」第二根柱；service merge 进同日趋势点，无退货日补 0）。
     */
    private BigDecimal returnQty;

    /**
     * 当日客单价（销售额 / 订单数，service 计算；订单数 0 时为 0）。
     */
    private BigDecimal avgPrice;

}
