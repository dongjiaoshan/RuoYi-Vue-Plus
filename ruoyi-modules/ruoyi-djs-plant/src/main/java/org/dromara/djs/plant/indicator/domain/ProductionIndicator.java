package org.dromara.djs.plant.indicator.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** 种植生产指标持久化行及查询返回。 */
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
        int disasterPlotCount, BigDecimal harvestPerformancePerPersonEstimate, int harvestDockUnmatchedCount,
        Integer idlePlotCount, BigDecimal idleArea, BigDecimal inFieldArea, Integer inFieldCropCount,
        Integer inFieldPlotCount, Integer totalPlotCount, Integer overdueHarvestPlotCount, Integer pendingRotationPlotCount
    ) {

        public BigDecimal getRotationWaitAverageDays() {
            return divide(BigDecimal.valueOf(rotationWaitDaysSum), rotationPlotCount, 1);
        }
    }

    public record Monthly(
        LocalDate statMonth, BigDecimal plantedArea, int plantedCropCount,
        int plantedPlotCount, int plantPlanCount, int plantPlanCompletedCount,
        int farmWorkCount, BigDecimal farmWorkArea, int harvestFinishedPlotCount,
        BigDecimal harvestInboundWeight, int harvestInboundCropCount,
        int harvestInboundPlotCount, int harvestCropUnmatchedCount, int rotationPlotCount,
        BigDecimal rotationArea, int rotationUnmatchedCount,
        BigDecimal harvestPerformanceAmount, BigDecimal yieldCompletionPercent, BigDecimal rotationAverageDays,
        int disasterPlotCount, BigDecimal harvestPerformancePerPerson, BigDecimal disasterLossArea,
        BigDecimal disasterLossYield, int harvestDockUnmatchedCount,
        Integer idlePlotCount, BigDecimal plotUtilizationPercent, Integer overdueHarvestPlotCount,
        BigDecimal inFieldArea, Integer inFieldPlotCount, Integer inFieldCropCount, BigDecimal idleArea,
        Integer pendingRotationPlotCount
    ) {

        public BigDecimal getPlantPlanCompletionPercent() {
            return divide(100L * plantPlanCompletedCount, plantPlanCount);
        }
    }

    public record Annual(
        int statYear, BigDecimal plantedArea, int plantPlanCount, int plantPlanCompletedCount,
        BigDecimal harvestInboundWeight, int disasterRecordCount, int rotationPlotCount,
        BigDecimal harvestPerformanceAmount, String performanceThroughMonth,
        long idleDaysSum, int idleIntervalCount, BigDecimal rotationAverageDays,
        BigDecimal harvestPerformancePerPerson, int harvestDockUnmatchedCount, BigDecimal averagePlantingsPerPlot
    ) {

        public BigDecimal getPlantPlanCompletionPercent() {
            return divide(100L * plantPlanCompletedCount, plantPlanCount);
        }
        public BigDecimal getIdleAverageDays() {
            return divide(BigDecimal.valueOf(idleDaysSum), idleIntervalCount, 1);
        }

        public Annual withIdleTotals(IdleTotals totals) {
            return new Annual(statYear, plantedArea, plantPlanCount, plantPlanCompletedCount, harvestInboundWeight,
                disasterRecordCount, rotationPlotCount, harvestPerformanceAmount, performanceThroughMonth,
                totals.days(), totals.intervals(), rotationAverageDays, harvestPerformancePerPerson,
                harvestDockUnmatchedCount, averagePlantingsPerPlot);
        }
    }

    public record IdleTotals(long days, int intervals) {
    }

    public record Report(List<Daily> daily, List<Monthly> monthly, List<Annual> annual) {
    }

    private static BigDecimal divide(long numerator, int denominator) {
        return divide(BigDecimal.valueOf(numerator), denominator, 2);
    }

    public static BigDecimal divide(BigDecimal numerator, int denominator, int scale) {
        return denominator == 0 ? BigDecimal.ZERO.setScale(scale)
            : numerator.divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }
}
