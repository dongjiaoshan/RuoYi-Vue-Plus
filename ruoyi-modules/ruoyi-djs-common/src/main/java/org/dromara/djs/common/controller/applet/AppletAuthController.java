package org.dromara.djs.common.controller.applet;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.PermissionService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.constant.DjsAuthConstants;
import org.dromara.djs.common.domain.bo.WechatBindPhoneBo;
import org.dromara.djs.common.domain.bo.WechatLoginBo;
import org.dromara.djs.common.domain.vo.WechatLoginVo;
import org.dromara.djs.common.service.IWechatLoginService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 小程序 applet 认证 Controller。
 *
 * <p>URL 前缀 {@code /applet/auth/*}，与 ruoyi 自带 {@code /auth/*}（admin）和 djs
 * {@code /djs/auth/wechat/*}（SYS-AUTH-001 原始路径）区分开。本 Controller 仅为小程序
 * 端口前端 unibest 提供"统一前缀"封装，内部职责：</p>
 *
 * <ul>
 *   <li>{@code POST /applet/auth/login}    — 账号密码登录（V1 mock 模式）</li>
 *   <li>{@code POST /applet/auth/wxLogin}  — 微信小程序登录（代理 SYS-AUTH-001 服务）</li>
 *   <li>{@code GET  /applet/auth/getInfo}  — 当前登录用户信息（驱动板块列表）</li>
 *   <li>{@code POST /applet/auth/logout}   — 退出登录</li>
 * </ul>
 *
 * <h2>Mock 模式说明</h2>
 *
 * <p>V1 真实小程序 AppID 尚未申请到位。前端 manifest 占位 {@code wxMOCKMOCKMOCKMOCK}，
 * 后端通过开关 {@code djs.applet.auth-mock-enabled=true} 启用 mock 路径：</p>
 *
 * <ul>
 *   <li>账号密码 login：接受 {@link #MOCK_ACCOUNTS} 白名单内任一账号（dev / admin / dev_boss /
 *       dev_breed_worker / dev_warehouse_worker / dev_store_clerk / dev_vet / dev_breed_mgr /
 *       dev_warehouse_mgr），命中白名单<b>且该 userId 在 sys_user 存在 + 启用 + 未删</b>才颁发
 *       sa-token，token 绑定 client_id={@code mp-applet-dongjiaoshan}</li>
 *   <li>wxLogin：完全代理到 SYS-AUTH-001 的 {@link IWechatLoginService}，复用其内置
 *       mock 模式（{@code djs.wechat.miniapp.app-id=mock}）</li>
 * </ul>
 *
 * <p><b>两道独立的关停手段（任一生效即登不进）：</b></p>
 * <ol>
 *   <li><b>开关层</b>：{@code djs.applet.auth-mock-enabled=false} 关掉整条白名单
 *       （注意关开关<b>不</b>直接拒绝请求，而是 fallthrough 到真实 BCrypt 登录）；</li>
 *   <li><b>账号层</b>：把 sys_user 该行置 {@code status='1'} 或 {@code del_flag='2'}
 *       —— 白名单命中后的账号状态闸会拒发 token，与密码错误同款提示。</li>
 * </ol>
 *
 * @author djs
 */
@Slf4j
@RestController
@RequestMapping("/applet/auth")
@RequiredArgsConstructor
public class AppletAuthController {

    private final IWechatLoginService wechatLoginService;

    /**
     * 菜单权限装配（与 ruoyi SysLoginService / WechatLoginServiceImpl 一致）。
     *
     * <p>mock 登录也走真 PermissionService 按 userId 读 DB 菜单权限，替代历史 {@code *:*:*} 通配——
     * 通配会掩盖按板块差异化授权（DJS-PERM-BOARD-001），导致 dev 账号测不出越权 403。mock 账号
     * userId（9001 / 9100-9109 / 1）均已 seed sys_user + sys_user_role，能解析到真实角色权限。</p>
     */
    private final PermissionService permissionService;

    /**
     * 是否启用 mock 账号密码登录。
     *
     * <p>V1 dev 阶段默认启用 ({@code djs.applet.auth-mock-enabled=true})；上生产前 Kevin
     * 必须显式改 false，否则下面的 mock login 端点会让任意人用 dev/dev123 进入系统
     * （前提是那些账号在 sys_user 里仍是启用状态，见 login 里的账号状态闸）。</p>
     */
    @Value("${djs.applet.auth-mock-enabled:true}")
    private boolean authMockEnabled;

    /**
     * V1 mock 账号白名单（username → {userId, password, nickname, roles}）。
     *
     * <p>新加调试账号在此处加一行即可，无需改 login 方法。userId 对齐 sys_user D04 dev seed
     * （[`V202605211700__D04-dev-seed-users.sql`] 9100-9107 段）。</p>
     *
     * <p><b>userId 必须指向 sys_user 里存在 + 启用 + 未删的真行</b>：login 的账号状态闸按该 userId
     * 查库，占位 / 已停用 / 已删除的 userId 一律登不进（这也是账号层停用测试账号的生效点）。
     * 另外 {@link #buildMockLoginUser} 按 userId 读真菜单权限，占位 id 会拿到空权限集。</p>
     *
     * <p>角色 key 与 {@code UserBoardController.mapRolesToBoards} 一致：boss/manager → 全板块
     * 含 manage；breed_worker/breed_admin → breed；warehouse_* → warehouse；store_* V1 无板块。</p>
     */
    private static final Map<String, MockAccount> MOCK_ACCOUNTS = Map.ofEntries(
        Map.entry("dev",                  new MockAccount(9001L, "dev123",   "dev 员工",
            Set.of("breed_admin", "plant_admin", "warehouse_admin", "store_admin"))),
        Map.entry("admin",                new MockAccount(1L,    "admin123", "超级管理员",
            Set.of("system_admin"))),
        Map.entry("dev_boss",             new MockAccount(9107L, "dev123",   "老板（dev 测试）",
            Set.of("boss"))),
        Map.entry("dev_breed_worker",     new MockAccount(9101L, "dev123",   "张三（养殖工人）",
            Set.of("breed_worker"))),
        Map.entry("dev_warehouse_worker", new MockAccount(9108L, "dev123",   "孙仓库（仓库工人）",
            Set.of("warehouse_worker"))),
        Map.entry("dev_store_clerk",      new MockAccount(9106L, "dev123",   "孙小妹（门店店员）",
            Set.of("store_clerk"))),
        Map.entry("dev_vet",              new MockAccount(9102L, "dev123",   "王兽医",
            Set.of("vet"))),
        Map.entry("dev_breed_mgr",        new MockAccount(9100L, "dev123",   "李养殖（养殖管理员）",
            Set.of("breed_admin"))),
        Map.entry("dev_warehouse_mgr",    new MockAccount(9104L, "dev123",   "赵仓库（仓库管理员）",
            Set.of("warehouse_admin"))),
        Map.entry("dev_plant_mgr",        new MockAccount(9103L, "dev123",   "陈种植（种植管理员）",
            Set.of("plant_admin"))),
        Map.entry("dev_plant_worker",     new MockAccount(9109L, "dev123",   "种植工人（dev 测试）",
            Set.of("plant_worker")))
    );

    /** mock 账号条目（不导出包外）。 */
    private record MockAccount(Long userId, String password, String nickname, Set<String> roles) {}

    /**
     * 账号密码登录（V1 mock 模式）。
     *
     * <p>请求体：</p>
     * <pre>
     * POST /applet/auth/login
     * { "username": "dev", "password": "dev123", "clientId": "mp-applet-dongjiaoshan" }
     * </pre>
     *
     * <p>响应：</p>
     * <ul>
     *   <li>200 + {@link WechatLoginVo}（含 access_token、currentFarmId、userId）→ 登录成功</li>
     *   <li>40003 + message → 账号或密码错误 / 账号已停用或已删除（同一文案，防账号枚举）</li>
     * </ul>
     *
     * <p>登录顺序：① dev mock 白名单（仅 {@code authMockEnabled} 时，dev/dev123 多角色快速登录）；
     * ② 真实员工账号密码登录（走 sys_user + BCrypt，任何环境都可用——员工用登记账号 + 真实密码登录）。
     * 白名单未命中即落到真实员工登录，二者互不干扰。</p>
     *
     * <p><b>两条路径都受账号层状态约束</b>：② 的 {@code selectAuthByUsername} 自带
     * {@code status='0' AND del_flag='0'} 过滤；① 白名单命中后显式过
     * {@link IWechatLoginService#assertUserLoginable(Long)}（查 sys_user 存在 + 启用 + 未删），
     * 不满足直接拒、<b>不</b> fallthrough 到 ②。白名单是内存常量，不加这道闸的话账号层停用
     * （{@code status='1'} / {@code del_flag='2'} / 改密）对 mock 路径零效果。</p>
     */
    @SaIgnore
    @PostMapping("/login")
    public R<WechatLoginVo> login(@Valid @RequestBody AppletPasswordLoginBo bo) {
        try {
            // ① dev mock 白名单：dev/dev123 等多角色快速登录（仅 authMockEnabled 环境）
            if (authMockEnabled) {
                MockAccount acc = MOCK_ACCOUNTS.get(bo.getUsername());
                if (acc != null && acc.password().equals(bo.getPassword())) {
                    // 账号状态闸：账号层停用 / 删除的 mock 账号一律不发 token
                    wechatLoginService.assertUserLoginable(acc.userId());
                    return R.ok(issueMockToken(bo.getUsername(), acc));
                }
            }
            // ② 真实员工账号密码登录（sys_user + BCrypt，任何环境可用）
            return R.ok(wechatLoginService.employeePasswordLogin(bo.getUsername(), bo.getPassword(), bo.getClientId()));
        } catch (ServiceException e) {
            int code = e.getCode() != null ? e.getCode() : DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR;
            return R.fail(code, e.getMessage());
        }
    }

    /**
     * dev mock 账号颁发真 sa-token（多角色快速登录）。
     *
     * <p>构造 LoginUser → LoginHelper.login → 拿真 JWT token，后续 /djs/* 都能正常解析。
     * 单测环境 sa-token 上下文未就绪时 fallback 到 {@code mock-token-{userId}} 字符串保持向后兼容。</p>
     */
    private WechatLoginVo issueMockToken(String username, MockAccount acc) {
        LoginUser loginUser = buildMockLoginUser(acc.userId(), username, acc.nickname(), acc.roles());
        SaLoginParameter param = new SaLoginParameter()
            .setDeviceType("mp")
            .setExtra(LoginHelper.CLIENT_KEY, DjsAuthConstants.MP_APPLET_CLIENT_ID);
        String token;
        try {
            LoginHelper.login(loginUser, param);
            token = StpUtil.getTokenValue();
        } catch (Exception e) {
            log.warn("[applet-auth-mock] sa-token 上下文不可用，fallback 到字符串 token：{}", e.getMessage());
            token = "mock-token-" + acc.userId();
        }
        WechatLoginVo vo = new WechatLoginVo();
        vo.setAccessToken(token);
        vo.setExpireIn(7200L);
        vo.setUserId(acc.userId());
        vo.setNickName(acc.nickname());
        vo.setOpenid("mock-openid-" + acc.userId());
        vo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);
        vo.setCurrentFarmId(DjsAuthConstants.DEFAULT_FARM_ID);
        log.info("[applet-auth-mock] password login ok username={} userId={} token={}", username, acc.userId(), token);
        return vo;
    }

    /**
     * 构造 mock LoginUser（loginId / tenantId / userType / roles 与 ruoyi sys_user 对齐）。
     *
     * <p>菜单权限走真 {@link PermissionService#getMenuPermission(Long)} 按 userId 读 DB，与真微信登录
     * （{@code WechatLoginServiceImpl.issueToken}）一致，使 dev mock 账号也受按板块差异化授权约束、能在
     * dev 测出越权 403。rolePermission 仍用 MOCK_ACCOUNTS 显式 roles（板块映射 UserBoardController 读它，
     * 保持既有行为）。生产模式下 mock 路径自动失效（authMockEnabled=false），不会走到此方法。</p>
     */
    private LoginUser buildMockLoginUser(Long userId, String username, String nickname, Set<String> roles) {
        LoginUser u = new LoginUser();
        u.setUserId(userId);
        u.setUsername(username);
        u.setNickname(nickname);
        u.setTenantId(DjsAuthConstants.DEFAULT_FARM_ID);
        u.setUserType("sys_user");
        u.setRolePermission(roles);
        u.setMenuPermission(permissionService.getMenuPermission(userId));
        u.setDeptId(100L);
        u.setDeptName("东角山农场");
        return u;
    }

    /**
     * 微信小程序登录（代理 {@link IWechatLoginService}）。
     *
     * <p>实际逻辑由 SYS-AUTH-001 实现，本端点仅提供 {@code /applet/auth/*} 统一前缀。</p>
     */
    @SaIgnore
    @PostMapping("/wxLogin")
    public R<WechatLoginVo> wxLogin(@Valid @RequestBody WechatLoginBo bo) {
        try {
            return R.ok(wechatLoginService.wechatLogin(bo));
        } catch (ServiceException e) {
            int code = e.getCode() != null ? e.getCode() : 500;
            return R.fail(code, e.getMessage());
        }
    }

    /**
     * 首次绑定手机号（代理 {@link IWechatLoginService}）。
     */
    @SaIgnore
    @PostMapping("/bindPhone")
    public R<WechatLoginVo> bindPhone(@Valid @RequestBody WechatBindPhoneBo bo) {
        try {
            return R.ok(wechatLoginService.bindPhone(bo));
        } catch (ServiceException e) {
            int code = e.getCode() != null ? e.getCode() : 500;
            return R.fail(code, e.getMessage());
        }
    }

    /**
     * 获取当前登录用户信息（驱动小程序首屏的 nickname / roles / boards 渲染）。
     *
     * <p>响应：</p>
     * <pre>
     * { "userId": 9001, "username": "dev", "nickname": "dev 员工",
     *   "roles": ["breed_admin", "store_clerk"],
     *   "permissions": ["djs:applet:warehouse:vegHandle:handle", "djs:applet:warehouse:mat:pick"] }
     * </pre>
     *
     * <p>{@code permissions} = 该用户经 sys_role_menu 聚合的菜单 perms 集（含 djs:applet:* 等），
     * 小程序据此按权限隐藏 tab / 卡片。</p>
     */
    @GetMapping("/getInfo")
    public R<Map<String, Object>> getInfo() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null) {
            return R.fail(401, "未登录");
        }
        Map<String, Object> info = new HashMap<>();
        info.put("userId", loginUser.getUserId());
        info.put("username", loginUser.getUsername());
        info.put("nickname", loginUser.getNickname());
        Set<String> roleKeys = loginUser.getRolePermission();
        info.put("roles", roleKeys != null ? roleKeys : Collections.emptySet());
        Set<String> menuPerms = loginUser.getMenuPermission();
        info.put("permissions", menuPerms != null ? menuPerms : Collections.emptySet());
        return R.ok(info);
    }

    /**
     * 退出登录（走真 sa-token）。
     *
     * <p>{@code @SaIgnore} + try-catch 兜底：允许"未登录态"也能调（前端清本地 storage 时通常会调一次同步后端），
     * 避免 401 → 前端 retry logout → 死循环。</p>
     */
    @SaIgnore
    @PostMapping("/logout")
    public R<Void> logout() {
        try {
            StpUtil.logout();
        } catch (Exception e) {
            log.info("[applet-auth] logout ignored (no valid token): {}", e.getMessage());
        }
        return R.ok();
    }

    /**
     * 账号密码登录入参。
     */
    @lombok.Data
    public static class AppletPasswordLoginBo {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;

        /** sys_client.client_id，固定 {@code mp-applet-dongjiaoshan} */
        @NotBlank(message = "clientId 不能为空")
        private String clientId;

        /** 默认农场（V1 多农场未启用，'1001'） */
        private String tenantId;

        /** 登录方式标识，前端固定 'password' */
        private String grantType;
    }
}
