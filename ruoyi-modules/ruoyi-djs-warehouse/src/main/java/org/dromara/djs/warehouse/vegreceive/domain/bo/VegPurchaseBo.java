package org.dromara.djs.warehouse.vegreceive.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 外购果蔬收货入库提交入参（FIX-WMS-VEGRECEIVE-001，mp {@code submitPurchase}）。
 *
 * <p>对齐 mp 契约 {@code miniapp/src/api/warehouse/vegReceive.ts#VegPurchaseBody}：
 * {@code cropId / weight / supplier / locationId}（+ {@code operatorId?} 入库人）。</p>
 *
 * <p>语义说明：外购果蔬即"外购产品"（{@code product_info.product_type=2}），契约复用字段
 * {@code cropId} 承载<b>产品 ID</b>（dock 外购列表的 cropId 填的是 product.id）；service 内当 productId 处理。
 * {@code supplier} 承载供应商<b>业务短码</b>（mp SupplierPicker 选中后发 supplierCode），
 * service resolve 成 supplierId + supplierName。</p>
 *
 * <h3>Service 同事务</h3>
 * <ol>
 *   <li>校验产品（product_type=2 外购）存在；resolve supplier</li>
 *   <li>INSERT {@code t_warehouse_veg_receive}（receiveType=2）</li>
 *   <li>UPSERT {@code location_stock}（按 productId 维度行锁增量 / 不存在 INSERT，复用 LocationStockMapper）</li>
 *   <li>INSERT {@code stock_flow}（flow_type=veg_purchase_in, inout_type=IN, supplierId 关联）</li>
 * </ol>
 *
 * @author djs
 * @since FIX-WMS-VEGRECEIVE-001
 */
@Data
public class VegPurchaseBo {

    /**
     * 产品 ID（外购果蔬产品 FK → t_warehouse_product_info.id；契约字段名沿用 cropId）。
     */
    @NotNull(message = "{vegReceive.product_id.required}")
    private Long cropId;

    /**
     * 外购重量(kg)（必填，&gt; 0）。
     */
    @NotNull(message = "{vegReceive.weight.required}")
    @DecimalMin(value = "0.001", message = "{vegReceive.weight.positive}")
    private BigDecimal weight;

    /**
     * 供应商业务短码（mp SupplierPicker 选中值，如 G0004；service resolve 成 supplierId）。
     */
    @NotBlank(message = "{vegReceive.supplier.required}")
    private String supplier;

    /**
     * 入库位置 ID（FK → t_warehouse_location_info.id）。
     */
    @NotNull(message = "{vegReceive.location_id.required}")
    private Long locationId;

    /**
     * 猪只耳号（V6 row132，仅猪肉产品有意义，<b>非必填</b>）。
     *
     * <p>mp 侧候选项 = 今日白条出库的耳号（{@code /todayBarEarNos}）。填了则这批外购货进
     * <b>该耳号的库存篮</b>（{@code location_stock.ear_no}）并写进 {@code stock_flow.ear_no}，
     * 不并入产品的通用篮 —— 耳号篮是本系统既有的追溯归属口径，混进通用篮会污染追溯。
     * 不填则维持原行为（进通用篮）。</p>
     */
    @Size(max = 32, message = "{vegReceive.pig_ear_no.too_long}")
    private String pigEarNo;

    /**
     * 入库人 userId（FK → sys_user.user_id；可选，mp 入库人下拉，缺省时取当前登录用户）。
     */
    private Long operatorId;

}
