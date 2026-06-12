package org.dromara.djs.warehouse.product.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 产品详情「生产记录」子表行 VO（DJS-FIX-WMS-RALN-B）。
 *
 * <p>数据源 {@code t_warehouse_product_production}（按 productId 过滤）。原型列：
 * 生产日期 / 生产类型(生产·退货) / 生产量 / 生产单位 / 标准计重 / 生产重量 / 差异。</p>
 *
 * <ul>
 *   <li>{@code produceType}：生产链路记录恒为 {@code produce}（生产）；退货由 stock_flow
 *       {@code flow_type=return_in} 反向补一行 {@code return}（退货）。</li>
 *   <li>{@code standardWeight} / {@code diffWeight}：源表无独立列，按
 *       {@code product_spec} 解析标准计重失败时留 null；差异 = 生产重量 - 标准计重。</li>
 * </ul>
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN-B
 */
@Data
public class ProductProductionRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 记录 ID（生产记录 id 或退货流水 id，前缀去重不保证唯一，仅用作行 key）。
     */
    private Long id;

    /**
     * 生产日期。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date produceDate;

    /**
     * 生产类型：{@code produce}=生产 / {@code return}=退货。
     */
    private String produceType;

    /**
     * 生产量（件数 / 数量）。
     */
    private BigDecimal produceNum;

    /**
     * 生产单位。
     */
    private String produceUnit;

    /**
     * 标准计重（kg；无则 null）。
     */
    private BigDecimal standardWeight;

    /**
     * 生产重量（kg）。
     */
    private BigDecimal produceWeight;

    /**
     * 差异（生产重量 - 标准计重；任一为 null 则 null）。
     */
    private BigDecimal diffWeight;

}
