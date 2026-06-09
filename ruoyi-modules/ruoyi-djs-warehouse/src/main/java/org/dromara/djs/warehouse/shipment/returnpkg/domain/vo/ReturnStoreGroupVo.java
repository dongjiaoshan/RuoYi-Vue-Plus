package org.dromara.djs.warehouse.shipment.returnpkg.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 退货管理 mp 门店分组聚合 VO（{@code GET /applet/warehouse/return/pending}）。
 *
 * <p>FIX-WMS-MP-RETURN-001（原型图 57）：进页 = 门店分组卡片，
 * 每张卡 = 同门店 + 同状态（{@code pending} / {@code confirmed}）的一批退货行：</p>
 * <ul>
 *   <li>门店名 + 状态（待确认 / 已确认）</li>
 *   <li>退货产品品种数（该门店该状态下 distinct product_id 计数）</li>
 *   <li>退货时间（该组最近一条 apply_time）</li>
 * </ul>
 *
 * <p>聚合口径：表 {@code t_warehouse_return_product} 一行 = 一个产品，
 * 按 {@code store_id + return_status} 分组（与原型一张卡一门店一状态一致）。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-RETURN-001
 */
@Data
public class ReturnStoreGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 门店 ID（snowflake，Jackson 序列化为 string）。 */
    private Long storeId;

    /** 门店名称（StoreMapper 批量填充，避免 N+1）。 */
    private String storeName;

    /** 退货状态字典 {@code djs_return_status}：pending / confirmed。 */
    private String returnStatus;

    /** 退货产品品种数（该门店该状态下 distinct product_id 计数）。 */
    private int productKindCount;

    /** 退货时间（该组最近一条 apply_time，列表展示用）。 */
    private LocalDateTime returnTime;
}
