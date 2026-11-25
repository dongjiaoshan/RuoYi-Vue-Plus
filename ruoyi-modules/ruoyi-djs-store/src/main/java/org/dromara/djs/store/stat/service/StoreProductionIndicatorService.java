package org.dromara.djs.store.stat.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.common.store.context.StoreContext;
import org.dromara.djs.store.loss.service.IStoreLossService;
import org.dromara.djs.store.stat.domain.StoreIndicatorPeriod;
import org.dromara.djs.store.stat.domain.StoreProductionIndicatorVo;
import org.dromara.djs.store.stat.mapper.StoreProductionIndicatorMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreProductionIndicatorService {

    private final StoreProductionIndicatorMapper mapper;
    private final IStoreLossService lossService;

    /** 重算闭区间。逐日提交，某天失败可从该日重跑。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void rebuildRange(LocalDate from, LocalDate to) {
        validateRange(from, to);
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            lossService.aggregate(date);
        }
    }

    /** 月、年查询覆盖入参命中的自然周期；基数按店汇总后计算百分比。 */
    public List<StoreProductionIndicatorVo> query(Long storeId, LocalDate from, LocalDate to,
                                                   StoreIndicatorPeriod period) {
        validateRange(from, to);
        String selectedStore = StoreContext.getStoreId();
        if (selectedStore != null) {
            Long selectedId = Long.valueOf(selectedStore);
            if (storeId != null && !storeId.equals(selectedId)) {
                return List.of();
            }
            storeId = selectedId;
        }
        List<Long> storeIds = StoreContext.getAccessibleStoreIds();
        if (storeId != null) {
            if (storeIds != null && !storeIds.contains(storeId)) {
                return List.of();
            }
            storeIds = List.of(storeId);
        }
        if (storeIds != null && storeIds.isEmpty()) {
            return List.of();
        }
        String tenantId = TenantHelper.getTenantId();
        List<StoreProductionIndicatorVo> rows = mapper.selectPeriod(
            tenantId == null ? DjsJobRunner.DEFAULT_TENANT : tenantId, storeIds,
            period.startOf(from), period.nextStart(to), period);
        rows.forEach(row -> row.calculateRates(period));
        return rows;
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ServiceException("日期区间无效", 400);
        }
    }
}
