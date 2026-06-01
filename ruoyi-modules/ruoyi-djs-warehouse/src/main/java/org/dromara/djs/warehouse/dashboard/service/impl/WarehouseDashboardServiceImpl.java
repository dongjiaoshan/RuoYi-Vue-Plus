package org.dromara.djs.warehouse.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.dashboard.domain.vo.LocationOverviewItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;
import org.dromara.djs.warehouse.dashboard.mapper.WarehouseDashboardMapper;
import org.dromara.djs.warehouse.dashboard.service.IWarehouseDashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仓库看板聚合实现（DJS-FIX-ADMIN-W22-006 占位版）。
 *
 * <p>每个 KPI 1 个聚合 query，无 view / 无复杂事务。所有 null 用 0 / 空列表兜底。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-006
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseDashboardServiceImpl implements IWarehouseDashboardService {

    private static final String DEFAULT_TENANT = "1001";

    private final WarehouseDashboardMapper dashboardMapper;

    @Override
    public WarehouseDashboardSummaryVo getSummary() {
        String tenantId = currentTenant();

        WarehouseDashboardSummaryVo vo = new WarehouseDashboardSummaryVo();

        BigDecimal todayDemand = dashboardMapper.sumTodayWhiteBarDemand(tenantId);
        vo.setTodayDemandQuantity(todayDemand == null ? BigDecimal.ZERO : todayDemand);

        vo.setTodayProductionCount(nz(dashboardMapper.countTodayProduction(tenantId)));
        vo.setStockCheckNormal(nz(dashboardMapper.countLatestCheckNormal(tenantId)));
        vo.setStockCheckAbnormal(nz(dashboardMapper.countLatestCheckAbnormal(tenantId)));
        vo.setStockCheckLoss(nz(dashboardMapper.countLatestCheckLoss(tenantId)));
        vo.setMonthAbnormalLocationCount(nz(dashboardMapper.countMonthAbnormalLocation(tenantId)));

        List<LocationOverviewItemVo> overview = dashboardMapper.selectLocationOverview(tenantId);
        vo.setLocationOverview(overview == null ? List.of() : overview);

        return vo;
    }

    /**
     * null → 0。
     *
     * @param v 可空整数
     * @return 非空整数（null 视作 0）
     */
    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 取当前租户；V1 单租户场景或异常时回退 {@value #DEFAULT_TENANT}。
     *
     * @return 当前租户 ID
     */
    private String currentTenant() {
        try {
            String t = TenantHelper.getTenantId();
            return t == null || t.isEmpty() ? DEFAULT_TENANT : t;
        } catch (Exception e) {
            log.warn("[WarehouseDashboard] 获取租户失败，回退默认租户", e);
            return DEFAULT_TENANT;
        }
    }

}
