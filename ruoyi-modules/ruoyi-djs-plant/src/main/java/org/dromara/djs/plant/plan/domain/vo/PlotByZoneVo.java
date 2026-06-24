package org.dromara.djs.plant.plan.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 向导 step3 用：按片区分组的可用地块清单（PLT-PLAN-001）。
 *
 * <p>{@code GET /djs/plant/plan/availablePlots}：返回所有片区 + 每个片区下的地块（不限制状态）。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
public class PlotByZoneVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long zoneId;
    private String zoneName;
    private String zoneCode;
    private List<Plot> plots;

    @Data
    public static class Plot implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long plotId;
        private String plotName;
        private String plotCode;
        private BigDecimal plotArea;
        private Integer plotStatus;

        /**
         * 当年该地块计划种植次数（轮作次数）= 该地块在所选 planYear 下的种植明细行数。
         *
         * <p>{@code listAvailablePlots(planYear)} 传 planYear 时由 service 批量回填；
         * planYear 为空时不回填（保持 null，前端不渲染「N次轮作计划」标签）。</p>
         */
        private Integer rotationCount;
    }
}
