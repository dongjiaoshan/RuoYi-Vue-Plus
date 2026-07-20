package org.dromara.djs.warehouse.dashboard.service;

import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardChartsVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehousePorkEfficiencyVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseVegEfficiencyVo;

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
     * 猪只分割效能管理（mp 仓库看板 tab1）：年度屠宰/分割 8 KPI + 月度屠宰效能趋势（柱+3 折线）
     * + 日猪肉处理数据矩阵。消费 WMS-STAT-001 落盘表。
     *
     * <p>无数据时各段 0 / 空列表，不抛错。</p>
     *
     * @param year  统计年份（null = 当年）；年度 KPI + 月度趋势用
     * @param month 统计月份 yyyy-MM（null = 当月）；日矩阵的区间锚点用
     * @return 猪只分割效能 VO
     */
    WarehousePorkEfficiencyVo getPorkEfficiency(Integer year, String month);

    /**
     * 果蔬处理效能管理（mp 仓库看板 tab2）：年度蔬菜处理 6 KPI + 收获量 TOP10 + 损耗率 TOP10
     * + 日蔬菜处理数据矩阵。消费 WMS-STAT-001 落盘表。
     *
     * <p>无数据时各段 0 / 空列表，不抛错。</p>
     *
     * @param year  统计年份（null = 当年）；年度 KPI 用
     * @param month 统计月份 yyyy-MM（null = 当月）；TOP10 + 日矩阵区间用
     * @return 果蔬处理效能 VO
     */
    WarehouseVegEfficiencyVo getVegEfficiency(Integer year, String month);

}
