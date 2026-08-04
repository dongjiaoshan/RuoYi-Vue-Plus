package org.dromara.djs.warehouse.stock.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.stock.domain.vo.StockOverviewDailyVo;
import org.dromara.djs.warehouse.stock.domain.vo.StockOverviewDetailVo;
import org.dromara.djs.warehouse.stock.domain.vo.StockOverviewMonthlyVo;
import org.dromara.djs.warehouse.stock.mapper.StockOverviewMapper;
import org.dromara.djs.warehouse.stock.service.IStockOverviewService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存总览聚合实现（WMS-STOCK-OVERVIEW-001，仓库-admin 行56）。
 *
 * <p>compute-on-read 回放 {@code t_warehouse_stock_flow}（Kevin 拍板），所有 null 用 0 / 空列表兜底。
 * 损耗（loss_flow）/ 饲喂（feed_log）按产品归集后只挂到该产品第一行（多库位不重复计数）。</p>
 *
 * @author djs
 * @since WMS-STOCK-OVERVIEW-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockOverviewServiceImpl implements IStockOverviewService {

    private static final String DEFAULT_TENANT = "1001";

    private final StockOverviewMapper stockOverviewMapper;

    @Override
    public List<StockOverviewDailyVo> queryDaily(Date dateFrom, Date dateTo) {
        String tenantId = currentTenant();
        List<StockOverviewDailyVo> list = stockOverviewMapper.selectDailyOverview(tenantId, dateFrom, dateTo);
        if (list == null) {
            return List.of();
        }
        for (StockOverviewDailyVo vo : list) {
            if (vo.getProductCount() == null) {
                vo.setProductCount(0);
            }
        }
        return list;
    }

    @Override
    public List<StockOverviewDetailVo> queryDetail(String date, String productName, Long locationId) {
        String tenantId = currentTenant();

        List<StockOverviewDetailVo> rows = stockOverviewMapper.selectDailyDetail(tenantId, date, productName, locationId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        // 产品维度损耗 / 饲喂归集（按 productId）
        Map<String, BigDecimal> lossMap = toProductMap(stockOverviewMapper.selectDailyLossByProduct(tenantId, date), true);
        Map<String, BigDecimal> feedMap = toProductMap(stockOverviewMapper.selectDailyFeedByProduct(tenantId, date), false);

        // 已挂过损耗 / 饲喂的产品（避免多库位行重复计数）
        Map<String, Boolean> lossAttached = new HashMap<>();
        Map<String, Boolean> feedAttached = new HashMap<>();

        for (StockOverviewDetailVo row : rows) {
            row.setBeginStock(nz(row.getBeginStock()));
            row.setInboundQty(nz(row.getInboundQty()));
            row.setOutboundQty(nz(row.getOutboundQty()));
            row.setEndStock(nz(row.getEndStock()));

            String pid = row.getProductId();
            // 损耗：只挂到该产品第一条行，其余行 0（信息列，期末不二次扣）
            if (pid != null && lossMap.containsKey(pid) && !Boolean.TRUE.equals(lossAttached.get(pid))) {
                row.setLossQty(nz(lossMap.get(pid)));
                lossAttached.put(pid, Boolean.TRUE);
            } else {
                row.setLossQty(BigDecimal.ZERO);
            }
            // 饲喂：同上
            if (pid != null && feedMap.containsKey(pid) && !Boolean.TRUE.equals(feedAttached.get(pid))) {
                row.setFeedQty(nz(feedMap.get(pid)));
                feedAttached.put(pid, Boolean.TRUE);
            } else {
                row.setFeedQty(BigDecimal.ZERO);
            }
        }
        return rows;
    }

    /**
     * 把按产品聚合的 loss/feed 行转 productId → 量 map。
     *
     * @param list  mapper 返回（productId + lossQty/feedQty）
     * @param isLoss true 取 lossQty，false 取 feedQty
     * @return productId → BigDecimal
     */
    private Map<String, BigDecimal> toProductMap(List<StockOverviewDetailVo> list, boolean isLoss) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (list == null) {
            return map;
        }
        for (StockOverviewDetailVo v : list) {
            if (v.getProductId() == null) {
                continue;
            }
            BigDecimal val = isLoss ? v.getLossQty() : v.getFeedQty();
            map.put(v.getProductId(), nz(val));
        }
        return map;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 当前租户（V1 固定 '1001'）。
     */
    private String currentTenant() {
        String t = TenantHelper.getTenantId();
        return (t == null || t.isBlank()) ? DEFAULT_TENANT : t;
    }


    @Override
    public List<StockOverviewMonthlyVo> queryMonthly(Date monthFrom, Date monthTo) {
        return stockOverviewMapper.selectMonthlyOverview(TenantHelper.getTenantId(), monthFrom, monthTo);
    }

    @Override
    public List<StockOverviewDetailVo> queryMonthlyDetail(Date monthStart, String productName, Long locationId) {
        return stockOverviewMapper.selectMonthlyDetail(TenantHelper.getTenantId(), monthStart, productName, locationId);
    }
}
