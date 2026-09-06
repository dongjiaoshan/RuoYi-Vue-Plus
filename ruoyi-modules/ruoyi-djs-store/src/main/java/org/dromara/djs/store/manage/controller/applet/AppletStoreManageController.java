package org.dromara.djs.store.manage.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.common.store.domain.vo.StorePickerVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageMonthlyVo;
import org.dromara.djs.store.manage.service.IStoreManageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序「管理板块 → 门店管理」Controller（MGMT-MP-STORE-MONTH-001）。
 *
 * <p>URL 前缀 {@code /djs/applet/store/manage}（ADR-0009 applet path 命名）。</p>
 *
 * <h3>权限命名空间为什么是 {@code djs:applet:manage:store:*}</h3>
 * <p>{@code UserBoardController.mapPermsToBoards} 把任何以 {@code djs:applet:store} /
 * {@code djs:mptab:store} 开头的权限判成<b>门店板块</b>（那是给店员的板块）。本页是管理者在
 * <b>管理板块</b>里看的月度看板，若端点权限落进 {@code djs:applet:store:*}，授权给管理者的同时
 * 会让他凭空多出一个门店板块。故端点权限与 tab 显隐权限一起走 {@code manage} 命名空间：</p>
 * <ul>
 *   <li>tab 显隐（前端 manageTabbarList gate）：{@code djs:mptab:manage:store}（sys_menu 11053）</li>
 *   <li>端点：{@code djs:applet:manage:store:*}（sys_menu 11054，挂 11053 下随 tab 一起授/撤）</li>
 * </ul>
 * <p>两串分属不同命名空间，不会被 permMatch 双向通配互相命中（FIX-DJS-PERM-MENU-008 的坑）。</p>
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/store/manage")
public class AppletStoreManageController {

    private final IStoreManageService storeManageService;

    /**
     * 门店下拉候选。前端在列表首位自行补「全部」，后端不返伪门店行。
     *
     * @return 门店精简列表
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:manage:store:list")
    @GetMapping("/stores")
    public R<List<StorePickerVo>> stores() {
        return R.ok(storeManageService.listSelectableStores());
    }

    /**
     * 月度看板：3 个品类数 + 4 张业态卡（猪肉 / 果蔬 / 蛋类 / 干货）。
     *
     * @param storeId 门店 ID；不传 = 全部门店合计
     * @param month   月份 yyyy-MM；不传 = 当月
     * @return 月度看板 VO
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:manage:store:list")
    @GetMapping("/monthly")
    public R<StoreManageMonthlyVo> monthly(@RequestParam(required = false) Long storeId,
                                           @RequestParam(required = false) String month) {
        return R.ok(storeManageService.getMonthly(storeId, month));
    }

}
