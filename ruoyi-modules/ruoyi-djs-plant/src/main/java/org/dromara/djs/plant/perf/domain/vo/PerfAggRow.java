package org.dromara.djs.plant.perf.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 绩效聚合中间行（PLT-PERF-001）。
 *
 * <p>{@code generate(statMonth)} 聚合 {@code t_plant_plant_details} 的原始结果：
 * 按 {@code harvest_by(team_id) × crop_id} GROUP BY 得采摘总量。
 * 不持久化，service 层据此补单价快照后转 entity 落库。</p>
 *
 * @author djs
 * @since PLT-PERF-001
 */
@Data
public class PerfAggRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 采摘班组 ID（= details.harvest_by）。
     */
    private Long teamId;

    /**
     * 作物 ID（= details.crop_id）。
     */
    private Long cropId;

    /**
     * 产品 ID（V6 row20，= handle_record.product_id）。存量流水没选过产品时为 null，
     * service 层折进该作物的首个配置产品。
     */
    private Long productId;

    /**
     * 绩效百分比（0-100 整数，%）—— V6 row107，= handle_record.perf_percent，同时是分组维度：
     * 同一「班组 × 作物 × 产品」下不同百分比拆成不同聚合行。采摘活动没有这个维度，按 100 归组。
     */
    private Integer perfPercent;

    /**
     * 采摘总量（公斤，= SUM(actual_yield)）。
     */
    private BigDecimal pickWeight;
}
