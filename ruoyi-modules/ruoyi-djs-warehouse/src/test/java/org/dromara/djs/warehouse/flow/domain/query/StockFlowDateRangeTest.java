package org.dromara.djs.warehouse.flow.domain.query;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

@Tag("dev")
class StockFlowDateRangeTest {
    @Test void sameDayIncludesWholeLastDay() {
        var range = StockFlowDateRange.parse("2026-11-06", "2026-11-06");
        assertThat(range.from()).isEqualTo(LocalDateTime.of(2026, 11, 6, 0, 0));
        assertThat(range.to()).isEqualTo(LocalDateTime.of(2026, 11, 7, 0, 0));
        assertThat(range.exclusiveEnd()).isTrue();
    }
    @Test void explicitMidnightRemainsAnExactInstant() {
        var range = StockFlowDateRange.parse("2026-11-06 00:00:00", "2026-11-06 00:00:00");
        assertThat(range.from()).isEqualTo(range.to());
        assertThat(range.exclusiveEnd()).isFalse();
    }
    @Test void explicitTimesAndFractionalSecondsStayPrecise() {
        var range = StockFlowDateRange.parse("2026-11-06 12:01:03", "2026-11-06T12:01:06.123");
        assertThat(range.from()).isEqualTo(LocalDateTime.of(2026, 11, 6, 12, 1, 3));
        assertThat(range.to().getNano()).isEqualTo(123000000);
        assertThat(range.exclusiveEnd()).isFalse();
    }
    @Test void multiDayLeapBoundaryAndOpenStart() {
        var range = StockFlowDateRange.parse(null, "2028-02-29");
        assertThat(range.from()).isNull();
        assertThat(range.to()).isEqualTo(LocalDateTime.of(2028, 3, 1, 0, 0));
        assertThat(range.exclusiveEnd()).isTrue();
    }
    @Test void emptyInputsDoNotApplyFilters() {
        var range = StockFlowDateRange.parse(" ", null);
        assertThat(range.from()).isNull();
        assertThat(range.to()).isNull();
        assertThat(range.exclusiveEnd()).isFalse();
    }
    @Test void invalidCalendarDatesAreRejected() {
        assertThatThrownBy(() -> StockFlowDateRange.parse("2026-02-30", null)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> StockFlowDateRange.parse(null, "2026-13-01")).isInstanceOf(ServiceException.class);
    }
}
