package org.dromara.djs.warehouse.stockquery.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 统计聚合行 VO（FIX-WMS-MP-STOCKQUERY-001，退货 / 损耗统计 tab）。
 *
 * <p>按产品聚合（{@code group by product_id}）：产品名 + 单位 + 笔数 + 数量合计。退货统计走
 * {@code flow_type=return_in}，损耗统计走 {@code flow_type=loss}（djs_flow_type 字典权威 value）。
 * 数量取 {@code SUM(change_quantity)}（绝对值列，不含符号）。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-STOCKQUERY-001
 */
@Data
public class StockQueryStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品 ID。
     */
    private Long productId;

    /**
     * 产品名称（聚合 SQL JOIN product_info 取）。
     */
    private String productName;

    /**
     * 单位（聚合 SQL JOIN product_info 取）。
     */
    private String productUnit;

    /**
     * 笔数（COUNT）。
     */
    private Long flowCount;

    /**
     * 数量合计（SUM(change_quantity)，绝对值）。
     */
    private BigDecimal totalQuantity;

}
