package org.dromara.djs.warehouse.dashboard.service;

import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardChartsVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseProductionStatVo;

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

    /**
     * 生产管理统计（mp 原型图 26，3 tab：猪肉 / 果蔬 / 发货）。
     *
     * <p>返回该 tab 的年度指标分组 + 月度效能趋势（当年逐月，缺月补 0）。
     * 无数据时各指标 / 趋势点返 0，不抛错。tab 入参非法时回退猪肉 tab。</p>
     *
     * @param tab 业态 tab：pig=猪肉 / veg=果蔬 / ship=发货（大小写不敏感，非法回退 pig）
     * @return 生产管理统计 VO
     */
    WarehouseProductionStatVo getProduction(String tab);

}
