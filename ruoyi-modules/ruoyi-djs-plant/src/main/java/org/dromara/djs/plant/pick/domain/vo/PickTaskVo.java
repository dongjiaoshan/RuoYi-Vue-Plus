package org.dromara.djs.plant.pick.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * mp 端采摘任务卡片 VO（PLT-PLAN-002）。
 *
 * <p>映射 {@code t_plant_plant_details} 单行 + enrich plot/crop 名称。
 * 本 ticket mp 端**仅浏览，不录入**（录入闭环在 D12 PLT-PICK-001）。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Data
public class PickTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** plant_details.id（snowflake，TS 端 string）。 */
    private Long id;

    private Long plantId;
    private String planNo;

    private Long plotId;
    private String plotCode;
    private String plotName;

    private Long cropId;
    private String cropName;

    private LocalDate earliestHarvestdate;
    private LocalDate lastHarvestdate;
    private LocalDate beginHarvestdate;
    private LocalDate endHarvestdate;

    /** djs_pick_status：pending/picking/completed/delayed。 */
    private String harvestStatus;

    private BigDecimal expectedYield;
    private BigDecimal actualYield;

    /** 1=游客采摘活动 / 2=否（字典 djs_yes_no）。 */
    private Integer isPick;

    private Long harvestBy;
    private String harvestTeamName;
}
