package org.dromara.djs.breed.med.api;

import org.dromara.djs.breed.med.mapper.MedBatchMapper;
import org.dromara.djs.common.supplier.api.SupplierDealVo;
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
 * {@link MedicineSupplierDealProvider} 单测（DJS-FIX-ADMIN-W22-005）。
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>happy：透传 mapper 结果（药品入库批次行）</li>
 *   <li>null guard：supplierId 为 null 时返空 list 且不查 mapper</li>
 * </ol>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-005
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class MedicineSupplierDealProviderTest {

    @Mock
    private MedBatchMapper medBatchMapper;

    @InjectMocks
    private MedicineSupplierDealProvider provider;

    @Test
    @DisplayName("happy：透传 mapper 聚合的药品入库批次行")
    void aggregateBySupplier_happy() {
        SupplierDealVo vo = new SupplierDealVo();
        vo.setDealDate(LocalDate.of(2026, 5, 20));
        vo.setDealProduct("头孢噻呋钠");
        vo.setDealQuantity(new BigDecimal("100"));
        vo.setDealUnit("瓶");
        vo.setSourceType("medicine");
        when(medBatchMapper.selectSupplierDeals(eq(1001L))).thenReturn(List.of(vo));

        List<SupplierDealVo> result = provider.aggregateBySupplier(1001L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDealProduct()).isEqualTo("头孢噻呋钠");
        assertThat(result.get(0).getSourceType()).isEqualTo("medicine");
        verify(medBatchMapper).selectSupplierDeals(1001L);
    }

    @Test
    @DisplayName("null guard：supplierId 为 null 返空 list 且不查 mapper")
    void aggregateBySupplier_nullGuard() {
        List<SupplierDealVo> result = provider.aggregateBySupplier(null);

        assertThat(result).isEmpty();
        verify(medBatchMapper, never()).selectSupplierDeals(org.mockito.ArgumentMatchers.any());
    }

}
