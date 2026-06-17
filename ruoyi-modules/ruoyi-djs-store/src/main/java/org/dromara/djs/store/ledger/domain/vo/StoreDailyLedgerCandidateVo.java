package org.dromara.djs.store.ledger.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 当日盘点候选行 VO（STORE-LEDGER-001 / WSA 重构「新增当日盘点」GET 候选）。
 *
 * <p>候选 = 三类产品并集（{@link #category}）：
 * <ul>
 *   <li>{@code pork} 猪肉：字典 {@code djs_pork_return_product} resolve 出的产品；入库量手动可编辑（上限=当日白条发货重量）；</li>
 *   <li>{@code inbound} 新到货：当日发货到该门店（shipment⋈demand，排 white_bar）的产品；{@code inboundQty}=发货量、{@link #inboundReadonly}=true；</li>
 *   <li>{@code stock} 昨日库存：{@code t_store_inventory.stock_qty>0} 的产品；{@code openingQty}=结存。</li>
 * </ul>
 * 并集去重；同一产品同时命中 inbound 与 stock 时合并为一行（category=stock，保留 inbound 的 inboundQty）。</p>
 *
 * <p>预填量：{@code openingQty}（库存表结存，只读）、{@code returnWhQty}（退回模块当日该产品聚合，只读）、
 * {@code saleQty}（销售流水当日聚合）、{@code returnSaleQty}（顾客退货当日聚合）。
 * 期末 / 赠送 / 销售前端手填默认 0；损耗由后端按公式计算不在候选。</p>
 *
 * @author djs
 * @since STORE-LEDGER-001
 */
@Data
public class StoreDailyLedgerCandidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 ID（雪花主键）。 */
    private Long productId;

    /** 产品名称。 */
    private String productName;

    /** 产品单位。 */
    private String productUnit;

    /** 产品规格。 */
    private String productSpec;

    /** 候选类别：pork=猪肉 / inbound=新到货 / stock=昨日库存。 */
    private String category;

    /** 期初库存（库存表当前结存，只读，无则 0）。 */
    private BigDecimal openingQty;

    /** 预填入库量（新到货=发货量；猪肉/库存行无则 0，猪肉可手动编辑）。 */
    private BigDecimal inboundQty;

    /** 入库量是否只读（新到货=true 不可编辑；猪肉=false 可手动且有上限）。 */
    private Boolean inboundReadonly;

    /** 预填销售量（销售流水当日聚合，无则 0）。 */
    private BigDecimal saleQty;

    /** 预填退货量（顾客退货 customer_to_store 当日聚合，无则 0）。 */
    private BigDecimal returnSaleQty;

    /** 预填退回量（门店退回仓库 store_to_warehouse 当日聚合，无则 0；只读）。 */
    private BigDecimal returnWhQty;
}
