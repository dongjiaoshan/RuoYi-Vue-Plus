package org.dromara.djs.warehouse.shipment.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 门店发货子页的「待发需求单」VO（{@code GET /applet/warehouse/ship/store-demands?storeId=}）。
 *
 * <p>发货月台 IA 重做（D12X-MP-SHIPDOCK-IA-001）：点门店 → 子页按需求单分组展示该门店所有
 * SHIPPABLE 状态的 demand，每个 demand 内嵌可发产品清单（复用既有 {@code listAvailableProductions}
 * 的按业态 + store_id 匹配逻辑）。子页"出车发货"按 demand 调既有 {@code confirmCheck}（单 demand
 * 单事务，不引跨 demand 跨事务复杂度 —— 方案 a）。</p>
 *
 * @author djs
 * @since D12X-MP-SHIPDOCK-IA-001
 */
@Data
public class ShipDemandVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 需求单 ID（snowflake，Jackson 序列化为 string —— confirmCheck 入参）。 */
    private Long demandId;

    /** 需求单号（展示用）。 */
    private String demandNo;

    /** 需求日期。 */
    private LocalDate demandDate;

    /** 业态（dict djs_demand_product_type：white_bar / vegetable / gift_box / other）。 */
    private String productType;

    /** 需求状态码（dict 由 mp tag 翻译）。 */
    private String demandStatus;

    /** 需求总量。 */
    private BigDecimal demandQuantity;

    /** 已发数量（累计）。 */
    private BigDecimal shippedCount;

    /** 需求单自身的产品 ID（snowflake→string）。用于「已确认需求但未生产」的产品也在门店货物里成卡展示（流程性问题 row6）。 */
    private Long productId;

    /** 需求单自身的产品名称（未生产时前端用它渲染产品卡，availableProductions 为空也要显示）。 */
    private String productName;

    /** 需求单自身的产品规格。 */
    private String productSpec;

    /** 需求单自身的产品计量单位（份/头/盒/kg…）。 */
    private String productUnit;

    /** 该需求按业态 + store_id 匹配出的可发产品清单（复用 AvailableProductionVo）。 */
    private List<AvailableProductionVo> availableProductions;
}
