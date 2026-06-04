package org.dromara.djs.store.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardDailyVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardSummaryVo;
import org.dromara.djs.store.dashboard.service.IStoreDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店看板 Controller（STR-DASH-001，admin PC 门店首页）。
 *
 * <p>6 KPI + 当日产品结构 + 当月 TOP10 + 近 10 日趋势。纯只读聚合，
 * 数据 100% 来自 {@code t_store_sale_record} + {@code t_warehouse_demand_manage}。</p>
 *
 * <p>权限 {@code djs:store:dashboard:view}（menu 10400，绑 store_admin / boss / manager + 超管）。</p>
 *
 * @author djs
 * @since STR-DASH-001
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/store/dashboard")
public class StoreDashboardController extends BaseController {

    private final IStoreDashboardService dashboardService;

    /**
     * 门店看板汇总：6 KPI + 当日产品结构 + 当月 TOP10 + 近 10 日趋势。
     *
     * @param storeId 门店 ID（可选，不传按数据权限范围聚合）
     * @return 看板汇总 VO（无数据时各计数返 0、列表空）
     */
    @SaCheckPermission("djs:store:dashboard:view")
    @GetMapping("/summary")
    public R<StoreDashboardSummaryVo> summary(@RequestParam(required = false) Long storeId) {
        return R.ok(dashboardService.getSummary(storeId));
    }

    /**
     * 门店每日情况一览：猪肉 / 果蔬当日速览 + 需求统计 + 发货统计。
     *
     * @param storeId 门店 ID（可选）
     * @return 每日一览 VO
     */
    @SaCheckPermission("djs:store:dashboard:view")
    @GetMapping("/daily")
    public R<StoreDashboardDailyVo> daily(@RequestParam(required = false) Long storeId) {
        return R.ok(dashboardService.getDaily(storeId));
    }

}
