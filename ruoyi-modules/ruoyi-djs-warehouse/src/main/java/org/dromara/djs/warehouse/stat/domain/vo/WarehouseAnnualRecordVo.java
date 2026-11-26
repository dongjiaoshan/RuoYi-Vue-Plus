package org.dromara.djs.warehouse.stat.domain.vo;

import java.math.BigDecimal;

/** 仓库年度指标。查询时由月表基数汇总。 */
public record WarehouseAnnualRecordVo(
    int year,
    BigDecimal cutLoss,
    BigDecimal cutLossPickupWeight,
    BigDecimal cutLossRate,
    BigDecimal vegRawInboundWeight,
    BigDecimal vegDeliveryOutWeight,
    int slaughterCount, // 第 53 行：年屠宰头数，按首次实际接收年累计。
    BigDecimal barYieldRate,
    BigDecimal cutRate,
    BigDecimal vegEndStockWeight, // 年末账面库存（kg），无快照为空。
    BigDecimal vegDamageRate
) {
}
