package org.dromara.djs.plant.dashboard.applet.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 管理·种植管理看板「后三个月果蔬预计产量排行」单格（FIX-MGMT-MP-PLT-001）。
 *
 * <p>{@code earliest_harvestdate} 落今~+3 月的明细，按 作物 + 月份 分组求 expected_yield 合计。
 * 前端透视成「品种 × 月」表。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-PLT-001
 */
@Data
public class MonthYieldRankVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物名称。 */
    private String cropName;

    /** 月份 1-12（earliest_harvestdate 的月）。 */
    private Integer month;

    /** 该作物该月预计产量合计（kg，SUM expected_yield）。 */
    private BigDecimal expectedYield;

}
