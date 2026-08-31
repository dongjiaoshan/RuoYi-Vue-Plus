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
 * <p>类名 ProductStockInBo（非 ProductInboundBo）以避开与
 * {@code selfcheck.domain.bo.ProductInboundBo} 的 MyBatis 类型别名（简单类名）冲突。</p>
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN-B
 */
@Data
public class ProductStockInBo {

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

    /**
     * 本次到货的供应商（V6-R141）。
     *
     * <p>只在采购入库弹框传：甲方原话「供应商调整为可以修改，根据实际进行选择」——
     * 同一个商品这次可能从张三进、下次从李四进，锁死在商品配置里那一个不符合实际。</p>
     *
     * <p>不传 / 传 null → 回落商品配置上的 {@code product_info.supplier_id}（商品配置入口的老行为不变）。
     * 只对外购商品（{@code product_type=2}）生效，自产品无供应商。</p>
     */
    private Long supplierId;

    /**
     * 首次采购入库自配置存储库位开关。
     *
     * <p>采购入库（商品维度）上下文置 {@code true}：若该商品尚未配置 {@code store_location_id}，
     * 则把本次入库库位回写为该商品的存储库位（首次入库 = 认领家库位），之后入库锁定到它。
     * 已配置存储库位的商品不覆盖。</p>
     *
     * <p>商品配置入库（product 维度）不传 / 传 {@code null}/{@code false} → 不回写，行为零变化。</p>
     */
    private Boolean autoConfigLocation;

}
