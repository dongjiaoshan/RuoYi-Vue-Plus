package org.dromara.djs.store.stat.service;

import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.store.loss.service.IStoreLossService;
import org.dromara.djs.store.stat.domain.StoreIndicatorPeriod;
import org.dromara.djs.store.stat.domain.StoreProductionIndicatorVo;
import org.dromara.djs.store.stat.mapper.StoreProductionIndicatorMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("local")
@Tag("dev")
class StoreProductionIndicatorServiceTest {

    private final StoreProductionIndicatorMapper mapper = mock(StoreProductionIndicatorMapper.class);
    private final IStoreLossService lossService = mock(IStoreLossService.class);
    private final StoreProductionIndicatorService service = new StoreProductionIndicatorService(mapper, lossService);

    @Test
    void monthRatesUseSummedBasesAndNaturalMonth() {
        LocalDate from = LocalDate.of(2026, 1, 15);
        StoreProductionIndicatorVo row = new StoreProductionIndicatorVo();
        row.setPeriodStart(from.withDayOfMonth(1));
        row.setWhiteBarReceivedWeight(new BigDecimal("150"));
        row.setSplitProductWeight(new BigDecimal("110"));
        row.setSplitLossWeight(new BigDecimal("45"));
        when(mapper.selectPeriod("1001", List.of(11L), from.withDayOfMonth(1),
            LocalDate.of(2026, 2, 1), StoreIndicatorPeriod.MONTH, from)).thenReturn(List.of(row));

        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("1001");
            assertThat(service.query(11L, from, from, StoreIndicatorPeriod.MONTH, from)).containsExactly(row);
        }
        assertThat(row.getSplitYieldRate()).isEqualByComparingTo("73.33");
        assertThat(row.getSplitLossRate()).isEqualByComparingTo("30.00");
    }

    @Test
    void rangeRebuildIncludesBothDates() {
        LocalDate from = LocalDate.of(2025, 12, 31);
        service.rebuildRange(from, from.plusDays(1));
        var ordered = inOrder(lossService);
        ordered.verify(lossService).aggregate(from);
        ordered.verify(lossService).aggregate(from.plusDays(1));
        ordered.verifyNoMoreInteractions();
    }
}
