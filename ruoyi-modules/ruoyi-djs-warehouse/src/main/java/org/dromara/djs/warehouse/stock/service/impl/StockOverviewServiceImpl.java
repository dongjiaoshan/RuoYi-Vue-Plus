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
import java.util.List;

/**
 * 库存总览聚合实现（WMS-STOCK-OVERVIEW-001，仓库-admin 行56）。
 *
 * <p>compute-on-read 回放 {@code t_warehouse_stock_flow}（Kevin 拍板），所有 null 用 0 / 空列表兜底。
 * 出库 / 损耗 / 饲喂三列均按 (产品, 库位) 由 mapper 直接算出，口径见 {@link StockOverviewMapper}。</p>
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

        for (StockOverviewDetailVo row : rows) {
            row.setBeginStock(nz(row.getBeginStock()));
            row.setInboundQty(nz(row.getInboundQty()));
            row.setOutboundQty(nz(row.getOutboundQty()));
            row.setLossQty(nz(row.getLossQty()));
            row.setFeedQty(nz(row.getFeedQty()));
            row.setEndStock(nz(row.getEndStock()));
        }
        return rows;
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
