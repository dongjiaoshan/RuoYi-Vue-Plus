package org.dromara.djs.warehouse.inout.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 果蔬出库工作台地块卡（V6 row283）。
 *
 * <p>一张卡 = 同产品 + 同库位 + 同地块 + 同耳号 + 同三期的一组库存篮，与出库先进先出分配
 * （{@code FifoAllocator}）认定的「同一行」完全一致，所以一张卡可以整组提交。
 * 无地块的库存单独成卡（{@code plotId} 为空）。</p>
 */
@Data
public class VegOutStockVo {
    private Long productId;
    private Long plotId;
    private String plotCode;
    private String plotName;
    private String earNo;
    private Integer thirdPhase;
    private Long locationId;
    private String locationName;
    /** 剩余库存总量（本组篮合计）。 */
    private BigDecimal stockWeight;
    /** 本组库存篮 id，先进先出序。 */
    private List<Long> stockIds;
}
