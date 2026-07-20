package org.dromara.djs.store.inventory.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.store.inventory.domain.StoreInventory;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店独立库存 VO（{@code t_store_inventory}，WSA-003）。
 *
 * @author djs
 * @since WSA-003
 */
@Data
@AutoMapper(target = StoreInventory.class)
public class StoreInventoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    private Long id;

    /** 门店 ID。 */
    private Long storeId;

    /** 产品 ID（雪花主键）。 */
    private Long productId;

    /** 门店结存数量/重量。 */
    private BigDecimal stockQty;
}
