package org.dromara.djs.warehouse.shipment.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店维度待发货聚合 VO（{@code GET /applet/warehouse/ship/store-list}）。
 *
 * <p>发货月台 IA 重做（D12X-MP-SHIPDOCK-IA-001）：进页 = 门店列表，一眼看到哪些门店还有货要发。</p>
 *
 * <h3>聚合口径</h3>
 * <p>待发货的驱动单位是<b>需求单（demand）</b>而非 production —— production 由 pack 链生成时
 * {@code demand_id} 恒为 NULL（SHIP-DEMANDID-001 设计：confirmCheck 时才回写），无法据此反查门店。
 * 故门店列表按 <b>SHIPPABLE 状态的 demand</b>（CONFIRMED / IN_PRODUCTION / PARTIAL_SHIPPED）
 * {@code group by store_id} 聚合。</p>
 *
 * @author djs
 * @since D12X-MP-SHIPDOCK-IA-001
 */
@Data
public class ShipStoreVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 门店 ID（snowflake，Jackson 序列化为 string）。 */
    private Long storeId;

    /** 门店名称（StoreMapper 批量 JOIN 填充，避免 N+1）。 */
    private String storeName;

    /** 待发货需求单数量（该门店 SHIPPABLE 状态的 demand 行数）。 */
    private int pendingDemandCount;

    /** 待发货产品种类数量（该门店待发 demand 涉及的 distinct product_id 计数 —— 客户主诉求字段）。 */
    private int productKindCount;

    /** 待发货需求总量（该门店待发 demand 的 demand_quantity 累加，便于子页预期）。 */
    private BigDecimal pendingQuantity;

    /** 发货状态（V1 恒 "待发货" —— 门店列表只列还有待发 demand 的门店）。 */
    private String shipStatus;
}
