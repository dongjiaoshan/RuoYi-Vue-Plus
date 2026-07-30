package org.dromara.djs.common.controller.applet;

import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.PermissionService;
import org.dromara.djs.common.constant.DjsAuthConstants;
import org.dromara.djs.common.domain.vo.WechatLoginVo;
import org.dromara.djs.common.service.IWechatLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AppletAuthController mock 模式 happy path 单测（不启 Spring，纯 Mockito）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>dev/dev123 → mock-token-9001 + userId=9001 + clientId=mp-applet-dongjiaoshan</li>
 *   <li>admin/admin123 → mock-token-1 + userId=1</li>
 *   <li>错密码 → 40003</li>
 *   <li>mock 开关关闭 → fallthrough 到真实 BCrypt 登录</li>
 *   <li><b>账号状态闸</b>：白名单命中但账号已停用 / 已删除 / userId 不存在 → 不发 token 返 40003，
 *       且不 fallthrough；账号启用未删 → 照常发 token</li>
 * </ul>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@DisplayName("AppletAuthController mock 登录单测")
class AppletAuthControllerTest {

    @Mock
    private IWechatLoginService wechatLoginService;

    /** mock 登录也按 userId 读真菜单权限，controller 里是构造器注入的必需依赖。 */
    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private AppletAuthController controller;

    @BeforeEach
    void setUp() {
        // 默认启用 mock 模式
        ReflectionTestUtils.setField(controller, "authMockEnabled", true);
    }

    @Test
    @DisplayName("dev/dev123 应颁发 mock-token-9001")
    void devLoginShouldIssueMockToken() {
        AppletAuthController.AppletPasswordLoginBo bo = new AppletAuthController.AppletPasswordLoginBo();
        bo.setUsername("dev");
        bo.setPassword("dev123");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        R<WechatLoginVo> r = controller.login(bo);

        assertEquals(200, r.getCode());
        WechatLoginVo vo = r.getData();
        assertNotNull(vo);
        assertEquals("mock-token-9001", vo.getAccessToken());
        assertEquals(9001L, vo.getUserId());
        assertEquals("dev 员工", vo.getNickName());
        assertEquals(DjsAuthConstants.MP_APPLET_CLIENT_ID, vo.getClientId());
        assertEquals(DjsAuthConstants.DEFAULT_FARM_ID, vo.getCurrentFarmId());
    }

    @Test
    @DisplayName("admin/admin123 应颁发 mock-token-1")
    void adminLoginShouldIssueMockToken() {
        AppletAuthController.AppletPasswordLoginBo bo = new AppletAuthController.AppletPasswordLoginBo();
        bo.setUsername("admin");
        bo.setPassword("admin123");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        R<WechatLoginVo> r = controller.login(bo);

        assertEquals(200, r.getCode());
        assertEquals("mock-token-1", r.getData().getAccessToken());
        assertEquals(1L, r.getData().getUserId());
    }

    @Test
    @DisplayName("白名单未命中 → fallthrough 到真实员工登录，凭证错时返 40003")
    void wrongCredentialsShouldFail() {
        // mock 白名单没有 hacker → 落到真实 employeePasswordLogin（sys_user + BCrypt）
        when(wechatLoginService.employeePasswordLogin("hacker", "wrong", DjsAuthConstants.MP_APPLET_CLIENT_ID))
            .thenThrow(new ServiceException("账号或密码错误", DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR));

        AppletAuthController.AppletPasswordLoginBo bo = new AppletAuthController.AppletPasswordLoginBo();
        bo.setUsername("hacker");
        bo.setPassword("wrong");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        R<WechatLoginVo> r = controller.login(bo);

        assertEquals(40003, r.getCode());
        assertTrue(r.getMsg().contains("账号或密码错误"));
    }

    @Test
    @DisplayName("mock 关闭时 dev 白名单失效，转真实员工登录（账号已停用 → 40003）")
    void productionModeShouldRejectMockLogin() {
        // ⚠️ 关掉 authMockEnabled 并<b>不</b>直接拒绝登录，而是 fallthrough 到真实 BCrypt 校验。
        // dev_* 账号的真实密码就是 dev123，所以光关开关挡不住——必须同时在账号层停用
        // （见迁移 V202608251600 GRAY-PREP-001）。本用例把这个事实固化下来，
        // 避免有人误以为"关了 mock 开关就安全了"。
        ReflectionTestUtils.setField(controller, "authMockEnabled", false);
        when(wechatLoginService.employeePasswordLogin("dev", "dev123", DjsAuthConstants.MP_APPLET_CLIENT_ID))
            .thenThrow(new ServiceException("账号或密码错误", DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR));

        AppletAuthController.AppletPasswordLoginBo bo = new AppletAuthController.AppletPasswordLoginBo();
        bo.setUsername("dev");
        bo.setPassword("dev123");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        R<WechatLoginVo> r = controller.login(bo);

        assertEquals(DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR, r.getCode());
        // 关键：没有走 mock 分支（否则会拿到 mock-token-9001）
        assertNull(r.getData());
        verify(wechatLoginService, times(1))
            .employeePasswordLogin("dev", "dev123", DjsAuthConstants.MP_APPLET_CLIENT_ID);
    }

