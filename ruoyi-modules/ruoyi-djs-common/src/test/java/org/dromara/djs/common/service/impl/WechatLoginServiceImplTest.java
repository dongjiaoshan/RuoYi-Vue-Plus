package org.dromara.djs.common.service.impl;

import org.dromara.common.core.exception.ServiceException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WechatLoginServiceImpl happy path 单测（不启动 Spring，纯 Mockito）。
 *
 * <p>覆盖的核心分支：</p>
 * <ol>
 *   <li>wechatLogin: openid 未绑定 → 抛 BIZ_CODE_WECHAT_NEED_BIND(40001)</li>
 *   <li>bindPhone:   phone 未注册 → 抛 BIZ_CODE_PHONE_NOT_REGISTERED(40002)</li>
 *   <li>bindPhone:   phone 命中 → 调用 updateWxOpenid + 颁 token 路径（不走 LoginHelper.login,
 *       因为它依赖 Sa-Token 上下文；本测仅断言 mapper 交互正确）</li>
 * </ol>
 *
 * <p>更完整的"颁 token + Sa-Token 集成"路径放在 D02 集成测试或全栈 B 的 Playwright e2e 里。</p>
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

    @InjectMocks
    private WechatLoginServiceImpl service;

    private void enableMockMode() {
        ReflectionTestUtils.setField(service, "wxAppId", "mock");
    }

    @Test
    @DisplayName("wechatLogin: openid 未绑定，应抛 40001 BIZ_CODE_WECHAT_NEED_BIND")
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
    @DisplayName("bindPhone: phone 未注册，应抛 40002 BIZ_CODE_PHONE_NOT_REGISTERED")
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
    @DisplayName("bindPhone: phone 命中，应调用 updateWxOpenid")
    void testBindPhone_HappyPath_UpdatesOpenid() {
        enableMockMode();
        WechatBindPhoneBo bo = new WechatBindPhoneBo();
        bo.setOpenid("mock-openid-002");
        bo.setClientId(DjsAuthConstants.MP_APPLET_CLIENT_ID);
        bo.setPhone("13800138001");

        when(djsUserExtMapper.selectUserIdByPhone("13800138001")).thenReturn(100L);
        when(djsUserExtMapper.updateWxOpenid(100L, "mock-openid-002")).thenReturn(1);

        // 颁 token 流程会调 selectUserName / selectTenantId / selectCurrentFarmId（懒加载）
        // 这一步会触发 StpUtil.login，需要 Sa-Token 上下文。本测试只断言 mapper 交互；
        // 实际 StpUtil 调用会抛 NPE（无 sa-token 上下文），用 assertThrows 包裹避免污染下游
        try {
            WechatLoginVo vo = service.bindPhone(bo);
            // 如果走到这里说明 sa-token 给了个默认 stub（极少见），断言基本字段
            assertEquals("mock-openid-002", vo.getOpenid());
        } catch (RuntimeException e) {
            // 预期：Sa-Token 没有 web 上下文会抛 NPE / IllegalStateException
            // 不打断测试，只验证 updateWxOpenid 已被调用（说明业务逻辑跑到颁 token 之前）
        }

        verify(djsUserExtMapper, times(1)).updateWxOpenid(100L, "mock-openid-002");
    }
}
