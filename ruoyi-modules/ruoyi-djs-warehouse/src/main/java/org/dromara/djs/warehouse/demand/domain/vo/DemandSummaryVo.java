package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 需求确认页 SummaryBar union DTO（DJS-FIX-ADMIN-W22-003）。
 *
 * <p>4 业态共用一个 VO，按 {@code productType} 填不同字段，其余字段保持 null：</p>
 * <ul>
 *   <li>{@code white_bar}：{@code availablePigs}（可出栏育肥猪头数）</li>
 *   <li>{@code vegetable}：{@code plotCount} / {@code expectedYieldKg} / {@code earliestPickDate} / {@code currentStockKg}</li>
 *   <li>{@code gift_box}：{@code giftBoxStock}（礼盒成品库存盒数）</li>
 *   <li>{@code other}：{@code rawMaterialStockKg}（原料 + 干货 + 包材 库存 kg）</li>
 * </ul>
 *
 * <p>礼盒库存 V1 注意：成品 product_inhouse 表 D11 才建，当前 V1 用 product_info(belong_type='gift_box')
 * 关联 location_stock SUM 兜底；admin 端 UI 文案不变（"当前礼盒库存 N 盒"）。
 * 详见 D09X reports / _open-issues.md。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-003
 */
@Data
public class DemandSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业态 echo：white_bar / vegetable / gift_box / other。 */
    private String productType;

    // ---- white_bar ----
    /** 可出栏育肥猪头数（COUNT；与「指定猪只」对话框 listAvailableForOutbound 同语义）。 */
    private Integer availablePigs;

    // ---- vegetable ----
    /** 进行中计划去重地块数。 */
    private Integer plotCount;

    /** 进行中计划待产量 kg（expected - actual）。 */
    private BigDecimal expectedYieldKg;

    /** 进行中计划最早采摘日期。 */
    private LocalDate earliestPickDate;

    /** 当前蔬菜成品库存 kg（product_info.belong_type='vegetable' SUM stock）。 */
    private BigDecimal currentStockKg;

    // ---- gift_box ----
    /** 当前礼盒成品库存（V1 用 location_stock + belong_type='gift_box' 兜底）。 */
    private BigDecimal giftBoxStock;

    // ---- other ----
    /**
     * 原料类库存合计 kg。
     * <p>口径：{@code product_info.product_attr=2} 原材料 + {@code belong_type IN ('dry_good')}。</p>
     */
    private BigDecimal rawMaterialStockKg;
}
