package org.dromara.djs.store.stat.domain;

import java.time.LocalDate;

public enum StoreIndicatorPeriod {
    DAY, MONTH, YEAR;

    public LocalDate startOf(LocalDate date) {
        return switch (this) {
            case DAY -> date;
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    public LocalDate nextStart(LocalDate date) {
        LocalDate start = startOf(date);
        return switch (this) {
            case DAY -> start.plusDays(1);
            case MONTH -> start.plusMonths(1);
            case YEAR -> start.plusYears(1);
        };
    }
}
