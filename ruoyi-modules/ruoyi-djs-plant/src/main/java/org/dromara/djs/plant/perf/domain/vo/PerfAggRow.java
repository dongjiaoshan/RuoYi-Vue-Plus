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
     * 采摘总量（公斤，= SUM(actual_yield)）。
     */
    private BigDecimal pickWeight;
}
