package org.dromara.djs.plant.activity.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采摘活动记录 VO（FIX-PLT-HARVEST-ACTIVITY-001）。
 *
 * <p>mp 采摘活动记录列表用：{@code cropName} / {@code teamName} 由 service 层批量 enrich
 * （为空时前端兜底显示，不阻塞主流程）。</p>
 *
 * @author djs
 * @since FIX-PLT-HARVEST-ACTIVITY-001
 */
@Data
public class PlantActivityVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    private Long id;

    /** FK → {@code t_plant_crop_info.id}。 */
    private Long cropId;

    /** 作物名称（enrich from t_plant_crop_info.crop_name，可空）。 */
    private String cropName;

    /** 采摘日期。 */
    private LocalDate activityDate;

    /** 当日采摘重量（kg）。 */
    private BigDecimal dailyWeight;

    /** 记录班组 FK → {@code t_plant_work_team.id}。 */
    private Long activityBy;

    /** 记录班组名称（enrich from t_plant_work_team.team_name，可空）。 */
    private String teamName;
}
