package org.dromara.djs.plant.dashboard.service;

import org.dromara.djs.plant.dashboard.domain.vo.GanttItemVo;
import org.dromara.djs.plant.dashboard.domain.vo.PlantDashboardSummaryVo;

import java.util.List;

/**
 * 种植看板 Service（PLT-DASH-001）。
 *
 * <p>纯只读聚合，无写、无事务、无 listener。无数据时返回全 0 / 空列表，不抛错。</p>
 *
 * @author djs
 * @since PLT-DASH-001
 */
public interface IPlantDashboardService {

    /**
     * 种植看板汇总：土地总览 + 今日农事 + 当月完成率。
     *
     * @return 看板汇总 VO（无数据时各计数返 0、列表空）
     */
    PlantDashboardSummaryVo getSummary();

    /**
     * 双甘特图数据：每条种植明细拆成种植段（plant）/ 采摘段（pick）。
     *
     * <p>日期为 null 的段跳过（未开始种植 / 未开始采摘不画条）。</p>
     *
     * @return 甘特条列表（无数据时空列表）
     */
    List<GanttItemVo> getGantt();

}
