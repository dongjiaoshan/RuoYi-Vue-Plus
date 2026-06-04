package org.dromara.djs.warehouse.dashboard.service;

import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;

/**
 * 仓库看板 Service（DJS-FIX-ADMIN-W22-006 占位版）。
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-006
 */
public interface IWarehouseDashboardService {

    /**
     * 仓库看板汇总：3 KPI 卡 + 库位概览。
     *
     * <p>无数据时返回全 0 / 空列表，不抛错。</p>
     *
     * @return 看板汇总 VO
     */
    WarehouseDashboardSummaryVo getSummary();

}
