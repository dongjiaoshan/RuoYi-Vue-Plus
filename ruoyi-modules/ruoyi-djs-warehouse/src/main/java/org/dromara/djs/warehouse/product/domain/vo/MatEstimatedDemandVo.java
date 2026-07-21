package org.dromara.djs.warehouse.product.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 生产领用「明日预估需求量」按原材料聚合投影 VO。
 *
 * <p>口径：以某原材料（{@code materialId}）为 {@code product_material} 的所有生产产品（成品，
 * {@code product_attr=1}），取其「明日需求量 × 单份用量（{@code material_num}）」之和——即
 * {@code DemandManageMapper} 的 {@code materialCalcQty}（需求量 × 单份用量）口径的「按原材料反向汇总」版。</p>
 *
 * <p>仅 {@code ProductInfoMapper.selectEstimatedDemandByMaterials} 批量反查回填；成品未配
 * {@code material_num} → 不进聚合，某材料的成品全 NULL → 无行返回 → 前端卡片「预估需求量」显空。</p>
 *
 * @author djs
 */
@Data
public class MatEstimatedDemandVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 原材料产品 ID（= {@code product_material}，snowflake，Jackson 序列化为 string）。
     */
    private Long materialId;

    /**
     * 明日预估需求量 = Σ（以本材料为原材料的成品的 明日需求量 × 单份用量）。
     */
    private BigDecimal estimatedDemand;

}
