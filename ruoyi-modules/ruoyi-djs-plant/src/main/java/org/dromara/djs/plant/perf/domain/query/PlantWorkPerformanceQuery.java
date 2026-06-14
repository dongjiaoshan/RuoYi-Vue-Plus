package org.dromara.djs.plant.perf.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 班组绩效查询参数（PLT-PERF-001）。
 *
 * @author djs
 * @since PLT-PERF-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlantWorkPerformanceQuery extends BaseEntity {

    /**
     * 统计月份（精确匹配，如 "2026-04"，可空）。
     */
    private String statMonth;

    /**
     * 班组 ID（精确匹配，可空）。
     */
    private Long teamId;
}
