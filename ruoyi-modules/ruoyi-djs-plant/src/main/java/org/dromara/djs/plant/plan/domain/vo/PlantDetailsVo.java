package org.dromara.djs.plant.plan.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.plant.plan.domain.PlantDetails;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 种植计划明细 VO（PLT-PLAN-001）。
 *
 * <p>{@code plotName / plotCode / cropName / plantTeamName / harvestTeamName} 由 service 批量 enrich。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
@AutoMapper(target = PlantDetails.class)
public class PlantDetailsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long plantId;
    private Long plotId;
    private Long cropId;
    private Integer plantMonth;
    private String plantPeriod;
    private LocalDate beginActualdate;
    private LocalDate endActualdate;
    private LocalDate beginHarvestdate;
    private LocalDate endHarvestdate;
    private LocalDate earliestHarvestdate;
    private LocalDate lastHarvestdate;
    private String plantStatus;
    private String harvestStatus;
    private BigDecimal plotArea;
    private BigDecimal expectedYield;
    private BigDecimal lossYield;
    private BigDecimal actualYield;
    private BigDecimal averageYield;
    private Long plantBy;
    private Long harvestBy;
    private Integer isPick;
    private Date createTime;

    // enrich
    private String plotName;
    private String plotCode;
    private String cropName;
    private String plantTeamName;
    private String harvestTeamName;
}
