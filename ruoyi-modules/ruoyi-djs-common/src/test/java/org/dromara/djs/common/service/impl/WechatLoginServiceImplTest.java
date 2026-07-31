package org.dromara.djs.common.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.WxMaUserService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import me.chanjar.weixin.common.error.WxError;
import me.chanjar.weixin.common.error.WxErrorException;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.PermissionService;
import org.dromara.djs.common.constant.DjsAuthConstants;
import org.dromara.djs.common.domain.bo.WechatBindPhoneBo;
import org.dromara.djs.common.domain.bo.WechatLoginBo;
import org.dromara.djs.common.domain.vo.WechatLoginVo;
import org.dromara.djs.common.mapper.DjsUserExtMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WechatLoginServiceImpl 单测（不启动 Spring，纯 Mockito）。
 *
 * <p>覆盖分支：</p>
 * <ol>
 *   <li>mock 模式 wechatLogin: openid 未绑定 → 40001 BIZ_CODE_WECHAT_NEED_BIND</li>
 *   <li>mock 模式 bindPhone: phone 未注册 → 40002 / phone 命中 → updateWxOpenid</li>
 *   <li>真模式 wechatLogin: WxJava getSessionInfo 换 openid（未绑定 → 40001，验证真 jscode2session 被调）</li>
 *   <li>真模式 jscode2session 抛 WxErrorException → ServiceException</li>
 *   <li>真模式 bindPhone: WxJava getPhoneNoInfo 解析手机号（未注册 → 40002，验证真手机号验证被调）</li>
 *   <li>assertUserLoginable 账号状态闸：不存在 / 停用 / 删除 / null → 40003 同款文案；启用未删 → 放行</li>
 * </ol>
 *
 * <p>"颁 token + Sa-Token 集成"路径依赖 Sa-Token 上下文，放集成测试 / Playwright e2e；
 * 本测仅断言到颁 token 之前的业务交互。</p>
 *
 * @author djs
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WechatLoginServiceImpl 单元测试")
@Tag("local")
@Tag("dev")
class WechatLoginServiceImplTest {

    @Mock
    private DjsUserExtMapper djsUserExtMapper;

    @Mock
    private WxMaService wxMaService;

    @Mock
    private WxMaUserService wxMaUserService;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private WechatLoginServiceImpl service;

    private void enableMockMode() {
        ReflectionTestUtils.setField(service, "wxAppId", "mock");
    }

    /**
     * 真实模式 = 真 appid + 真 secret。两者必须成对设置：只设 appid 不设 secret 会被
     * {@code assertWxConfigured()} 前置拦下（少了 secret 微信返 41004 appsecret missing，
     * 那道闸就是把它翻译成可执行提示的）。
     */
    private void enableRealMode() {
        ReflectionTestUtils.setField(service, "wxAppId", "wx42158dde5e73260c");
        ReflectionTestUtils.setField(service, "wxSecret", "test-secret-not-a-real-one");
    }

    /** 真 appid 但没配 secret —— 本地忘了 export WX_MA_SECRET 的场景。 */
    private void enableRealModeWithoutSecret() {
        ReflectionTestUtils.setField(service, "wxAppId", "wx42158dde5e73260c");
        ReflectionTestUtils.setField(service, "wxSecret", "");
    }

    @Test
    @DisplayName("mock 模式 wechatLogin: openid 未绑定，应抛 40001 BIZ_CODE_WECHAT_NEED_BIND")
    void testWechatLogin_OpenidNotBound() {
        enableMockMode();
        WechatLoginBo bo = new WechatLoginBo();
        bo.setCode("mock-openid-not-exists");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        // mock 模式下 code 直接当 openid，mapper 返 null 模拟未绑定
        when(djsUserExtMapper.selectLoginVoByOpenid("mock-openid-not-exists")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.wechatLogin(bo));
        assertEquals(DjsAuthConstants.BIZ_CODE_WECHAT_NEED_BIND, ex.getCode());
    }

    @Test
    @DisplayName("mock 模式 bindPhone: phone 未注册，应抛 40002 BIZ_CODE_PHONE_NOT_REGISTERED")
    void testBindPhone_PhoneNotRegistered() {
        enableMockMode();
        WechatBindPhoneBo bo = new WechatBindPhoneBo();
        bo.setOpenid("mock-openid-001");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);
        bo.setPhone("13800138000");

