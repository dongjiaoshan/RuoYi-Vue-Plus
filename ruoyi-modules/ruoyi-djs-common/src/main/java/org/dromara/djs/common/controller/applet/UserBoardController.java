package org.dromara.djs.common.controller.applet;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
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
 * 小程序板块（养殖 / 种植 / 仓库 / 门店）路由 Controller。
 *
 * <p>小程序首屏"板块选择页"调 {@code GET /djs/applet/user/role-tabs} 拿当前用户可见的板块清单，
 * 按 sys_role.role_key 取并集（多角色用户合并各自能进的板块）。</p>
 *
 * <h2>角色 → 板块映射规则</h2>
 *
 * <table>
 *   <tr><th>board</th>      <th>可见角色</th></tr>
 *   <tr><td>breed     </td> <td>system_admin / boss / manager / breed_admin / breed_worker / vet</td></tr>
 *   <tr><td>plant     </td> <td>system_admin / boss / manager / plant_admin / plant_worker</td></tr>
 *   <tr><td>warehouse </td> <td>system_admin / boss / manager / warehouse_admin / warehouse_worker</td></tr>
 *   <tr><td>store     </td> <td>system_admin / boss / manager / store_admin / store_clerk</td></tr>
 *   <tr><td>dashboard </td> <td>system_admin / boss / manager（V2 加 BI 大屏后启用）</td></tr>
 * </table>
 *
 * <p>角色源数据：SYS-INIT-002 已 seed 的 12 个业务角色 + ruoyi 自带 superadmin（role_id=1）。
 * 多角色用户取并集（举例：分割师 + 包装工 → breed + warehouse）。</p>
 *
 * @author djs
 */
@Slf4j
@RestController
@RequestMapping("/djs/applet/user")
@RequiredArgsConstructor
public class UserBoardController {

    /** ruoyi 超级管理员（sys_role.role_id=1, role_key 不固定，靠 isSuperAdmin 判定） */
    private static final Set<String> ADMIN_ROLES = Set.of("system_admin", "boss", "manager", "admin");
    private static final Set<String> BREED_ROLES = Set.of("breed_admin", "breed_worker", "vet");
    private static final Set<String> PLANT_ROLES = Set.of("plant_admin", "plant_worker");
    private static final Set<String> WAREHOUSE_ROLES = Set.of("warehouse_admin", "warehouse_worker");
    private static final Set<String> STORE_ROLES = Set.of("store_admin", "store_clerk");

    /**
     * 拉取当前用户可见的板块清单。
     *
     * <p>响应示例（多角色用户：breed_admin + store_clerk）：</p>
     * <pre>
     * [
     *   {"code":"breed","name":"养殖","icon":"i-carbon-pig","route":"/pages/breed/index"},
     *   {"code":"store","name":"门店","icon":"i-carbon-store","route":"/pages/store/index"}
     * ]
     * </pre>
     */
    /**
     * 使用 {@link SaIgnore} 而非 {@code @SaCheckLogin}：V1 mock token（{@code mock-token-*}）
     * 不在 sa-token session 注册，会被 SaCheckLogin 直接拒。本方法内部走 token 前缀分支判定，
     * 缺 token 时返空数组（前端会在 onShow 提示"未登录"再 reLaunch 到登录页）。
     */
    @SaIgnore
    @GetMapping("/role-tabs")
    public R<List<BoardVo>> getRoleTabs() {
        Set<String> roleKeys = resolveRoleKeys();
        Set<String> boards = mapRolesToBoards(roleKeys);
        List<BoardVo> result = new ArrayList<>();
        if (boards.contains("breed"))     result.add(new BoardVo("breed",     "养殖", "i-carbon-pig",       "/pages/breed/index"));
        if (boards.contains("plant"))     result.add(new BoardVo("plant",     "种植", "i-carbon-tree",      "/pages/plant/index"));
        if (boards.contains("warehouse")) result.add(new BoardVo("warehouse", "仓库", "i-carbon-warehouse", "/pages/warehouse/index"));
        if (boards.contains("store"))     result.add(new BoardVo("store",     "门店", "i-carbon-store",     "/pages/store/index"));
        // dashboard V2 启用，V1 不返
        return R.ok(result);
    }

    /**
     * 解析当前用户的 role_key 集合，兼容三种来源：
     * <ol>
     *   <li>普通登录用户 → {@code LoginHelper.getLoginUser().rolePermission}</li>
     *   <li>mock token（{@code mock-token-*}） → 按 token 后缀硬编码（与 AppletAuthController 对齐）</li>
     *   <li>ruoyi 超管 → 注入 {@code "system_admin"} 让其能看到全部板块</li>
     * </ol>
     */
    private Set<String> resolveRoleKeys() {
        String token = StpUtil.getTokenValue();
        if (token != null && token.startsWith("mock-token-")) {
            if ("mock-token-9001".equals(token)) {
                return Set.of("breed_admin", "plant_admin", "warehouse_admin", "store_admin");
            }
            if ("mock-token-1".equals(token)) {
                return Set.of("system_admin");
            }
            return Set.of("breed_admin");
        }

        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null) {
            return Collections.emptySet();
        }
        Set<String> keys = new HashSet<>();
        if (loginUser.getRolePermission() != null) {
            keys.addAll(loginUser.getRolePermission());
        }
        if (LoginHelper.isSuperAdmin()) {
            keys.add("system_admin");
        }
        return keys;
    }

    /**
     * 将 role_key 集合映射为 board code 集合（多角色取并集）。
     */
    private Set<String> mapRolesToBoards(Set<String> roleKeys) {
        Set<String> boards = new HashSet<>();
        if (roleKeys.stream().anyMatch(ADMIN_ROLES::contains)) {
            return Set.of("breed", "plant", "warehouse", "store");
        }
        if (roleKeys.stream().anyMatch(BREED_ROLES::contains))     boards.add("breed");
        if (roleKeys.stream().anyMatch(PLANT_ROLES::contains))     boards.add("plant");
        if (roleKeys.stream().anyMatch(WAREHOUSE_ROLES::contains)) boards.add("warehouse");
        if (roleKeys.stream().anyMatch(STORE_ROLES::contains))     boards.add("store");
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
