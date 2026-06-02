package org.dromara.djs.store.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardDailyVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardSummaryVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreGroupCountVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreProductRankItemVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreTrendPointVo;
import org.dromara.djs.store.dashboard.mapper.StoreDashboardMapper;
import org.dromara.djs.store.dashboard.mapper.StoreDashboardMapper.DailyCategoryRow;
import org.dromara.djs.store.dashboard.service.IStoreDashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 门店看板聚合实现（STR-DASH-001，纯只读聚合，不 extends DjsBaseServiceImpl）。
 *
 * <p>每个 KPI / 列表 1 个聚合 query，无 view / 无写事务。所有 null 用 0 / 空列表兜底。
 * 退回率 / 损耗率上游 V1 无明细表 → 置 null（前端显示"—"）。</p>
 *
 * @author djs
 * @since STR-DASH-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreDashboardServiceImpl implements IStoreDashboardService {

    private static final String DEFAULT_TENANT = "1001";

    /**
     * 猪肉业态（belong_type，字典 djs_belong_type）：自养猪肉 + 白条。
     */
    private static final List<String> PORK_TYPES = List.of("pork", "white_bar");

    /**
     * 果蔬业态（belong_type，字典 djs_belong_type）。
     */
    private static final List<String> VEG_TYPES = List.of("vegetable");

    private final StoreDashboardMapper dashboardMapper;

    @Override
    public StoreDashboardSummaryVo getSummary(Long storeId) {
        String tenantId = currentTenant();

        StoreDashboardSummaryVo vo = new StoreDashboardSummaryVo();
        vo.setTodaySaleAmount(nzBd(dashboardMapper.sumTodaySaleAmount(tenantId, storeId)));
        vo.setMonthSaleAmount(nzBd(dashboardMapper.sumMonthSaleAmount(tenantId, storeId)));
        vo.setTodayOrderCount(nz(dashboardMapper.countTodayOrders(tenantId, storeId)));
        vo.setMonthOrderCount(nz(dashboardMapper.countMonthOrders(tenantId, storeId)));
        vo.setPendingShipCount(nz(dashboardMapper.countPendingShip(tenantId, storeId)));
        vo.setPendingPurchaseCount(nz(dashboardMapper.countPendingPurchase(tenantId, storeId)));

        vo.setProductStructure(nzList(dashboardMapper.selectProductStructure(tenantId, storeId)));
        vo.setTop10Products(nzList(dashboardMapper.selectTop10Products(tenantId, storeId)));

        List<StoreTrendPointVo> trend = nzList(dashboardMapper.selectTrend10Days(tenantId, storeId));
        trend.forEach(p -> p.setAvgPrice(avg(p.getSaleAmount(), p.getOrderCount())));
        vo.setTrend10Days(trend);

        return vo;
    }

    @Override
    public StoreDashboardDailyVo getDaily(Long storeId) {
        String tenantId = currentTenant();

        StoreDashboardDailyVo vo = new StoreDashboardDailyVo();
        vo.setPork(buildCategory(dashboardMapper.selectDailyByBelongTypes(tenantId, storeId, PORK_TYPES)));
        vo.setVegetable(buildCategory(dashboardMapper.selectDailyByBelongTypes(tenantId, storeId, VEG_TYPES)));
        vo.setDemandStat(nzList(dashboardMapper.countDemandByStatus(tenantId, storeId)));
        vo.setShipStat(nzList(dashboardMapper.countShipByStatus(tenantId, storeId)));
        return vo;
    }

    /**
     * 单业态速览组装：销售额 + 订单数 + 客单价；退回率 / 损耗率置 null（上游 V1 无数据）。
     *
     * @param row mapper 原始行（可空）
     * @return 速览 VO（row 空时返 empty）
     */
    private StoreDashboardDailyVo.DailyCategoryVo buildCategory(DailyCategoryRow row) {
        if (row == null) {
            return StoreDashboardDailyVo.DailyCategoryVo.empty();
        }
        StoreDashboardDailyVo.DailyCategoryVo vo = new StoreDashboardDailyVo.DailyCategoryVo();
        BigDecimal amount = nzBd(row.getValue());
        int orders = nz(row.getOrderCount());
        vo.setSalesAmount(amount);
        vo.setOrderCount(orders);
        vo.setAvgOrderPrice(avg(amount, orders));
        // 退回率 / 损耗率上游 V1 无退货 / 损耗明细表 → null，前端显示"—"占位
        vo.setReturnRate(null);
        vo.setLossRate(null);
        return vo;
    }

    /**
     * 客单价 = 销售额 / 订单数（订单数 0 时返 0，避免除零），保留 2 位小数。
     *
     * @param amount 销售额（可空）
     * @param orders 订单数
     * @return 客单价
     */
    private BigDecimal avg(BigDecimal amount, Integer orders) {
        int o = nz(orders);
        if (o == 0) {
            return BigDecimal.ZERO;
        }
        return nzBd(amount).divide(BigDecimal.valueOf(o), 2, RoundingMode.HALF_UP);
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
     * null → BigDecimal.ZERO。
     *
     * @param v 可空 BigDecimal
     * @return 非空 BigDecimal（null 视作 ZERO）
     */
    private BigDecimal nzBd(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * null → 可变空列表（forEach 回写客单价需可变）。
     *
     * @param list 可空列表
     * @param <T>  元素类型
     * @return 非空列表
     */
    private <T> List<T> nzList(List<T> list) {
        return list == null ? List.of() : list;
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
            log.warn("[StoreDashboard] 获取租户失败，回退默认租户", e);
            return DEFAULT_TENANT;
        }
    }

}
