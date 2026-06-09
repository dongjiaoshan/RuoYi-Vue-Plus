package org.dromara.djs.warehouse.flow.api;

import org.dromara.djs.common.supplier.api.SupplierDealVo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StockFlowSupplierDealProvider} 单测（DJS-FIX-ADMIN-W22-005）。
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>happy：透传 mapper 结果（物资入库流水行）</li>
 *   <li>null guard：supplierId 为 null 时返空 list 且不查 mapper</li>
 * </ol>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-005
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class StockFlowSupplierDealProviderTest {

    @Mock
    private StockFlowMapper stockFlowMapper;

    @InjectMocks
    private StockFlowSupplierDealProvider provider;

    @Test
    @DisplayName("happy：透传 mapper 聚合的物资入库流水行")
    void aggregateBySupplier_happy() {
        SupplierDealVo vo = new SupplierDealVo();
        vo.setDealDate(LocalDate.of(2026, 6, 1));
        vo.setDealProduct("玉米饲料");
        vo.setDealQuantity(new BigDecimal("500"));
        vo.setDealUnit("kg");
        vo.setSourceType("material");
        when(stockFlowMapper.selectSupplierDeals(eq(2002L))).thenReturn(List.of(vo));

        List<SupplierDealVo> result = provider.aggregateBySupplier(2002L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDealProduct()).isEqualTo("玉米饲料");
        assertThat(result.get(0).getSourceType()).isEqualTo("material");
        verify(stockFlowMapper).selectSupplierDeals(2002L);
    }

    @Test
    @DisplayName("null guard：supplierId 为 null 返空 list 且不查 mapper")
    void aggregateBySupplier_nullGuard() {
        List<SupplierDealVo> result = provider.aggregateBySupplier(null);

        assertThat(result).isEmpty();
        verify(stockFlowMapper, never()).selectSupplierDeals(org.mockito.ArgumentMatchers.any());
    }

}
