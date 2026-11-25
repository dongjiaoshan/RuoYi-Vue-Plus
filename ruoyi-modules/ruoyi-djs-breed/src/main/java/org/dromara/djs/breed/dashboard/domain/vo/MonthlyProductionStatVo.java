package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 当月生产统计表（率 / 窝均 9 行）VO（FIX-MGMT-MP-BRD-001，原型"种猪 tab·当月生产统计表"，#7.8）。
 *
 * <p>本表 = 9 行率/窝均指标 × (当月 vs 上月)。区别于现有
 * {@code /monthly-comparison}（7 行头数快照）：本端点输出 9 行率/窝均口径，实时算。</p>
 *
 * <p>9 行口径：分娩率 / 断配间隔 / 返空流头数 / 头均非生产天数NPD / 总产仔数 /
 * 窝均总产仔 / 窝均活仔 / 窝均断奶 / 产房损失率（R73 删配怀率）。复用 BreedingAnnual 同套公式（#7.1-7.5）。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-BRD-001
 */
@Data
public class MonthlyProductionStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当月（yyyy-MM）。 */
    private String currentMonth;

    /** 上月（yyyy-MM）。 */
    private String previousMonth;

    /** 当月落库生产指标与计算基数。 */
    private ProductionIndicators currentIndicators;

    /** 上月落库生产指标与计算基数。 */
    private ProductionIndicators previousIndicators;

    /** 原率/窝均对照行（顺序固定）。 */
    private List<StatRow> rows = new ArrayList<>();

    public record ProductionIndicators(
        Integer farrowSowCount,
        Integer reserveFirstBreedCount,
        Integer firstParityFarrowSowCount,
        Integer weaningSowCount,
        Integer normalWeaningSowCount,
        Integer lactationDaysSum,
        Integer lactationValidLitterCount,
        BigDecimal avgLactationDaysWeaned,
        Integer lactationDaysSumByFarrow,
        BigDecimal avgLactationDaysFarrowed,
        Integer birthLitterWeanedPigletCount,
        BigDecimal avgBirthLitterWeanedPiglets,
        Integer abortionCount,
        Integer weanAgeDaysSum,
        Integer weanAgeValidCount,
        BigDecimal avgWeanAgeDays,
        BigDecimal avgMarketingWeight,
        Integer marketingCount,
        BigDecimal marketingWeight,
        Integer weanedCount,
        BigDecimal avgWeanedPerLitter,
        BigDecimal avgProdSowStock,
        BigDecimal farrowLossRate,
        Integer bornCount,
        Integer deathCount,
        Integer cullingCount,
        BigDecimal weanBreedInterval,
        BigDecimal farrowRate,
        Integer mateLitterCount,
        Integer cohortFarrowCount,
        Integer totalBornCount,
        BigDecimal avgBornPerLitter,
        BigDecimal avgLiveBornPerLitter,
        Integer monthNpdDays,
        BigDecimal npdDays,
        Integer breedingSowCount,
        Integer repeatBreedingSowCount,
        BigDecimal repeatBreedingRate,
        Integer entryFirstBreedDaysSum,
        Integer entryFirstBreedValidCount,
        BigDecimal avgEntryFirstBreedDays,
        Integer reserveFirstBreedAgeDaysSum,
        Integer reserveFirstBreedAgeValidCount,
        BigDecimal avgReserveFirstBreedAgeDays,
        Integer weanBreedDaysSum,
        Integer weanBreedValidCount,
        Integer dailyFarrowSowCount,
        Integer dailyLiveBornCount,
        Integer farrowParitySum,
        Integer farrowParityValidCount,
        BigDecimal avgFarrowParity,
        Integer smallLiveBornLitterCount,
        BigDecimal smallLiveBornLitterRate,
        Integer healthyBornCount,
        Integer healthyBornRecordCount,
        BigDecimal avgHealthyBornPerLitter,
        Integer deadBornCount,
        BigDecimal deadBornRate,
        BigDecimal avgDeadBornPerLitter,
        Integer mummyBornCount,
        BigDecimal mummyBornRate,
        BigDecimal avgMummyBornPerLitter,
        Integer farrowBornCount,
        Integer gestationDaysSum,
        Integer gestationValidCount,
        BigDecimal avgGestationDays,
        BigDecimal birthWeightSum,
        Integer birthWeightPigletCount,
        BigDecimal avgBirthWeight,
        BigDecimal weanedWeightSum,
        BigDecimal avgWeanedWeight,
        Integer farrowLossCount,
        Integer farrowLossLiveBornCount,
        Integer unlinkedPigletDeathCullCount,
        Integer marketingAgeDaysSum,
        Integer marketingAgeValidCount,
        Integer marketingMissingBirthCount,
        BigDecimal avgMarketingAgeDays,
        BigDecimal marketingNetGainKg,
        Integer marketingGainDays,
        BigDecimal avgDailyGainGrams,
        Integer pigletDeathCount,
        Integer productionSowCullCount,
        BigDecimal productionSowCullRate,
        Integer productionSowDeathCount,
        BigDecimal productionSowDeathRate,
        Integer openingPigletStock,
        Integer farrowingHousePigletCount,
        BigDecimal farrowingHouseDeathRate,
        Integer fatteningDeathCount,
        BigDecimal fatteningDeathRate,
        Integer fatteningCullCount,
        BigDecimal fatteningCullRate,
        Integer fatteningStockSum,
        BigDecimal avgFatteningStock,
        BigDecimal feedWeightKg,
        BigDecimal organicFeedWeightKg,
        Integer vaccinePigTimes,
        Integer healthPigTimes,
        Integer treatmentPigTimes,
        Integer deathCullCount,
        Integer returnHeatCount,
        Integer emptySowCount,
        Integer endProductionSowStock,
        LocalDate endProductionSowStockDate,
        Integer productionSowStockSum,
        Integer indicatorDayCount,
        Integer pigletDeathAgeDaysSum,
        Integer pigletDeathAgeValidCount,
        Integer pigletDeathMissingBirthCount,
        BigDecimal avgPigletDeathAgeDays,
        Integer weaningTransferAgeDaysSum,
        Integer weaningTransferAgeValidCount,
        BigDecimal avgWeaningTransferAgeDays,
        BigDecimal weaningTransferWeightSum,
        Integer weaningTransferWeightValidCount,
        BigDecimal avgWeaningTransferWeight,
        Integer healthyBornTotalCount,
        BigDecimal healthyBornRate,
        Integer weakBornCount,
        BigDecimal avgWeakBornPerLitter,
        BigDecimal weakBornRate,
        Integer newProductionSowCount,
        BigDecimal productionSowReplacementRate,
        Integer culledSowParitySum,
        Integer culledSowParityValidCount,
        BigDecimal avgCulledSowParity,
        Integer pregnancyLossSowCount,
        BigDecimal pregnancyLossRate
    ) implements Serializable {}

    /**
     * 单行：中文指标名 + 当月值 + 上月值 + 单位提示。
     */
    @Data
    public static class StatRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 指标名（固定中文文案）。 */
        private String metric;

        /** 当月值。 */
        private BigDecimal current;

        /** 上月值。 */
        private BigDecimal previous;

        /** 单位/口径提示（如"%"、"天"、"头"、"头/窝"），前端拼显示。 */
        private String unit;

        public StatRow() {
        }

        public StatRow(String metric, BigDecimal current, BigDecimal previous, String unit) {
            this.metric = metric;
            this.current = current == null ? BigDecimal.ZERO : current;
            this.previous = previous == null ? BigDecimal.ZERO : previous;
            this.unit = unit;
        }
    }
}
