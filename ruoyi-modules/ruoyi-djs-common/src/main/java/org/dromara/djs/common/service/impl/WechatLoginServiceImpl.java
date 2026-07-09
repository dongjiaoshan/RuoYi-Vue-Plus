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
        // 1. 解析 openid：真实流程传 code（jscode2session 换 openid，客户端不经手 openid）；
        //    dev / mock 模式可直接透传 openid。code 优先。
        String openid = StrUtil.isNotBlank(bo.getCode()) ? jscode2openid(bo.getCode()) : bo.getOpenid();
        if (StrUtil.isBlank(openid)) {
            throw new ServiceException("缺少 code / openid，无法绑定");
        }

        // 2. 拿到真实手机号（dev：直接读 bo.phone；真实：调微信"手机号快速验证"接口换 phoneCode → phone）
        String phone = StrUtil.isNotBlank(bo.getPhone()) ? bo.getPhone() : resolvePhoneByCode(bo.getPhoneCode());
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
            throw new ServiceException("账号或密码错误", DjsAuthConstants.BIZ_CODE_PASSWORD_ERROR);
        }
        Long userId = ((Number) auth.get("userId")).longValue();
        // 真员工登录无 openid，颁真 token（issueToken 内按 userId 装配真菜单/角色权限）
        return issueToken(userId, null, StrUtil.blankToDefault(clientId, DjsAuthConstants.MP_APPLET_CLIENT_ID));
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
        if ("mock".equalsIgnoreCase(wxAppId)) {
            // dev mock：约定前端传 'mock-openid-<员工 user_name>'，便于本地预绑场景测试
            log.info("[mock] jscode2openid code={} → openid 直接复用 code", code);
            return code;
        }
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
     * <p>mock 模式：返 null（dev 走 bo.phone 字段直传）。真模式：调 WxJava
     * {@code getUserService().getPhoneNoInfo(phoneCode)} 解析微信授权手机号（新版 code 直换，无需 sessionKey）。</p>
     */
    private String resolvePhoneByCode(String phoneCode) {
        if (StrUtil.isBlank(phoneCode)) {
            return null;
        }
        if ("mock".equalsIgnoreCase(wxAppId)) {
            log.warn("[mock] resolvePhoneByCode phoneCode={} 返 null（dev 模式请传 phone 字段）", phoneCode);
            return null;
        }
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
