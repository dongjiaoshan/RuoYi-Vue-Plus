package org.dromara.djs.warehouse.flow.domain.query;

import org.dromara.common.core.exception.ServiceException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** 日期区间覆盖整个自然日；明确时间则保留精确边界，不能从零点数值反推输入类型。 */
public record StockFlowDateRange(LocalDateTime from, LocalDateTime to, boolean exclusiveEnd) {
    public static StockFlowDateRange parse(String from, String to) {
        boolean dayEnd = to != null && to.trim().matches("\\d{4}-\\d{2}-\\d{2}");
        LocalDateTime lower = boundary(from);
        LocalDateTime upper = boundary(to);
        return new StockFlowDateRange(lower, dayEnd ? upper.plusDays(1) : upper, dayEnd);
    }

    private static LocalDateTime boundary(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return value.length() == 10 ? LocalDate.parse(value).atStartOfDay()
                : LocalDateTime.parse(value.replace(' ', 'T'));
        } catch (DateTimeParseException ex) {
            throw new ServiceException("业务时间格式无效，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }
}
