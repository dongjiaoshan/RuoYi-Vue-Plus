package org.dromara.djs.plant.perf.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 农事次数批量统计行（PLT-PERF-001 rework 134/135）。
 *
 * <p>{@code mapper.countFarmByTeamMonths} 对 {@code t_plant_farm_records} 按
 * {@code (farm_by, DATE_FORMAT(farm_date,'%Y-%m'))} GROUP BY 的中间结果，
 * service 据此按 (teamId, statMonth) 批量补 {@link PerfListRow#getFarmCount()}，避免 N+1。</p>
 *
 * @author djs
 * @since PLT-PERF-001
 */
@Data
public class FarmCountRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 班组 ID（= farm_records.farm_by）。
     */
    private Long farmBy;

    /**
     * 统计月份（yyyy-MM，= DATE_FORMAT(farm_date,'%Y-%m')）。
     */
    private String statMonth;

    /**
     * 农事次数（= COUNT(*)）。
     */
    private Integer cnt;
}
