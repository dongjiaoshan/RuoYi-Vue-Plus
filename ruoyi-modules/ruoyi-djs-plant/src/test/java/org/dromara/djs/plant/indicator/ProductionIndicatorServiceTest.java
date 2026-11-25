package org.dromara.djs.plant.indicator;

import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Daily;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.IdleTotals;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Monthly;
import org.dromara.djs.plant.indicator.mapper.PlantProductionIndicatorMapper;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.Detail;
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
            new Detail(1, 10L, 11L, BigDecimal.valueOf(5), day, null, 2025, 10),
            new Detail(2, 10L, 11L, BigDecimal.valueOf(5), day.minusDays(1), null, 2025, 10)));

        when(source.selectIdleTotals("1001", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)))
            .thenReturn(new IdleTotals(6, 2));

        service.recalculate(day, day);

        ArgumentCaptor<Daily> daily = ArgumentCaptor.forClass(Daily.class);
        verify(mapper).upsertDaily(anyLong(), daily.capture());
        assertThat(daily.getValue().plantedPlotCount()).isEqualTo(1);
        ArgumentCaptor<Monthly> monthly = ArgumentCaptor.forClass(Monthly.class);
        verify(mapper).upsertMonthly(anyLong(), monthly.capture());
        assertThat(monthly.getValue().plantedPlotCount()).isEqualTo(2);
        assertThat(monthly.getValue().plantedArea()).isEqualByComparingTo("10");
    }
}
