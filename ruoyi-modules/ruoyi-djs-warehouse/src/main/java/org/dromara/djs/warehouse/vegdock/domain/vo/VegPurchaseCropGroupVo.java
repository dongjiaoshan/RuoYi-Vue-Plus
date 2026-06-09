package org.dromara.djs.warehouse.vegdock.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 外购果蔬「按品种聚合」VO（FIX-WMS-MP-VEGDOCK-001，原型图 42 外购产品收货列表）。
 *
 * <p>果蔬月台「外购收货」tab 按 {@code cropId} 聚合所有未入完（status != done）的到货记录，
 * 展示「品种 + 待入库重量合计」，对齐图 42 的「小白菜 / 番茄 / 花菜 + 待入库重量」卡片。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-VEGDOCK-001
 */
@Data
public class VegPurchaseCropGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long cropId;

    private String cropName;

    /**
     * 该品种合计待入库重量(kg)（SUM(pending_weight)，仅 status != done）。
     */
    private BigDecimal pendingWeight;

    /**
     * 该品种下待入库到货记录笔数。
     */
    private Integer recordCount;

}
