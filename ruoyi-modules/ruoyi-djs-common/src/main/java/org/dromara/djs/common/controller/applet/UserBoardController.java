package org.dromara.djs.common.controller.applet;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 小程序板块（养殖 / 种植 / 仓库 / 门店 / 管理）路由 Controller。
 *
 * <p>小程序首屏"板块选择页"调 {@code GET /djs/applet/user/role-tabs} 拿当前用户可见的板块清单，
 * 按<b>菜单权限命名空间</b>推导（ADR-0020；早期按 role_key 硬编码的写法已废）。多角色用户取并集。</p>
 *
 * <h2>板块清单（5 个）</h2>
 *
 * <table>
 *   <tr><th>board</th>      <th>可见角色</th>                                                        <th>首页路由</th></tr>
 *   <tr><td>breed     </td> <td>system_admin / boss / manager / breed_admin / breed_worker / vet</td><td>/pages/breed/index</td></tr>
 *   <tr><td>plant     </td> <td>system_admin / boss / manager / plant_admin / plant_worker</td>     <td>/pages/plant/index</td></tr>
 *   <tr><td>store     </td> <td>store_admin / store_clerk（授 mp 门店权限 11051/11052 的角色）</td>   <td>/pages/store/demand/list/index</td></tr>
 *   <tr><td>warehouse </td> <td>system_admin / boss / manager / warehouse_admin / warehouse_worker</td><td>/pages/warehouse/index</td></tr>
 *   <tr><td>manage    </td> <td>system_admin / boss / manager（管理板块，BI dashboard 聚合）</td>    <td>/pages/breed/dashboard/index</td></tr>
 * </table>
 *
 * <p>门店（store）板块 V6 row62/63 重新上线（V1 曾整体下线）：授权由 mp 权限树 11051 需求下单 /
 * 11052 门店需求·功能 驱动，见 {@code V202608301200__STORE-MP-BOARD-001-menu.sql}。</p>
 *
 * <p>角色源数据：SYS-INIT-002 已 seed 的 12 个业务角色 + ruoyi 自带 superadmin（role_id=1）。
 * 多角色用户取并集（举例：分割师 + 包装工 → breed + warehouse）。superadmin / boss / manager
 * 享有全板块 + 管理板块默认入口。</p>
 *
 * @author djs
 */
@Slf4j
@RestController
@RequestMapping("/djs/applet/user")
@RequiredArgsConstructor
public class UserBoardController {

    /**
     * 拉取当前用户可见的板块清单。
     *
     * <p>响应示例（boss / manager / system_admin 全板块）：</p>
     * <pre>
     * [
     *   {"code":"manage",   "name":"管理","icon":"i-carbon-data-vis-4","route":"/pages/breed/dashboard/index"},
     *   {"code":"breed",    "name":"养殖","icon":"i-carbon-pig",       "route":"/pages/breed/index"},
     *   {"code":"plant",    "name":"种植","icon":"i-carbon-tree",      "route":"/pages/plant/index"},
     *   {"code":"warehouse","name":"仓库","icon":"i-carbon-warehouse", "route":"/pages/warehouse/index"},
     *   {"code":"store",    "name":"门店","icon":"i-carbon-store",     "route":"/pages/store/demand/list/index"}
     * ]
     * </pre>
     *
     * <p>响应顺序约定：manage 排首位（boss/manager 默认入口），其余按 breed → plant → warehouse → store
     * 业务序（供应链自然序；板块页是 2 列网格，门店排末位正好落第三行，V6 row63）。
     * 普通角色（如 breed_worker）只返单板块。</p>
     */
    /**
     * 使用 {@link SaIgnore} 而非 {@code @SaCheckLogin}：V1 mock token（{@code mock-token-*}）
     * 不在 sa-token session 注册，会被 SaCheckLogin 直接拒。本方法内部走 token 前缀分支判定，
     * 缺 token 时返空数组（前端会在 onShow 提示"未登录"再 reLaunch 到登录页）。
     */
    @SaIgnore
    @GetMapping("/role-tabs")
    public R<List<BoardVo>> getRoleTabs() {
        Set<String> boards = resolveBoards();
        List<BoardVo> result = new ArrayList<>();
        // manage 排首位（boss/manager 默认入口，前端按 list[0] 决定默认 board）
        if (boards.contains("manage"))    result.add(new BoardVo("manage",    "管理", "i-carbon-data-vis-4", "/pages/breed/dashboard/index"));
        if (boards.contains("breed"))     result.add(new BoardVo("breed",     "养殖", "i-carbon-pig",        "/pages/breed/index"));
        if (boards.contains("plant"))     result.add(new BoardVo("plant",     "种植", "i-carbon-tree",       "/pages/plant/index"));
        // 仓库板块 4 tab（燎毛间/蔬菜处理/分拣发货/我的），「燎毛间」tab 承担聚合首页角色（屠宰工序 / 蔬菜工序 / 物资 分组卡片）
        if (boards.contains("warehouse")) result.add(new BoardVo("warehouse", "仓库", "i-carbon-warehouse",  "/pages/warehouse/index"));
        // 门店板块排最后（V6 row62/63 重新上线，Kevin 2026-08-09 拍板保留 2 列网格）：
        //   板块选择页是 2 列网格（.dt-boardgrid grid-template-columns: 1fr 1fr），不是原型那种一行一卡。
        //   门店排末位时，看得到多板块的用户（超管 / 全域角色，5 张卡）渲染成
        //   「管理|养殖 / 种植|仓库 / 门店」—— 门店正好落在**第三行**，与甲方 row63「在第三行增加门店」一致；
        //   同时也是供应链的自然序（养殖·种植 → 仓库 → 门店）。
        //   放在种植与仓库之间反而会挤到第二行右格，两条都不满足。
        if (boards.contains("store"))     result.add(new BoardVo("store",     "门店", "i-carbon-store",      "/pages/store/demand/list/index"));
        return R.ok(result);
    }

