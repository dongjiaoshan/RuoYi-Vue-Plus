package org.dromara.djs.plant.plan.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 种植计划甘特图数据（PLT-PLAN-001 简化版双甘特图）。
 *
 * <p>每个地块一行 {@link Row}；前端按 {@code earliestHarvestdate / lastHarvestdate}（计划条）
 * + {@code beginActualdate / endActualdate}（实际条）绘制双横条。完整 dashboard 双甘特图
 * 见 PLT-DASH-001（后续）。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
public class PlantPlanGanttVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long planId;
    private String planNo;
    private Integer planYear;
    private String planSeason;
    private String cropName;
    private List<Row> rows;

    @Data
    public static class Row implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long detailId;
        private Long plotId;
        private String plotName;
        private String plotCode;
        private LocalDate earliestHarvestdate;
        private LocalDate lastHarvestdate;
        private LocalDate beginActualdate;
        private LocalDate endActualdate;
        private LocalDate beginHarvestdate;
        private LocalDate endHarvestdate;
        private String plantStatus;
        private String harvestStatus;
    }
}
