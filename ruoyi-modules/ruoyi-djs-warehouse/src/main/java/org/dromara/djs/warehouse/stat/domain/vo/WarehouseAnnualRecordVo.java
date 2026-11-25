package org.dromara.djs.warehouse.stat.domain.vo;

import java.math.BigDecimal;

/** 仓库年度指标。查询时由月表基数汇总。 */
public record WarehouseAnnualRecordVo(
    int year,
    BigDecimal cutLoss,
    BigDecimal cutLossPickupWeight,
    BigDecimal cutLossRate,
    BigDecimal vegRawInboundWeight,
    BigDecimal vegDeliveryOutWeight
) {
}