    /**
     * 角色配置驱动：从当前用户的菜单权限（{@code sys_role_menu} 聚合，即角色管理里勾选的授权）推导可见板块，
     * 不再硬编码 role_key。新增 / 改名角色无需改本类，只要在角色管理里授对应域的权限即可。
     *
     * <ul>
     *   <li>超管（{@code *:*:*} / isSuperAdmin）→ 全板块</li>
     *   <li>权限命名空间含对应域 → 该业务板块可见</li>
     *   <li>拥有全部三业务板块 → 追加 manage（监管全域）</li>
     * </ul>
     */
    private Set<String> resolveBoards() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null) {
            return Collections.emptySet();
        }
        if (LoginHelper.isSuperAdmin()) {
            return Set.of("manage", "breed", "plant", "store", "warehouse");
        }
        return mapPermsToBoards(loginUser.getMenuPermission());
    }

    /**
     * 菜单权限串 → board code（角色配置驱动，多权限取并集）。
     *
     * <p>只认小程序专属命名空间 {@code djs:applet:<域>:*} / {@code djs:mptab:<域>:*}
     * （由 mp 权限子树 11020/11030/11040/11050 授予），<b>不</b>用宽泛的 {@code :域:} 子串——
     * 后者会把 admin 域权限（如 warehouse 里的 {@code djs:breed:*}、store 里的 {@code djs:...warehouse...}）
     * 误判成 mp 板块。</p>
     *
     * <p>「含全部三业务板块 → 追加 manage」的判定<b>只看 breed/plant/warehouse</b>，门店不参与——
     * manage 是养殖/种植/仓库三域监管的 BI 入口，把门店算进去会让「只有门店权限的店员」少一个板块
     * 也无所谓、却让「三域齐全但无门店」的管理者丢掉 manage。</p>
     */
    Set<String> mapPermsToBoards(Set<String> perms) {
        Set<String> boards = new HashSet<>();
        if (perms == null) {
            return boards;
        }
        for (String p : perms) {
            if (p == null) {
                continue;
            }
            if ("*:*:*".equals(p)) {
                boards.addAll(Set.of("manage", "breed", "plant", "store", "warehouse"));
                return boards;
            }
            if (p.startsWith("djs:applet:breed") || p.startsWith("djs:mptab:breed")) {
                boards.add("breed");
            }
            if (p.startsWith("djs:applet:plant") || p.startsWith("djs:mptab:plant")) {
                boards.add("plant");
            }
            if (p.startsWith("djs:applet:store") || p.startsWith("djs:mptab:store")) {
                boards.add("store");
            }
            if (p.startsWith("djs:applet:warehouse") || p.startsWith("djs:mptab:warehouse")) {
                boards.add("warehouse");
            }
        }
        if (boards.contains("breed") && boards.contains("plant") && boards.contains("warehouse")) {
            boards.add("manage");
        }
        return boards;
    }

    /**
     * 板块 VO（保持轻量；放在 controller 内部类避免污染 domain/vo 包）。
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class BoardVo {
        /** 板块编码 */
        private String code;
        /** 中文名 */
        private String name;
        /** 图标（前端 unocss 类名） */
        private String icon;
        /** 板块首页路由（小程序 pages 下各板块的 index.vue） */
        private String route;
    }
}
