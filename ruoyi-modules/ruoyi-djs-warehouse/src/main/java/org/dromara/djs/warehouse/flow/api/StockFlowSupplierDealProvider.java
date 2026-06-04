package org.dromara.djs.warehouse.flow.api;

import lombok.RequiredArgsConstructor;
import org.dromara.djs.common.supplier.api.SupplierDealProvider;
import org.dromara.djs.common.supplier.api.SupplierDealVo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 物资入库交易明细提供方（DJS-FIX-ADMIN-W22-005）。
 *
 * <p>实现 common 的 {@link SupplierDealProvider} 契约，对供应商详情"交易明细" tab
 * 贡献物资入库流水行（{@code inout_type='IN'} 且带 {@code supplier_id}）。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-005
 */
@Component
@RequiredArgsConstructor
public class StockFlowSupplierDealProvider implements SupplierDealProvider {

    private final StockFlowMapper stockFlowMapper;

    @Override
    public List<SupplierDealVo> aggregateBySupplier(Long supplierId) {
        if (supplierId == null) {
            return List.of();
        }
        return stockFlowMapper.selectSupplierDeals(supplierId);
    }

}