        when(djsUserExtMapper.selectUserIdByPhone("13800138000")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.bindPhone(bo));
        assertEquals(DjsAuthConstants.BIZ_CODE_PHONE_NOT_REGISTERED, ex.getCode());
        verify(djsUserExtMapper, times(0)).updateWxOpenid(anyLong(), anyString());
    }

    @Test
    @DisplayName("mock 模式 bindPhone: phone 命中，应调用 updateWxOpenid")
    void testBindPhone_HappyPath_UpdatesOpenid() {
        enableMockMode();
        WechatBindPhoneBo bo = new WechatBindPhoneBo();
        bo.setOpenid("mock-openid-002");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);
        bo.setPhone("13800138001");

        when(djsUserExtMapper.selectUserIdByPhone("13800138001")).thenReturn(100L);
        when(djsUserExtMapper.updateWxOpenid(100L, "mock-openid-002")).thenReturn(1);

        // 颁 token 流程会触发 StpUtil.login，需要 Sa-Token 上下文。本测只断言 mapper 交互；
        // StpUtil 调用会抛 NPE/IllegalState（无上下文），用 try-catch 包裹避免污染
        try {
            WechatLoginVo vo = service.bindPhone(bo);
            assertEquals("mock-openid-002", vo.getOpenid());
        } catch (RuntimeException e) {
            // 预期：Sa-Token 无 web 上下文抛异常，不打断测试
        }

        verify(djsUserExtMapper, times(1)).updateWxOpenid(100L, "mock-openid-002");
    }

    @Test
    @DisplayName("真模式 wechatLogin: WxJava 换 openid 后未绑定，应抛 40001（验证 jscode2session 被调）")
    void testWechatLogin_RealMode_Jscode2Session() throws WxErrorException {
        enableRealMode();
        WechatLoginBo bo = new WechatLoginBo();
        bo.setCode("real-login-code");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        WxMaJscode2SessionResult session = new WxMaJscode2SessionResult();
        session.setOpenid("real-openid-1");
        when(wxMaService.getUserService()).thenReturn(wxMaUserService);
        when(wxMaUserService.getSessionInfo("real-login-code")).thenReturn(session);
        when(djsUserExtMapper.selectLoginVoByOpenid("real-openid-1")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.wechatLogin(bo));
        assertEquals(DjsAuthConstants.BIZ_CODE_WECHAT_NEED_BIND, ex.getCode());
        verify(wxMaUserService, times(1)).getSessionInfo("real-login-code");
    }

    @Test
    @DisplayName("真模式 wechatLogin: jscode2session 抛 WxErrorException，应转 ServiceException")
    void testWechatLogin_RealMode_WxError() throws WxErrorException {
        enableRealMode();
        WechatLoginBo bo = new WechatLoginBo();
        bo.setCode("bad-code");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        when(wxMaService.getUserService()).thenReturn(wxMaUserService);
        when(wxMaUserService.getSessionInfo("bad-code"))
            .thenThrow(new WxErrorException(WxError.builder().errorCode(40029).errorMsg("invalid code").build()));

        assertThrows(ServiceException.class, () -> service.wechatLogin(bo));
    }

    @Test
    @DisplayName("真 appid 但没配 secret: 应前置拦下并提示 WX_MA_SECRET，不去调微信（防 41004 堆栈误导）")
    void testWechatLogin_RealAppIdWithoutSecret_FailsFastWithActionableMessage() {
        enableRealModeWithoutSecret();
        WechatLoginBo bo = new WechatLoginBo();
        bo.setCode("any-code");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.wechatLogin(bo));
        // 报错必须点名环境变量，否则等于把 41004 换了身皮，照样没人知道该干什么
        assertTrue(ex.getMessage().contains("WX_MA_SECRET"),
            "报错应点名 WX_MA_SECRET，实际为：" + ex.getMessage());
        // 关键：连微信都不该碰（省一次必然失败的外部调用，也让报错稳定可断言）
        verify(wxMaService, times(0)).getUserService();
    }

    @Test
    @DisplayName("真模式 bindPhone: WxJava 解析手机号后未注册，应抛 40002（验证 getPhoneNoInfo 被调）")
    void testBindPhone_RealMode_PhoneCode() throws WxErrorException {
        enableRealMode();
        WechatBindPhoneBo bo = new WechatBindPhoneBo();
        // 真实模式下 openid 必须由 code 换取，客户端传的 openid 一律忽略（GRAY-PREP-001 加固）
        bo.setCode("real-login-code");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);
        bo.setPhoneCode("real-phone-code");

        WxMaJscode2SessionResult session = new WxMaJscode2SessionResult();
        session.setOpenid("real-openid-2");
        WxMaPhoneNumberInfo info = new WxMaPhoneNumberInfo();
        info.setPhoneNumber("13900139000");
        when(wxMaService.getUserService()).thenReturn(wxMaUserService);
        when(wxMaUserService.getSessionInfo("real-login-code")).thenReturn(session);
        when(wxMaUserService.getPhoneNoInfo("real-phone-code")).thenReturn(info);
        when(djsUserExtMapper.selectUserIdByPhone("13900139000")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.bindPhone(bo));
        assertEquals(DjsAuthConstants.BIZ_CODE_PHONE_NOT_REGISTERED, ex.getCode());
        verify(wxMaUserService, times(1)).getPhoneNoInfo("real-phone-code");
        verify(djsUserExtMapper, times(0)).updateWxOpenid(anyLong(), anyString());
    }

    @Test
    @DisplayName("真模式 bindPhone 安全闸：客户端直传 openid 不给 code → 拒绝（防冒充任意员工）")
    void testBindPhone_RealMode_RejectsRawOpenid() {
        enableRealMode();
        WechatBindPhoneBo bo = new WechatBindPhoneBo();
        bo.setOpenid("attacker-controlled-openid");
        bo.setPhone("13900139000");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.bindPhone(bo));
        assertTrue(ex.getMessage().contains("缺少微信 code"));
        // 关键：绝不能走到查手机号 / 改写 wx_openid —— 否则等于拿别人手机号就能冒充登录
        verify(djsUserExtMapper, times(0)).selectUserIdByPhone(anyString());
        verify(djsUserExtMapper, times(0)).updateWxOpenid(anyLong(), anyString());
    }

    @Test
    @DisplayName("真模式 bindPhone 安全闸：传了 code 但同时塞 phone → phone 被忽略，仍走微信换号")
    void testBindPhone_RealMode_IgnoresRawPhone() throws WxErrorException {
        enableRealMode();
        WechatBindPhoneBo bo = new WechatBindPhoneBo();
        bo.setCode("real-login-code");
        bo.setPhoneCode("real-phone-code");
        bo.setPhone("13800138000");   // 攻击者想指定的手机号，必须被忽略
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);

        WxMaJscode2SessionResult session = new WxMaJscode2SessionResult();
        session.setOpenid("real-openid-3");
        WxMaPhoneNumberInfo info = new WxMaPhoneNumberInfo();
        info.setPhoneNumber("13900139000");   // 微信返回的才算数
        when(wxMaService.getUserService()).thenReturn(wxMaUserService);
        when(wxMaUserService.getSessionInfo("real-login-code")).thenReturn(session);
        when(wxMaUserService.getPhoneNoInfo("real-phone-code")).thenReturn(info);
        when(djsUserExtMapper.selectUserIdByPhone("13900139000")).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.bindPhone(bo));
        // 查的是微信返回的号，不是 bo.phone
        verify(djsUserExtMapper, times(1)).selectUserIdByPhone("13900139000");
        verify(djsUserExtMapper, times(0)).selectUserIdByPhone("13800138000");
    }

    // ── assertUserLoginable 账号状态闸 ────────────────────────────────────
    //
    // 闸的职责：给<b>不查密码</b>的登录路径（mp dev mock 白名单）补账号层校验。
    // selectLoginableUserId 的 SQL 谓词（del_flag='0' AND status='0'）把「账号不存在 /
    // 已停用 / 已删除」三态统一归一成返回 null，下面按这三种业务场景分别固化拒绝行为。

    @Test
    @DisplayName("账号状态闸：账号存在且启用未删 → 放行，不抛异常")
    void testAssertUserLoginable_ActiveAccountPasses() {
        when(djsUserExtMapper.selectLoginableUserId(9001L)).thenReturn(9001L);

        assertDoesNotThrow(() -> service.assertUserLoginable(9001L));
    }

    @Test
    @DisplayName("账号状态闸：账号已停用 status='1' → 抛 40003，不放行")
    void testAssertUserLoginable_DisabledAccountRejected() {
        // status='1' 被 SQL 谓词过滤 → 查不到行
        when(djsUserExtMapper.selectLoginableUserId(9001L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.assertUserLoginable(9001L));
        assertEquals(DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR, ex.getCode());
        assertSameWordingAsPasswordError(ex);
    }

    @Test
    @DisplayName("账号状态闸：账号已删除 del_flag='2' → 抛 40003，不放行")
    void testAssertUserLoginable_DeletedAccountRejected() {
        // del_flag='2' 被 SQL 谓词过滤 → 查不到行
        when(djsUserExtMapper.selectLoginableUserId(9107L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.assertUserLoginable(9107L));
        assertEquals(DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR, ex.getCode());
        assertSameWordingAsPasswordError(ex);
    }

    @Test
    @DisplayName("账号状态闸：userId 在 sys_user 根本不存在（白名单占位 id）→ 抛 40003，不放行")
    void testAssertUserLoginable_MissingAccountRejected() {
        when(djsUserExtMapper.selectLoginableUserId(9108L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.assertUserLoginable(9108L));
        assertEquals(DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR, ex.getCode());
        assertSameWordingAsPasswordError(ex);
    }

    @Test
    @DisplayName("账号状态闸：userId 为 null → 直接拒，不查库")
    void testAssertUserLoginable_NullUserIdRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.assertUserLoginable(null));
        assertEquals(DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR, ex.getCode());
        verify(djsUserExtMapper, times(0)).selectLoginableUserId(null);
    }

    /**
     * 拒绝文案必须走 i18n key {@code applet.auth.login.rejected}，与密码错误路径完全同款 ——
     * 不回「该账号已停用」这类可用于枚举有效账号的信息。
     *
     * <p>无 Spring 上下文时 {@code I18nMessages.t} 回吐 key 本身，故这里断言 key。</p>
     */
    private void assertSameWordingAsPasswordError(ServiceException ex) {
        assertEquals("applet.auth.login.rejected", ex.getMessage());
    }
}
