package org.dromara.djs.plant.dashboard.applet.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.dashboard.applet.domain.vo.CropAreaShareVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.MonthYieldRankVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PickRecordVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantAnnualPlanVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantManageOverviewVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantPlanTimelineVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantRecordVo;
import org.dromara.djs.plant.dashboard.applet.service.IAppletPlantManageDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * mp 管理·种植管理看板 + 种植/采摘计划子页 Controller（FIX-MGMT-MP-PLT-001）。
 *
 * <p>独立 mp 看板链（不复用 admin {@code /djs/plant/dashboard} A 版口径 + perm 墙；不碰 worker 包）。
 * 纯只读聚合，8 端点。{@code @SaCheckLogin} 走 sa-token 真路（mp V1 mock 登录已发真 token，参 ADR-0003），
 * 不挂 {@code @SaCheckPermission}（mp 管理端无 admin 菜单 perm）。</p>
 *
 * <h2>8 端点</h2>
 * <ul>
 *   <li>{@code GET overview}                       农场种植情况一览 6 KPI</li>
 *   <li>{@code GET annualPlan?year}                年度果蔬规划与执行 6 KPI</li>
 *   <li>{@code GET cropAreaShare}                  果蔬分布饼图</li>
 *   <li>{@code GET yieldRank3m}                    后三月产量排行（作物 × 月）</li>
 *   <li>{@code GET planTimeline?year}              种植计划时间轴</li>
 *   <li>{@code GET planRecords?month&cropId}       种植记录列表（分页）</li>
 *   <li>{@code GET pickTimeline?year}              采摘计划时间轴</li>
 *   <li>{@code GET pickRecords?month&cropId&pickType} 采摘记录列表（分页）</li>
 * </ul>
 *
 * @author djs
 * @since FIX-MGMT-MP-PLT-001
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/plant/dashboard")
public class AppletPlantManageDashboardController extends BaseController {

    private final IAppletPlantManageDashboardService dashboardService;

    /**
     * 农场种植情况一览 6 KPI。
     *
     * @return overview VO（无数据时各计数 0 / 面积 0）
     */
    @GetMapping("/overview")
    public R<PlantManageOverviewVo> overview() {
        return R.ok(dashboardService.getOverview());
    }

    /**
     * 年度果蔬规划与执行 6 KPI。
     *
     * @param year 计划年份（空时取当前年）
     * @return annualPlan VO
     */
    @GetMapping("/annualPlan")
    public R<PlantAnnualPlanVo> annualPlan(@RequestParam(required = false) Integer year) {
        return R.ok(dashboardService.getAnnualPlan(year));
    }

    /**
     * 当前农场果蔬分布（饼图）。
     *
     * @return 作物面积片列表
     */
    @GetMapping("/cropAreaShare")
    public R<List<CropAreaShareVo>> cropAreaShare() {
        return R.ok(dashboardService.getCropAreaShare());
    }

    /**
     * 后三个月果蔬预计产量排行（作物 × 月）。
     *
     * @return 排行格列表
     */
    @GetMapping("/yieldRank3m")
    public R<List<MonthYieldRankVo>> yieldRank3m() {
        return R.ok(dashboardService.getYieldRank3m());
    }

    /**
     * 种植计划时间轴。
     *
     * @param year 计划年份（空时取当前年）
     * @return 月度时间轴列表
     */
    @GetMapping("/planTimeline")
    public R<List<PlantPlanTimelineVo>> planTimeline(@RequestParam(required = false) Integer year) {
        return R.ok(dashboardService.getPlanTimeline(year));
    }

    /**
     * 种植记录列表（分页）。
     *
     * @param month     月份过滤（可空）
     * @param cropId    作物过滤（可空）
     * @param year      年份过滤（可空 → 不限年份，跨年"去年记录"用）
     * @param pageQuery 分页参数
     * @return 种植记录分页
     */
    @GetMapping("/planRecords")
    public TableDataInfo<PlantRecordVo> planRecords(@RequestParam(required = false) Integer month,
                                                    @RequestParam(required = false) Long cropId,
                                                    @RequestParam(required = false) Integer year,
                                                    PageQuery pageQuery) {
        return dashboardService.getPlanRecords(month, cropId, year, pageQuery);
    }

    /**
     * 采摘计划时间轴。
     *
     * @param year 年份（空时取当前年）
     * @return 月度时间轴列表
     */
    @GetMapping("/pickTimeline")
    public R<List<PlantPlanTimelineVo>> pickTimeline(@RequestParam(required = false) Integer year) {
        return R.ok(dashboardService.getPickTimeline(year));
    }

    /**
     * 采摘记录列表（分页）。
     *
     * @param month     月份过滤（可空）
     * @param cropId    作物过滤（可空）
     * @param pickType  采摘类型过滤 is_pick（1=活动 / 2=基地，可空）
     * @param year      年份过滤（可空 → 不限年份，跨年"去年记录"用）
     * @param pageQuery 分页参数
     * @return 采摘记录分页
     */
    @GetMapping("/pickRecords")
    public TableDataInfo<PickRecordVo> pickRecords(@RequestParam(required = false) Integer month,
                                                   @RequestParam(required = false) Long cropId,
                                                   @RequestParam(required = false) Integer pickType,
                                                   @RequestParam(required = false) Integer year,
                                                   PageQuery pageQuery) {
        return dashboardService.getPickRecords(month, cropId, pickType, year, pageQuery);
    }

}
