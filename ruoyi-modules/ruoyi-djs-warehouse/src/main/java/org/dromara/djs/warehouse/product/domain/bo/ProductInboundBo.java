package org.dromara.djs.warehouse.product.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品配置「产品入库」入参 BO（DJS-FIX-WMS-RALN-B）。
 *
 * <p>原型在 admin 商品配置列表行画了「产品入库」操作：录入 产品 / 库位 / 数量 →
 * 写一条入库 stock_flow（{@code flow_type=purchase_in / inout_type=IN}）+ 增 location_stock。</p>
 *
 * <p>雪花 ID 全链路保持 String（前端不 Number()，防 19 位精度丢失），后端在 service 显式 parse。</p>
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN-B
 */
@Data
public class ProductInboundBo {

    /**
     * 产品 ID（snowflake string）。
     */
    @NotNull(message = "{product.inbound.product.required}")
    private Long productId;

    /**
     * 入库库位 ID（snowflake string；FK → t_warehouse_location_info.id）。
     */
    @NotNull(message = "{product.inbound.location.required}")
    private Long locationId;

    /**
     * 入库数量（正数）。
     */
    @NotNull(message = "{product.inbound.quantity.required}")
    @Positive(message = "{product.inbound.quantity.positive}")
    private BigDecimal quantity;

    /**
     * 备注（可选）。
     */
    private String remark;

}