    @Test
    @DisplayName("🔴 白名单命中但账号层已停用 → 不发 token，返 40003，且不 fallthrough 到真实登录")
    void mockHitButAccountDisabledShouldNotIssueToken() {
        // 账号状态闸：sys_user 里 dev(9001) 已 status='1' / del_flag='2' → service 抛 40003
        doThrow(new ServiceException("applet.auth.login.rejected", DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR))
            .when(wechatLoginService).assertUserLoginable(9001L);

        AppletAuthController.AppletPasswordLoginBo bo = new AppletAuthController.AppletPasswordLoginBo();
        bo.setUsername("dev");
        bo.setPassword("dev123");          // 白名单密码本身是对的
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        R<WechatLoginVo> r = controller.login(bo);

        assertEquals(DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR, r.getCode());
        assertNull(r.getData());           // 没有 token
        verify(wechatLoginService, times(1)).assertUserLoginable(9001L);
        // 停用账号不得借 fallthrough 从真实 BCrypt 路径再试一次
        verify(wechatLoginService, never()).employeePasswordLogin(anyString(), anyString(), anyString());
        // 也不得装配权限（装配意味着已经在造 LoginUser）
        verify(permissionService, never()).getMenuPermission(anyLong());
    }

    @Test
    @DisplayName("🔴 白名单命中但 sys_user 无此 userId → 不发 token，返 40003")
    void mockHitButAccountMissingShouldNotIssueToken() {
        doThrow(new ServiceException("applet.auth.login.rejected", DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR))
            .when(wechatLoginService).assertUserLoginable(9108L);

        AppletAuthController.AppletPasswordLoginBo bo = new AppletAuthController.AppletPasswordLoginBo();
        bo.setUsername("dev_warehouse_worker");
        bo.setPassword("dev123");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        R<WechatLoginVo> r = controller.login(bo);

        assertEquals(DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR, r.getCode());
        assertNull(r.getData());
        verify(permissionService, never()).getMenuPermission(anyLong());
    }

    @Test
    @DisplayName("白名单命中且账号启用未删 → 过闸后照常发 token（回归保护）")
    void mockHitWithActiveAccountShouldIssueToken() {
        // assertUserLoginable 是 void，默认不抛 = 账号启用未删
        AppletAuthController.AppletPasswordLoginBo bo = new AppletAuthController.AppletPasswordLoginBo();
        bo.setUsername("admin");
        bo.setPassword("admin123");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        R<WechatLoginVo> r = controller.login(bo);

        assertEquals(200, r.getCode());
        WechatLoginVo vo = r.getData();
        assertNotNull(vo);
        assertEquals(1L, vo.getUserId());
        assertNotNull(vo.getAccessToken());
        assertEquals(DjsAuthConstants.MP_APPLET_CLIENT_ID, vo.getClientId());
        // 闸在颁 token 之前跑过，且没有落到真实密码登录
        verify(wechatLoginService, times(1)).assertUserLoginable(1L);
        verify(wechatLoginService, never()).employeePasswordLogin(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("白名单未命中不查账号状态闸（避免拿闸的行为差异枚举白名单）")
    void nonWhitelistUsernameShouldNotHitAccountGate() {
        when(wechatLoginService.employeePasswordLogin("caobi", "dev123", DjsAuthConstants.MP_APPLET_CLIENT_ID))
            .thenThrow(new ServiceException("applet.auth.login.rejected", DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR));

        AppletAuthController.AppletPasswordLoginBo bo = new AppletAuthController.AppletPasswordLoginBo();
        bo.setUsername("caobi");
        bo.setPassword("dev123");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        R<WechatLoginVo> r = controller.login(bo);

        assertEquals(DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR, r.getCode());
        verify(wechatLoginService, never()).assertUserLoginable(anyLong());
    }
}
