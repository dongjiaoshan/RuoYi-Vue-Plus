package org.dromara.djs.store.dashboard.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardDailyVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardSummaryVo;
import org.dromara.djs.store.dashboard.service.IStoreDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序门店看板 Controller（STR-DASH-001，mp 门店店员视角）。
 *
 * <p>URL 前缀 {@code /djs/applet/store/dashboard}（ADR-0009 applet path 命名）。
 * mp 用户角色不含 admin 的 {@code djs:store:dashboard:view} 权限，无法直连 admin 端点
 * （sa-token perm 拦截 403），故 mp 走独立 applet 端点 + {@code @SaCheckLogin}，
 * 复用同一 {@link IStoreDashboardService}（克隆 W22-006 applet 范式）。</p>
 *
 * <p>门店隔离走显式 {@code storeId} 入参（店员在 mp 选门店；V1 不做行级拦截器，
 * 克隆 AppletStoreDemandController 范式）；null 时按数据权限范围聚合。</p>
 *
 * @author djs
 * @since STR-DASH-001
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/store/dashboard")
public class AppletStoreDashboardController {

    private final IStoreDashboardService dashboardService;

    /**
     * mp 门店每日情况一览：猪肉 / 果蔬当日速览 + 需求统计 + 发货统计。
     *
     * @param storeId 门店 ID（可选，店员显式传；null 时按范围聚合）
     * @return 每日一览 VO
     */
    @SaCheckLogin
    @GetMapping("/daily")
    public R<StoreDashboardDailyVo> daily(@RequestParam(required = false) Long storeId) {
        return R.ok(dashboardService.getDaily(storeId));
    }

    /**
     * mp 门店看板汇总：6 KPI（mp 一般展示销售 KPI，VO 仍含列表，前端按需取）。
     *
     * @param storeId 门店 ID（可选）
     * @return 看板汇总 VO
     */
    @SaCheckLogin
    @GetMapping("/summary")
    public R<StoreDashboardSummaryVo> summary(@RequestParam(required = false) Long storeId) {
        return R.ok(dashboardService.getSummary(storeId));
    }

}
