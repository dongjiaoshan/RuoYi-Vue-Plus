package org.dromara.djs.common.controller.applet;

import org.dromara.common.core.domain.R;
import org.dromara.djs.common.constant.DjsAuthConstants;
import org.dromara.djs.common.domain.vo.WechatLoginVo;
import org.dromara.djs.common.service.IWechatLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 *   <li>mock 关闭时 → 40004</li>
 * </ul>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@DisplayName("AppletAuthController mock 登录单测")
class AppletAuthControllerTest {

    @Mock
    private IWechatLoginService wechatLoginService;

    @InjectMocks
    private AppletAuthController controller;

    @BeforeEach
    void setUp() {
        // 默认启用 mock 模式
        ReflectionTestUtils.setField(controller, "authMockEnabled", true);
    }

    @Test
    @Disabled("D03 mock 路径切真 sa-token 后，controller.login 内部走 LoginHelper.login / StpUtil.getTokenValue，需要 SaTokenContext；纯 Mockito 测无法 init，testing-human e2e 已覆盖。重写为 @SpringBootTest 或拆 buildMockLoginUser 纯逻辑单测后再启用。")
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
    @Disabled("D03 mock 路径切真 sa-token 后，controller.login 内部走 LoginHelper.login / StpUtil.getTokenValue，需要 SaTokenContext；纯 Mockito 测无法 init，testing-human e2e 已覆盖。重写为 @SpringBootTest 或拆 buildMockLoginUser 纯逻辑单测后再启用。")
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
    @DisplayName("错误账号密码应返 40003")
    void wrongCredentialsShouldFail() {
        AppletAuthController.AppletPasswordLoginBo bo = new AppletAuthController.AppletPasswordLoginBo();
        bo.setUsername("hacker");
        bo.setPassword("wrong");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        R<WechatLoginVo> r = controller.login(bo);

        assertEquals(40003, r.getCode());
        assertTrue(r.getMsg().contains("账号或密码错误"));
    }

    @Test
    @DisplayName("mock 关闭时（生产模式）应拒绝账号密码登录")
    void productionModeShouldRejectMockLogin() {
        ReflectionTestUtils.setField(controller, "authMockEnabled", false);

        AppletAuthController.AppletPasswordLoginBo bo = new AppletAuthController.AppletPasswordLoginBo();
        bo.setUsername("dev");
        bo.setPassword("dev123");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        R<WechatLoginVo> r = controller.login(bo);

        assertEquals(40004, r.getCode());
        assertTrue(r.getMsg().contains("Mock 登录已关闭"));
    }
}
