package org.dromara.djs.common.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.constant.DjsAuthConstants;
import org.dromara.djs.common.domain.bo.WechatBindPhoneBo;
import org.dromara.djs.common.domain.bo.WechatLoginBo;
import org.dromara.djs.common.domain.vo.WechatLoginVo;
import org.dromara.djs.common.mapper.DjsUserExtMapper;
import org.dromara.djs.common.service.IWechatLoginService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 微信小程序登录服务实现。
 *
 * <p>本类实现 jscode2session 调用（V1 mock，V2 切 WxJava）+ openid/phone 匹配 sys_user +
 * 通过 {@link LoginHelper#login(LoginUser, SaLoginParameter)} 颁发 Sa-Token。</p>
 *
 * <p>关于 LoginUser.menuPermission / rolePermission 字段：本实现**不预填**这两个 Set，留 null。
 * 运行时 {@code SaPermissionImpl}（ruoyi-common-satoken）会发现 session 里没有，自动回退到
 * 调 SpringUtils.getBean(PermissionService.class).getMenuPermission(userId) 实时加载（懒加载策略）。
 * 这避免了 djs-common 反向依赖 ruoyi-system，同时保留 @SaCheckPermission 正常工作。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatLoginServiceImpl implements IWechatLoginService {

    private final DjsUserExtMapper djsUserExtMapper;

    /**
     * 微信小程序 AppID（生产环境从 application.yml 读 djs.wechat.miniapp.app-id）。
     * 默认 "mock" 表示走 dev mock 模式：直接使用前端传的 code 作为 openid。
     */
    @Value("${djs.wechat.miniapp.app-id:mock}")
    private String wxAppId;

    @Override
    public WechatLoginVo wechatLogin(WechatLoginBo bo) {
        // 1. 凭 code 拿 openid（V1 mock：code 当作 openid 用；V2 切 jscode2session）
        String openid = jscode2openid(bo.getCode());

        // 2. 查 sys_user wx_openid 是否绑定
        WechatLoginVo summary = djsUserExtMapper.selectLoginVoByOpenid(openid);
        if (summary == null) {
            log.info("微信登录 openid={} 未绑定，需走 bind-phone 流程", openid);
            throw new ServiceException(
                "微信账号未绑定，请使用手机号绑定后登录",
                DjsAuthConstants.BIZ_CODE_WECHAT_NEED_BIND);
        }

        // 3. 颁发 token
        return issueToken(summary.getUserId(), openid, bo.getClientId());
    }

    @Override
    public WechatLoginVo bindPhone(WechatBindPhoneBo bo) {
        // 1. 拿到真实手机号（V1：直接读 bo.phone；V2：调微信"手机号快速验证"接口换 phoneCode → phone）
        String phone = StrUtil.isNotBlank(bo.getPhone()) ? bo.getPhone() : resolvePhoneByCode(bo.getPhoneCode());
        if (StrUtil.isBlank(phone)) {
            throw new ServiceException("手机号不能为空", DjsAuthConstants.BIZ_CODE_PHONE_NOT_REGISTERED);
        }

        // 2. 查 sys_user.phonenumber
        Long userId = djsUserExtMapper.selectUserIdByPhone(phone);
        if (userId == null) {
            log.info("绑定手机号 {} 未注册，提示联系管理员", phone);
            throw new ServiceException(
                "手机号未在员工库注册，请联系管理员",
                DjsAuthConstants.BIZ_CODE_PHONE_NOT_REGISTERED);
        }

        // 3. UPDATE sys_user.wx_openid
        int rows = djsUserExtMapper.updateWxOpenid(userId, bo.getOpenid());
        if (rows == 0) {
            throw new ServiceException("绑定 openid 失败");
        }

        // 4. 颁发 token
        return issueToken(userId, bo.getOpenid(), bo.getClientId());
    }

    // ---------------------- 内部辅助 ----------------------

    /**
     * jscode2session 调用。
     *
     * <p>V1 mock 实现：当 djs.wechat.miniapp.app-id = "mock" 时，把入参 code 直接当 openid 返回，
     * 方便本地调试。生产环境需把 wxAppId 配真实 AppID 后切到 WxJava / JustAuth。</p>
     *
     * @param code wx.login() 返回的临时 code
     * @return openid
     */
    private String jscode2openid(String code) {
        if (StrUtil.isBlank(code)) {
            throw new ServiceException("微信 code 不能为空");
        }
        if ("mock".equalsIgnoreCase(wxAppId)) {
            // dev mock：约定前端传 'mock-openid-<员工 user_name>'，便于本地预绑场景测试
            log.info("[mock] jscode2openid code={} → openid 直接复用 code", code);
            return code;
        }
        // TODO V2：引入 me.zhyd.justauth.AuthWechatMiniProgramRequest 或 weixin-java-miniapp
        //  调真实 jscode2session，参考 ruoyi-admin XcxAuthStrategy 的实现样例
        throw new ServiceException("生产环境 jscode2session 待 SYS-INFRA-006 联调微信后启用，请先用 mock 模式");
    }

    /**
     * 手机号快速验证 phoneCode → phone。
     *
     * <p>V1 mock：始终抛"未配置"；生产用 weixin-java-miniapp WxMaPhoneNumberInfo.</p>
     */
    private String resolvePhoneByCode(String phoneCode) {
        if (StrUtil.isBlank(phoneCode)) {
            return null;
        }
        log.warn("[mock] resolvePhoneByCode phoneCode={} 未实现，直接返 null（dev 模式请传 phone 字段）", phoneCode);
        return null;
    }

    /**
     * 颁发 Sa-Token：构造最小化 LoginUser → LoginHelper.login。
     */
    private WechatLoginVo issueToken(Long userId, String openid, String clientId) {
        // 拉用户基本信息（用于 LoginUser 构造）
        String userName = djsUserExtMapper.selectUserName(userId);
        String tenantId = djsUserExtMapper.selectTenantId(userId);
        String currentFarmId = djsUserExtMapper.selectCurrentFarmId(userId);
        if (StrUtil.isBlank(currentFarmId)) {
            currentFarmId = DjsAuthConstants.DEFAULT_FARM_ID;
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setTenantId(tenantId);
        loginUser.setUsername(userName);
        loginUser.setUserType(UserType.SYS_USER.getUserType());
        loginUser.setClientKey(DjsAuthConstants.MP_APPLET_CLIENT_KEY);
        loginUser.setDeviceType("mp");
        // menuPermission / rolePermission 不预填，SaPermissionImpl 会按需调 PermissionService 实时加载

        SaLoginParameter model = new SaLoginParameter();
        model.setDeviceType("mp");
        // 30 天 token TTL（doc/05 §4.4.3）
        model.setTimeout(30 * 24 * 60 * 60L);
        model.setActiveTimeout(30 * 60L);
        model.setExtra(LoginHelper.CLIENT_KEY, clientId);
        // 把 current_farm_id 注入 token extra（FarmContextInterceptor V2 启用时读）
        model.setExtra("farmId", currentFarmId);
        LoginHelper.login(loginUser, model);

        // 拼响应
        WechatLoginVo vo = new WechatLoginVo();
        vo.setAccessToken(StpUtil.getTokenValue());
        vo.setExpireIn(StpUtil.getTokenTimeout());
        vo.setClientId(clientId);
        vo.setOpenid(openid);
        vo.setUserId(userId);
        vo.setNickName(StrUtil.nullToDefault(loginUser.getNickname(), userName));
        vo.setCurrentFarmId(currentFarmId);
        return vo;
    }
}
