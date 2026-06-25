package org.dromara.djs.breed.med.api;

import lombok.RequiredArgsConstructor;
import org.dromara.djs.breed.med.mapper.MedBatchMapper;
import org.dromara.djs.common.supplier.api.SupplierDealProvider;
import org.dromara.djs.common.supplier.api.SupplierDealStat;
import org.dromara.djs.common.supplier.api.SupplierDealVo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 批量聚合：一条 GROUP BY 直出，DB 端 COUNT/SUM，避免列表页逐个 N+1。
     *
     * <p>把 mapper 返回的 {@code {supplierId, dealCount, purchaseQty}} row map 转成
     * {@code supplierId → SupplierDealStat}；某 supplierId 无药品入库时不在结果中（调用方走 ZERO 兜底）。</p>
     */
    @Override
    public Map<Long, SupplierDealStat> aggregateStatsBySupplierIds(Collection<Long> ids) {
        Map<Long, SupplierDealStat> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : medBatchMapper.selectSupplierDealStats(ids)) {
            Long supplierId = toLong(row.get("supplierId"));
            if (supplierId == null) {
                continue;
            }
            int dealCount = toInt(row.get("dealCount"));
            BigDecimal purchaseQty = toBigDecimal(row.get("purchaseQty"));
            result.merge(supplierId, new SupplierDealStat(dealCount, purchaseQty), SupplierDealStat::plus);
        }
        return result;
    }

    private static Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static int toInt(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(v.toString());
    }

}
