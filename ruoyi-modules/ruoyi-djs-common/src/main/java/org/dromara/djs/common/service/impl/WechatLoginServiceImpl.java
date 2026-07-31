package org.dromara.djs.common.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.PermissionService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.constant.DjsAuthConstants;
import org.dromara.djs.common.domain.bo.WechatBindPhoneBo;
import org.dromara.djs.common.domain.bo.WechatLoginBo;
import org.dromara.djs.common.domain.vo.WechatLoginVo;
import org.dromara.djs.common.mapper.DjsUserExtMapper;
import org.dromara.djs.common.service.IWechatLoginService;
import org.dromara.djs.common.util.I18nMessages;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 微信小程序登录服务实现。
 *
 * <p>本类实现 jscode2session 调用（mock 模式短路 / 真模式走 WxJava）+ openid/phone 匹配 sys_user +
 * 通过 {@link LoginHelper#login(LoginUser, SaLoginParameter)} 颁发 Sa-Token。</p>
 *
 * <p>权限装配：{@link #issueToken} 颁 token 前用 {@link PermissionService} 把 menuPermission +
 * rolePermission 实填到 LoginUser（与 ruoyi 自带 {@code SysLoginService} 一致）。两个字段都要填——
 * {@code SaPermissionImpl} 对"当前 session 自身"的权限校验直接读 LoginUser 里的值，不会回退 DB；
 * 留空会导致 @SaCheckPermission 全拒、role-tabs / getInfo 取不到角色（员工进不去板块）。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatLoginServiceImpl implements IWechatLoginService {

    private final DjsUserExtMapper djsUserExtMapper;
    private final WxMaService wxMaService;
    private final PermissionService permissionService;

    /**
     * mock 模式哨兵值：{@code djs.wechat.miniapp.app-id=mock} 表示不调微信真实 API。
     *
     * <p>该模式下 {@link #jscode2openid} 把 code 当 openid 用、{@link #bindPhone} 允许透传
     * openid / phone —— 都是<b>绕过微信认证</b>的调试捷径，仅限本地。
     * {@code application-prod.yml} 里 app-id 无默认值（缺失即启动失败），防线上误回退。</p>
     */
    private static final String MOCK_APP_ID = "mock";

    /** mp token 总时长：30 天（doc/05 §4.4.3）。 */
    private static final long TOKEN_TIMEOUT_SECONDS = 30 * 24 * 60 * 60L;

    /**
     * mp token「最低活跃频率」：7 天内有过任意请求即自动续期，超期未用才要求重新登录。
     *
     * <p>原值 30 分钟对 B 端农场工人过短——一天用几次、间隔超半小时是常态，等于每次开小程序都要重登。
     * sa-token 每次请求会刷新活跃时间，所以 7 天是「连续 7 天没打开过」才掉线。</p>
     */
    private static final long TOKEN_ACTIVE_TIMEOUT_SECONDS = 7 * 24 * 60 * 60L;

    /**
     * 微信小程序 AppID（生产环境从 application.yml 读 djs.wechat.miniapp.app-id）。
     * 默认 "mock" 表示走 dev mock 模式：直接使用前端传的 code 作为 openid。
     */
    @Value("${djs.wechat.miniapp.app-id:mock}")
    private String wxAppId;

    /**
     * 微信小程序 AppSecret（{@code djs.wechat.miniapp.secret}，只走环境变量 WX_MA_SECRET，不入 git）。
     *
     * <p>只用来做「配了真 appid 却没配 secret」的前置校验 —— 少了它微信会返 41004
     * {@code appsecret missing}，WxJava 抛出的是一屏堆栈，看不出是本机没导环境变量。</p>
     */
    @Value("${djs.wechat.miniapp.secret:}")
    private String wxSecret;

    /**
     * 调微信 API 前的前置自检：配了真 appid 就必须有 secret，否则给出可执行的提示而不是 41004 堆栈。
     *
     * <p>本地起后端拿 secret：
     * {@code export WX_MA_SECRET=$(grep '^WX_MA_SECRET=' ~/.dongjiaoshan-secrets/prod.env | cut -d= -f2-)}</p>
     */
    private void assertWxConfigured() {
        if (StrUtil.isBlank(wxSecret)) {
            log.error("djs.wechat.miniapp.secret 为空（app-id={}）—— 环境变量 WX_MA_SECRET 没设", wxAppId);
            throw new ServiceException(
                "后端未配置微信 AppSecret（环境变量 WX_MA_SECRET），无法调用微信接口；"
                    + "本地调试请改用「账号密码登录」，或导入 secret 后重启后端",
                DjsAuthConstants.BIZ_CODE_PHONE_NOT_REGISTERED);
        }
    }

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
        // 客户端透传 openid / phone 只在 mock 模式（app-id=mock，本地调试）允许。
        // 真实模式必须由微信换取：否则任何人 POST {openid:任意, phone:某员工手机号} 就能冒充该员工
        // 拿到 token，并把 sys_user.wx_openid 改写成自己的（把真人的微信绑定顶掉）。
        // mp 端本来就只传 {code, phoneCode}，加严不影响正常链路。
        boolean mockMode = MOCK_APP_ID.equalsIgnoreCase(wxAppId);

        // 1. openid：真实模式一律由 code 经 jscode2session 换取，客户端不经手
        String openid;
        if (StrUtil.isNotBlank(bo.getCode())) {
            openid = jscode2openid(bo.getCode());
        } else if (mockMode) {
            openid = bo.getOpenid();
        } else {
            throw new ServiceException("缺少微信 code，无法绑定");
        }
        if (StrUtil.isBlank(openid)) {
            throw new ServiceException("缺少 code / openid，无法绑定");
        }

        // 2. 手机号：真实模式一律由 phoneCode 经微信「手机号快速验证」换取，忽略客户端传的 phone
        String phone = (mockMode && StrUtil.isNotBlank(bo.getPhone()))
            ? bo.getPhone()
            : resolvePhoneByCode(bo.getPhoneCode());
        if (StrUtil.isBlank(phone)) {
            throw new ServiceException("手机号不能为空", DjsAuthConstants.BIZ_CODE_PHONE_NOT_REGISTERED);
        }

        // 3. 查 sys_user.phonenumber
        Long userId = djsUserExtMapper.selectUserIdByPhone(phone);
        if (userId == null) {
            log.info("绑定手机号 {} 未注册，提示联系管理员", phone);
            throw new ServiceException(
                "手机号未在员工库注册，请联系管理员",
                DjsAuthConstants.BIZ_CODE_PHONE_NOT_REGISTERED);
        }

        // 4. UPDATE sys_user.wx_openid
        int rows = djsUserExtMapper.updateWxOpenid(userId, openid);
        if (rows == 0) {
            throw new ServiceException("绑定 openid 失败");
        }

        // 5. 颁发 token
        return issueToken(userId, openid, bo.getClientId());
    }

    @Override
    public WechatLoginVo employeePasswordLogin(String username, String password, String clientId) {
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            throw new ServiceException("账号或密码不能为空", DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR);
        }
        // 查 sys_user（启用未删）的 userId + BCrypt hash
        Map<String, Object> auth = djsUserExtMapper.selectAuthByUsername(username);
        String hash = auth == null ? null : (String) auth.get("password");
        // 账号不存在与密码错误统一提示，避免账号枚举
        if (hash == null || !BCrypt.checkpw(password, hash)) {
            throw loginRejected();
        }
        Long userId = ((Number) auth.get("userId")).longValue();
        // 真员工登录无 openid，颁真 token（issueToken 内按 userId 装配真菜单/角色权限）
        return issueToken(userId, null, StrUtil.blankToDefault(clientId, DjsAuthConstants.MP_APPLET_CLIENT_ID));
    }

    @Override
    public void assertUserLoginable(Long userId) {
        if (userId == null || djsUserExtMapper.selectLoginableUserId(userId) == null) {
            log.info("[applet-auth] 拒绝登录：userId={} 在 sys_user 不存在 / 已停用 / 已删除", userId);
            throw loginRejected();
        }
    }

    /**
     * 统一的登录拒绝异常：账号不存在 / 已停用 / 已删除 / 密码错误共用同一文案与业务码。
     *
     * <p>不区分具体原因（不回「该账号已停用」），否则可被用来枚举有效账号。</p>
     */
    private ServiceException loginRejected() {
        return new ServiceException(
            I18nMessages.t("applet.auth.login.rejected"),
            DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR);
    }

    // ---------------------- 内部辅助 ----------------------

    /**
     * jscode2session 调用。
     *
     * <p>mock 模式（djs.wechat.miniapp.app-id = "mock"）：把入参 code 直接当 openid 返回，方便本地调试。
     * 真模式：调 WxJava {@code getUserService().getSessionInfo(code)} 换取真实 openid。</p>
     *
     * @param code wx.login() 返回的临时 code
     * @return openid
     */
    private String jscode2openid(String code) {
        if (StrUtil.isBlank(code)) {
            throw new ServiceException("微信 code 不能为空");
        }
        if (MOCK_APP_ID.equalsIgnoreCase(wxAppId)) {
            // dev mock：约定前端传 'mock-openid-<员工 user_name>'，便于本地预绑场景测试
            log.info("[mock] jscode2openid code={} → openid 直接复用 code", code);
            return code;
        }
        assertWxConfigured();
        try {
            WxMaJscode2SessionResult session = wxMaService.getUserService().getSessionInfo(code);
            String openid = session.getOpenid();
            if (StrUtil.isBlank(openid)) {
                throw new ServiceException("微信 jscode2session 返回空 openid");
            }
            return openid;
        } catch (WxErrorException e) {
            log.error("微信 jscode2session 失败 code={}", code, e);
            throw new ServiceException("微信登录失败：" + e.getError().getErrorMsg());
        }
    }

    /**
     * 手机号快速验证 phoneCode → phone。
     *
     * <p>真模式：调 WxJava {@code getUserService().getPhoneNoInfo(phoneCode)} 解析微信授权手机号
     * （新版 code 直换，无需 sessionKey）。</p>
     *
     * <p><b>mock 模式（{@code wx.ma.app-id=mock}，本地 dev）拿不到真手机号</b>：mock appid 没有
     * 对应的 secret，换不了。这里直接抛出说明性异常，<b>不再返 null</b> —— 返 null 会被上游当成
     * 「手机号为空」抛出「手机号不能为空」，而客户端明明已经授权成功，报错完全误导（现场表现：
     * 微信弹窗选完手机号 → toast 说手机号不能为空）。<br>
     * 早期 mock 模式靠客户端直传 {@code bo.phone} 兜底，但那条路已因冒充漏洞被封（任何人 POST
     * 别人的手机号即可顶掉其微信绑定），前端也不再发该字段，所以 mock 下这条链路本就走不通。
     * 本地调试请改用「账号密码登录」，或把 {@code wx.ma.app-id/secret} 配成真值。</p>
     */
    private String resolvePhoneByCode(String phoneCode) {
        if (StrUtil.isBlank(phoneCode)) {
            return null;
        }
        if (MOCK_APP_ID.equalsIgnoreCase(wxAppId)) {
            log.warn("[mock] wx.ma.app-id=mock，无法用 phoneCode 换取手机号（phoneCode={}）", phoneCode);
            throw new ServiceException(
                "当前后端为本地调试(mock)配置，无法获取微信手机号；请改用「账号密码登录」，"
                    + "或将 wx.ma.app-id / secret 配成真实值",
                DjsAuthConstants.BIZ_CODE_PHONE_NOT_REGISTERED);
        }
        assertWxConfigured();
        try {
            WxMaPhoneNumberInfo info = wxMaService.getUserService().getPhoneNoInfo(phoneCode);
            return info.getPhoneNumber();
        } catch (WxErrorException e) {
            log.error("微信手机号快速验证失败 phoneCode={}", phoneCode, e);
            throw new ServiceException("获取微信手机号失败：" + e.getError().getErrorMsg());
        }
    }

    /**
     * 颁发 Sa-Token：构造最小化 LoginUser → LoginHelper.login。
     */
    private WechatLoginVo issueToken(Long userId, String openid, String clientId) {
        // 拉用户基本信息（用于 LoginUser 构造）
        String userName = djsUserExtMapper.selectUserName(userId);
        String nickName = djsUserExtMapper.selectNickName(userId);
        String tenantId = djsUserExtMapper.selectTenantId(userId);
        String currentFarmId = djsUserExtMapper.selectCurrentFarmId(userId);
        if (StrUtil.isBlank(currentFarmId)) {
            currentFarmId = DjsAuthConstants.DEFAULT_FARM_ID;
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setTenantId(tenantId);
        loginUser.setUsername(userName);
        // 真员工登录必填 nickname（真实姓名）：getInfo 回传它驱动 mp 首屏 / 人员选择器默认回显，
        // 不填则 mp EmployeePicker 兜底显「已选人员」占位（流程性问题 row5）。回落 userName 防空。
        loginUser.setNickname(StrUtil.blankToDefault(nickName, userName));
        loginUser.setUserType(UserType.SYS_USER.getUserType());
        loginUser.setClientKey(DjsAuthConstants.MP_APPLET_CLIENT_KEY);
        loginUser.setDeviceType("mp");
        // 实填 menuPermission + rolePermission（与 ruoyi SysLoginService 一致）：
        // SaPermissionImpl 对当前 session 自身的权限校验直接读这两个 Set，留空会让 @SaCheckPermission 全拒、
        // role-tabs / getInfo 取不到 role_key（员工进不去板块）。rolePermission 即 sys_role.role_key 集合。
        loginUser.setMenuPermission(permissionService.getMenuPermission(userId));
        loginUser.setRolePermission(permissionService.getRolePermission(userId));

        SaLoginParameter model = new SaLoginParameter();
        model.setDeviceType("mp");
        // 30 天 token TTL（doc/05 §4.4.3）
        model.setTimeout(TOKEN_TIMEOUT_SECONDS);
        model.setActiveTimeout(TOKEN_ACTIVE_TIMEOUT_SECONDS);
        model.setExtra(LoginHelper.CLIENT_KEY, clientId);
        // 把 current_farm_id 注入 token extra（FarmContextInterceptor V2 启用时读）
        model.setExtra("farmId", currentFarmId);
        LoginHelper.login(loginUser, model);

        // 拼响应
        WechatLoginVo vo = new WechatLoginVo();
        vo.setAccessToken(StpUtil.getTokenValue());
        // 回传「活跃期限」而非 token 总时长：前端按 expireIn 算本地过期时间来决定要不要跳登录页，
        // 给它 30 天而实际 activeTimeout 先到，会让前端以为还登着 → 冷启直接进业务页 → 请求 401
        // 才发现掉线（用户看到的就是「闪一下又被踢回登录」）。两者取小才是 token 真正的可用期限。
        vo.setExpireIn(Math.min(StpUtil.getTokenTimeout(), TOKEN_ACTIVE_TIMEOUT_SECONDS));
        vo.setClientId(clientId);
        vo.setOpenid(openid);
        vo.setUserId(userId);
        vo.setNickName(StrUtil.nullToDefault(loginUser.getNickname(), userName));
        vo.setCurrentFarmId(currentFarmId);
        return vo;
    }
}
