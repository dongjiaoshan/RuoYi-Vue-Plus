package org.dromara.djs.warehouse.dashboard.service;

import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardChartsVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;

/**
 * 仓库看板 Service。
 *
 * @author djs
 */
public interface IWarehouseDashboardService {

    /**
     * 仓库看板汇总：3 KPI 卡 + 库位概览（mp 复用）。
     *
     * <p>无数据时返回全 0 / 空列表，不抛错。</p>
     *
     * @return 看板汇总 VO
     */
    WarehouseDashboardSummaryVo getSummary();

    /**
     * 仓库看板可视化：6 张 ECharts 图 series + 双 KPI 横条 11 指标。
     *
     * <p>无数据时各 series 返空列表、折线全 0、横条指标全 0，不抛错。</p>
     *
     * @return 看板可视化 VO
     */
    WarehouseDashboardChartsVo getCharts();

}
