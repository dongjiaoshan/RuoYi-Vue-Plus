package org.dromara.djs.store.stat.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Data
public class StoreProductionIndicatorVo {

    private Long storeId;
    private LocalDate periodStart;
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

    /** 百分数保留两位。无到店分母时，日出品率为 0.00，其他不可比的率返回 null。 */
    public void calculateRates(StoreIndicatorPeriod period) {
        if (whiteBarReceivedWeight.signum() <= 0) {
            splitYieldRate = period == StoreIndicatorPeriod.DAY ? new BigDecimal("0.00") : null;
            splitLossRate = null;
            return;
        }
        splitYieldRate = percent(splitProductWeight);
        splitLossRate = percent(splitLossWeight);
    }

    private BigDecimal percent(BigDecimal numerator) {
        return numerator.movePointRight(2).divide(whiteBarReceivedWeight, 2, RoundingMode.HALF_UP);
    }
}
