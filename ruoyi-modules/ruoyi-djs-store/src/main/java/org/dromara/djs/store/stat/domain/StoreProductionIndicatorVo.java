package org.dromara.djs.store.stat.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Data
public class StoreProductionIndicatorVo {

    private Long storeId;
    private LocalDate periodStart;
    private Boolean orderTimely;
    private BigDecimal orderTimelinessRate;
    private BigDecimal porkReceivedWeightKg;
    private BigDecimal vegDamagePortionCount;
    private BigDecimal vegDamageReceivedPortionCount;
    private BigDecimal vegDamageRate;
    private BigDecimal porkReturnRate;
    private BigDecimal vegReturnRate;
    @JsonIgnore
    private long timelyDayCount;
    @JsonIgnore
    private LocalDate firstOrderDate;
    private BigDecimal vegReceivedPortionCount;
    private BigDecimal vegReceiveKgWeight;
    private BigDecimal whiteBarReceivedWeight;
    private BigDecimal splitProductWeight;
    private BigDecimal splitLossWeight;
    private BigDecimal porkReturnWeightKg;
    private BigDecimal vegReturnPortionCount;
    private BigDecimal vegReturnKgWeight;
    private BigDecimal splitYieldRate;
    private BigDecimal splitLossRate;

    private static final BigDecimal ZERO_RATE = new BigDecimal("0.00");

    public void calculateRates(StoreIndicatorPeriod period, LocalDate reportDate) {
        if (period == StoreIndicatorPeriod.DAY) {
            // V1.1 快照仍保有计算基数，首次回刷前按基数读出已有日率。
            if (splitYieldRate == null) {
                splitYieldRate = splitRate(splitProductWeight, whiteBarReceivedWeight);
            }
            if (splitLossRate == null) {
                splitLossRate = splitRate(splitLossWeight, whiteBarReceivedWeight);
            }
            return;
        }

        splitYieldRate = periodRate(splitProductWeight, whiteBarReceivedWeight);
        splitLossRate = periodRate(splitLossWeight, whiteBarReceivedWeight);
        porkReturnRate = periodRate(porkReturnWeightKg, porkReceivedWeightKg);
        vegReturnRate = periodRate(vegReturnPortionCount, vegReceivedPortionCount);
        vegDamageRate = periodRate(vegDamageReceivedPortionCount, vegReceivedPortionCount);

        LocalDate end = period.nextStart(periodStart).minusDays(1);
        if (reportDate.isBefore(end)) {
            end = reportDate;
        }
        LocalDate start = period == StoreIndicatorPeriod.YEAR ? firstOrderDate : periodStart;
        long days = start == null ? 0 : ChronoUnit.DAYS.between(start, end) + 1;
        orderTimelinessRate = periodRate(BigDecimal.valueOf(timelyDayCount), BigDecimal.valueOf(days));
    }

    /** 日分割出品率、损耗率，无白条到店时存 NULL。 */
    public static BigDecimal splitRate(BigDecimal numerator, BigDecimal whiteBarWeight) {
        return whiteBarWeight.signum() > 0 ? percent(numerator, whiteBarWeight) : null;
    }

    private static BigDecimal periodRate(BigDecimal numerator, BigDecimal denominator) {
        return denominator == null || denominator.signum() <= 0 ? ZERO_RATE : percent(numerator, denominator);
    }

    private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        return numerator.movePointRight(2).divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
