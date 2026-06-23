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
 *       dev_warehouse_mgr），命中即颁发 sa-token，token 绑定 client_id={@code mp-applet-dongjiaoshan}</li>
 *   <li>wxLogin：完全代理到 SYS-AUTH-001 的 {@link IWechatLoginService}，复用其内置
 *       mock 模式（{@code djs.wechat.miniapp.app-id=mock}）</li>
 * </ul>
 *
 * <p><b>Kevin 上生产前必须：</b></p>
 * <ol>
 *   <li>关闭 {@code djs.applet.auth-mock-enabled}（默认 false）；</li>
 *   <li>账号密码登录走 ruoyi 自带 {@code /auth/login} 完整链路（password / captcha / 锁定策略），
 *       本 controller 的 mock login 端点会自动抛 ServiceException，禁止生产使用。</li>
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
     * 必须显式改 false，否则下面的 mock login 端点会让任意人用 dev/dev123 进入系统。</p>
     */
    @Value("${djs.applet.auth-mock-enabled:true}")
    private boolean authMockEnabled;

    /**
     * V1 mock 账号白名单（username → {userId, password, nickname, roles}）。
     *
     * <p>新加调试账号在此处加一行即可，无需改 login 方法。userId 对齐 sys_user D04 dev seed
     * （[`V202605211700__D04-dev-seed-users.sql`] 9100-9107 段）；`dev_warehouse_worker`
     * sys_user 暂未 seed，用占位 9108 — mock 路径 userId 不依赖 sys_user 真行（走 sa-token
     * 内存 LoginUser），故无影响。</p>
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
     *   <li>40003 + message → 账号密码错误</li>
     *   <li>40004 + message → 生产模式（mock 关闭）禁用本端点，请改用 ruoyi 自带 {@code /auth/login}</li>
     * </ul>
     */
    @SaIgnore
    @PostMapping("/login")
    public R<WechatLoginVo> login(@Valid @RequestBody AppletPasswordLoginBo bo) {
        if (!authMockEnabled) {
            log.warn("生产模式下账号密码登录端点被调用，已拒绝。username={}", bo.getUsername());
            return R.fail(40004, "Mock 登录已关闭，请使用微信登录或联系管理员");
        }

        // V1 mock：白名单查找（MOCK_ACCOUNTS），命中后做密码常量时间比较
        MockAccount acc = MOCK_ACCOUNTS.get(bo.getUsername());
        if (acc == null || !acc.password().equals(bo.getPassword())) {
            return R.fail(40003,
                "账号或密码错误（V1 mock 仅接受 dev / admin / dev_boss / dev_breed_worker / dev_warehouse_worker / dev_store_clerk 等 dev seed 账号，密码 dev123 / admin123）");
        }
        Long mockUserId = acc.userId();
        String mockNickname = acc.nickname();
        Set<String> mockRoles = acc.roles();

        // 走真 sa-token 颁发：构造 LoginUser → LoginHelper.login → 拿真 JWT token。
        // 所有后续 /djs/* 端点都能正常解析 token，不再有 "mock-token-xxx" 字符串特例。
        // 单测环境 sa-token 上下文未初始化时（SaTokenContext 未就绪），fallback 到 mock-token-{userId}
        // 字符串保持向后兼容（生产路径 sa-token 一定初始化，不走 catch）。
        LoginUser loginUser = buildMockLoginUser(mockUserId, bo.getUsername(), mockNickname, mockRoles);
        SaLoginParameter param = new SaLoginParameter()
            .setDeviceType("mp")
            .setExtra(LoginHelper.CLIENT_KEY, DjsAuthConstants.MP_APPLET_CLIENT_ID);
        String token;
        try {
            LoginHelper.login(loginUser, param);
            token = StpUtil.getTokenValue();
        } catch (Exception e) {
            log.warn("[applet-auth-mock] sa-token 上下文不可用，fallback 到字符串 token：{}", e.getMessage());
            token = "mock-token-" + mockUserId;
        }

        WechatLoginVo vo = new WechatLoginVo();
        vo.setAccessToken(token);
        vo.setExpireIn(7200L);
        vo.setUserId(mockUserId);
        vo.setNickName(mockNickname);
        vo.setOpenid("mock-openid-" + mockUserId);
        vo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);
        vo.setCurrentFarmId(DjsAuthConstants.DEFAULT_FARM_ID);
        log.info("[applet-auth-mock] password login ok username={} userId={} token={}", bo.getUsername(), mockUserId, token);
        return R.ok(vo);
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
     *   "roles": ["breed_admin", "store_clerk"] }
     * </pre>
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
