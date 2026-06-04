package org.dromara.djs.breed.med.api;

import lombok.RequiredArgsConstructor;
import org.dromara.djs.breed.med.mapper.MedBatchMapper;
import org.dromara.djs.common.supplier.api.SupplierDealProvider;
import org.dromara.djs.common.supplier.api.SupplierDealVo;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 药品入库交易明细提供方（DJS-FIX-ADMIN-W22-005）。
 *
 * <p>实现 common 的 {@link SupplierDealProvider} 契约，对供应商详情"交易明细" tab
 * 贡献药品入库批次行。数据经 {@code medicine_id} JOIN {@code t_breed_medicine_info}
 * 拿到供应商关联（batch 表本身无 supplier_id）。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-005
 */
@Component
@RequiredArgsConstructor
public class MedicineSupplierDealProvider implements SupplierDealProvider {

    private final MedBatchMapper medBatchMapper;

    @Override
    public List<SupplierDealVo> aggregateBySupplier(Long supplierId) {
        if (supplierId == null) {
            return List.of();
        }
        return medBatchMapper.selectSupplierDeals(supplierId);
    }

}
