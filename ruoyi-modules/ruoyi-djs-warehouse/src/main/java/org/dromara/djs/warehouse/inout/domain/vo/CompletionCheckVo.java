package org.dromara.djs.warehouse.inout.domain.vo;

import java.math.BigDecimal;

/** 完成前复核结果；不改变业务状态。百分比不四舍五入后比较。 */
public record CompletionCheckVo(boolean confirmationRequired, String message,
                                BigDecimal actualWeight, BigDecimal referenceWeight,
                                BigDecimal thresholdPercent, BigDecimal thresholdWeight,
                                String comparison) { }
