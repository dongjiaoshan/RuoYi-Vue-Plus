package org.dromara.djs.plant.perf.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 地块 × 作物 → 采收班组解析行（row12 采摘活动绩效平摊用）。
 *
 * <p>来源 {@code t_plant_plant_details JOIN t_plant_details_team(role='harvest')}，
 * 一个 (plot, crop) 可对应多个班组（多选）。不持久化。</p>
 *
 * @author djs
 * @since FIX-ADMIN-0722
 */
@Data
public class PlotCropTeamRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 地块 ID。 */
    private Long plotId;

    /** 作物 ID。 */
    private Long cropId;

    /** 采收班组 ID。 */
    private Long teamId;
}
