package org.dromara.djs.plant.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.dashboard.domain.vo.GanttItemVo;
import org.dromara.djs.plant.dashboard.domain.vo.PlantDashboardSummaryVo;
import org.dromara.djs.plant.dashboard.service.IPlantDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 种植看板 Controller（PLT-DASH-001）。
 *
 * <p>admin 种植首页（A 版）：土地总览 + 今日农事 KPI + 当月完成率柱状图 + 双甘特图。
 * 纯只读聚合，2 个端点，无写、无事务。前端 5 分钟轮询。</p>
 *
 * @author djs
 * @since PLT-DASH-001
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/plant/dashboard")
public class PlantDashboardController extends BaseController {

    private final IPlantDashboardService dashboardService;

    /**
     * 种植看板汇总：土地总览 KPI + 今日农事 KPI + 当月完成率柱状图数据。
     *
     * @return 看板汇总 VO（无数据时各计数返 0、列表空）
     */
    @SaCheckPermission("djs:plant:dashboard:view")
    @GetMapping("/summary")
    public R<PlantDashboardSummaryVo> summary() {
        return R.ok(dashboardService.getSummary());
    }

    /**
     * 双甘特图数据：种植段（绿）+ 采摘段（黄）。
     *
     * @return 甘特条列表（无数据时空数组）
     */
    @SaCheckPermission("djs:plant:dashboard:view")
    @GetMapping("/gantt")
    public R<List<GanttItemVo>> gantt() {
        return R.ok(dashboardService.getGantt());
    }

}
