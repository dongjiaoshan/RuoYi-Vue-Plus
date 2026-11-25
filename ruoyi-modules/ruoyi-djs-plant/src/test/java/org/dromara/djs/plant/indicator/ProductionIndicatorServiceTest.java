package org.dromara.djs.plant.indicator;

import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Daily;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Annual;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Monthly;
import org.dromara.djs.plant.indicator.mapper.PlantProductionIndicatorMapper;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.DailyPerformance;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.Detail;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.Member;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.Performance;
import org.dromara.djs.plant.indicator.service.ProductionIndicatorService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class ProductionIndicatorServiceTest {

    @Test
    void replayOneDayRefreshesWholeMonthWithOtherActualPlantingDays() {
        ProductionIndicatorSourceMapper source = mock(ProductionIndicatorSourceMapper.class);
        PlantProductionIndicatorMapper mapper = mock(PlantProductionIndicatorMapper.class);
        ProductionIndicatorService service = new ProductionIndicatorService(source, mapper);
        LocalDate day = LocalDate.of(2025, 10, 15);
        when(source.selectDetails("1001", 2025, LocalDate.of(2026, 1, 1))).thenReturn(List.of(
            new Detail(1, 10L, 11L, BigDecimal.valueOf(5), day, null, 2025, 10, 1L, day.minusDays(3), null, null),
            new Detail(2, 10L, 11L, BigDecimal.valueOf(5), day.minusDays(1), null, 2025, 10, 1L, day.minusDays(3), null, null)));

        when(source.selectDailyPerformance("1001", day, day.plusDays(1)))
            .thenReturn(List.of(new DailyPerformance(day, 77L, new BigDecimal("24"))));
        when(source.selectTeamMembers("1001", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)))
            .thenReturn(List.of(new Member(77L, 1L, day, null), new Member(77L, 2L, day, day.plusDays(1))));
        when(source.selectPerformance("1001", "2025-01", "2025-12"))
            .thenReturn(List.of(new Performance("2025-10", new BigDecimal("30"))));

        service.recalculate(day, day);

        ArgumentCaptor<Daily> daily = ArgumentCaptor.forClass(Daily.class);
        verify(mapper).upsertDaily(anyLong(), daily.capture());
        assertThat(daily.getValue().plantedPlotCount()).isEqualTo(1);
        assertThat(daily.getValue().harvestPerformancePerPersonEstimate()).as("种植 25").isEqualByComparingTo("12.00");
        ArgumentCaptor<Monthly> monthly = ArgumentCaptor.forClass(Monthly.class);
        verify(mapper).upsertMonthly(anyLong(), monthly.capture());
        assertThat(monthly.getValue().plantedPlotCount()).isEqualTo(2);
        assertThat(monthly.getValue().plantedArea()).isEqualByComparingTo("10");
        assertThat(monthly.getValue().harvestPerformancePerPerson()).as("种植 44").isEqualByComparingTo("30.00");
        ArgumentCaptor<Annual> annual = ArgumentCaptor.forClass(Annual.class);
        verify(mapper).upsertAnnual(anyLong(), annual.capture());
        assertThat(annual.getValue().harvestPerformancePerPerson()).as("种植 62").isEqualByComparingTo("30.00");
    }
}
