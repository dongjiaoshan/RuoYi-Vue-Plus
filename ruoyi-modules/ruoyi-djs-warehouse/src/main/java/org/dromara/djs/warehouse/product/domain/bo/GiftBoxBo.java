package org.dromara.djs.warehouse.product.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.warehouse.product.domain.GiftBox;

import java.math.BigDecimal;

/**
 * 礼盒组件入参 BO（WMS-MD-002）。
 *
 * <p>嵌套在 {@link ProductInfoBo#getGiftComponents()} 列表中；当礼盒主体
 * {@code productType=3} 时由父 BO 携带，service 端覆盖式 replace。</p>
 *
 * @author djs
 * @since WMS-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = GiftBox.class, reverseConvertGenerate = false)
public class GiftBoxBo extends BaseEntity {

    /**
     * 组件行 ID（编辑场景；新增时由 service 忽略 — 走覆盖式 replace）。
     */
    private Long id;

    /**
     * 礼盒主体 ID（service 端会强制覆盖为新建礼盒的 id，前端不必填）。
     */
    private Long boxProductId;

    /**
     * 组件 SKU FK → product_info.id（必填）。
     */
    @NotNull(message = "{product.gift_component.product.required}")
    private Long componentProductId;

    /**
     * 组件数量（必填，> 0）。
     */
    @NotNull(message = "{product.gift_component.count.required}")
    @Positive(message = "{product.gift_component.count.positive}")
    private BigDecimal componentCount;

    /**
     * 组件单位（必填；前端可由组件 SKU 默认 fill）。
     */
    @NotNull(message = "{product.gift_component.unit.required}")
    @Size(max = 16, message = "{product.gift_component.unit.size}")
    private String componentUnit;

    /**
     * 排序（默认 0）。
     */
    @PositiveOrZero(message = "{product.gift_component.sort.positive}")
    private Integer componentSort;

}
