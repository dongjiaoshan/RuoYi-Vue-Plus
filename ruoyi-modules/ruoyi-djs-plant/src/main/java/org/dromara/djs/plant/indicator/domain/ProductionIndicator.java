package org.dromara.djs.plant.indicator.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** V1.1 种植生产指标持久化行及查询返回。 */
public final class ProductionIndicator {

    private ProductionIndicator() {
    }

    public record Daily(
        LocalDate statDate, BigDecimal plantedArea, int plantedCropCount,
        int plantedPlotCount, int farmWorkCount, BigDecimal farmWorkArea,
        int harvestFinishedPlotCount, BigDecimal harvestInboundWeight,
        int harvestInboundCropCount, int harvestInboundPlotCount,
        int harvestCropUnmatchedCount, int rotationPlotCount, BigDecimal rotationArea,
        int rotationWaitDaysSum, int rotationWaitValidCount, int rotationUnmatchedCount,
        int disasterPlotCount
    ) {

        public BigDecimal getRotationWaitAverageDays() {
            return divide(rotationWaitDaysSum, rotationWaitValidCount);
        }
    }

    public record Monthly(
        LocalDate statMonth, BigDecimal plantedArea, int plantedCropCount,
        int plantedPlotCount, int plantPlanCount, int plantPlanCompletedCount,
        int farmWorkCount, BigDecimal farmWorkArea, int harvestFinishedPlotCount,
        BigDecimal harvestInboundWeight, int harvestInboundCropCount,
        int harvestInboundPlotCount, int harvestCropUnmatchedCount, int rotationPlotCount,
        BigDecimal rotationArea, int rotationUnmatchedCount,
        BigDecimal harvestPerformanceAmount
    ) {

        public BigDecimal getPlantPlanCompletionPercent() {
            return divide(100L * plantPlanCompletedCount, plantPlanCount);
        }
    }

    public record Annual(
        int statYear, BigDecimal plantedArea, int plantPlanCount, int plantPlanCompletedCount,
        BigDecimal harvestInboundWeight, int disasterRecordCount, int rotationPlotCount,
        BigDecimal harvestPerformanceAmount, String performanceThroughMonth,
        long idleDaysSum, int idleIntervalCount
    ) {

        public BigDecimal getPlantPlanCompletionPercent() {
            return divide(100L * plantPlanCompletedCount, plantPlanCount);
        }
        public BigDecimal getIdleAverageDays() {
            return divide(idleDaysSum, idleIntervalCount);
        }

        public Annual withIdleTotals(IdleTotals totals) {
            return new Annual(statYear, plantedArea, plantPlanCount, plantPlanCompletedCount, harvestInboundWeight,
                disasterRecordCount, rotationPlotCount, harvestPerformanceAmount, performanceThroughMonth,
                totals.days(), totals.intervals());
        }
    }

    public record IdleTotals(long days, int intervals) {
    }

    public record Report(List<Daily> daily, List<Monthly> monthly, List<Annual> annual) {
    }

    private static BigDecimal divide(long numerator, int denominator) {
        return denominator == 0 ? BigDecimal.ZERO
            : BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
