package org.dromara.djs.plant.perf.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 绩效聚合中间行——采摘活动流水（row12）。
 *
 * <p>{@code generate(statMonth)} 按 {@code crop_id × plot_id} 聚合 {@code t_plant_plant_activity}
 * 当月采摘量；service 层按地块采收班组集合（{@code t_plant_details_team role='harvest'}）平摊后
 * 并入班组 × 作物聚合。不持久化。</p>
 *
 * @author djs
 * @since FIX-ADMIN-0722
 */
@Data
public class PerfActivityAggRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物 ID。 */
    private Long cropId;

    /** 地块 ID（销售未结算行无地块，SQL 已过滤 plot 非空）。 */
    private Long plotId;

    /** 活动采摘总量（公斤，= SUM(daily_weight)）。 */
    private BigDecimal pickWeight;
}
