package org.dromara.djs.warehouse.product.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品详情「业务流水」子表行 VO（DJS-FIX-WMS-RALN-B）。
 *
 * <p>数据源 {@code t_warehouse_stock_flow}（按 productId 过滤）。原型列：
 * 业务日期 / 业务类型(入库·领用出库·后台出库) / 业务量 / 业务单位。</p>
 *
 * <p>{@code bizType} 由 flow 字段派生：</p>
 * <ul>
 *   <li>{@code inout_type='IN'} → {@code in_stock}（入库）</li>
 *   <li>{@code inout_type='OT' AND flow_type='pick_out'} → {@code pick_out}（领用出库）</li>
 *   <li>其余 {@code inout_type='OT'} → {@code backend_out}（后台出库）</li>
 * </ul>
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN-B
 */
@Data
public class ProductFlowRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流水记录 ID（行 key）。
     */
    private Long id;

    /**
     * 业务日期。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date bizDate;

    /**
     * 业务类型：{@code in_stock}=入库 / {@code pick_out}=领用出库 / {@code backend_out}=后台出库。
     */
    private String bizType;

    /**
     * 业务量（正数）。
     */
    private BigDecimal bizNum;

    /**
     * 业务单位（按 product_info.product_unit 回填）。
     */
    private String bizUnit;

}
