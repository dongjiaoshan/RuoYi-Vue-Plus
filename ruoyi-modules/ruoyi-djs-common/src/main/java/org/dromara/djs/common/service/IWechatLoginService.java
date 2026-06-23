package org.dromara.djs.common.service;

import org.dromara.djs.common.domain.bo.WechatBindPhoneBo;
import org.dromara.djs.common.domain.bo.WechatLoginBo;
import org.dromara.djs.common.domain.vo.WechatLoginVo;

/**
 * 微信小程序登录服务。
 *
 * <p>本接口暴露 2 个端点的核心业务能力，由 {@link org.dromara.djs.common.controller.applet.AppletAuthController}
 * 调用：</p>
 * <ol>
 *   <li>{@link #wechatLogin(WechatLoginBo)} — 凭 code 拿 openid，命中 sys_user.wx_openid 则颁 token；
 *       未命中抛 {@link org.dromara.djs.common.constant.DjsAuthConstants#BIZ_CODE_WECHAT_NEED_BIND}
 *       业务码（Controller 层捕获后返 R.fail）。</li>
 *   <li>{@link #bindPhone(WechatBindPhoneBo)} — 凭 phone（或 phoneCode）查 sys_user.phonenumber，
 *       命中则 UPDATE wx_openid 并颁 token；未命中抛
 *       {@link org.dromara.djs.common.constant.DjsAuthConstants#BIZ_CODE_PHONE_NOT_REGISTERED}。</li>
 * </ol>
 *
 * <p><b>V1 实现说明</b>：</p>
 * <ul>
 *   <li>jscode2session 走 ruoyi 自带的 JustAuth {@code AuthWechatMiniProgramRequest}，需要 application.yml
 *       配 {@code djs.wechat.miniapp.appId / appSecret}（dev 环境用 mock 模式：appId=mock 时 service 直接
 *       回构造一个伪 openid，便于本地调试）。</li>
 *   <li>getPhoneNumber"手机号快速验证"接口 V1 不调微信（0.03 元/次，等客户充值），直接读
 *       {@link WechatBindPhoneBo#getPhone()}；待 SYS-INFRA-006 微信订阅消息 ticket 引入
 *       weixin-java-miniapp 后再切换。</li>
 * </ul>
 *
 * @author djs
 */
public interface IWechatLoginService {

    /**
     * 凭 wx.login() 的 code 登录。
     *
     * @param bo 登录入参（code + clientId）
     * @return 已颁发 token 的 WechatLoginVo
     * @throws org.dromara.common.core.exception.ServiceException
     *         当 openid 未绑定时抛 code = 40001（{@code BIZ_CODE_WECHAT_NEED_BIND}）；
     *         当微信 jscode2session 失败时抛 code = 500
     */
    WechatLoginVo wechatLogin(WechatLoginBo bo);

    /**
     * 凭 phone 绑定 openid 并登录（首次绑定 / 换微信号场景）。
     *
     * @param bo 绑定入参（openid + clientId + phone | phoneCode）
     * @return 已颁发 token 的 WechatLoginVo
     * @throws org.dromara.common.core.exception.ServiceException
     *         当 phone 不在 sys_user 中时抛 code = 40002（{@code BIZ_CODE_PHONE_NOT_REGISTERED}）
     */
    WechatLoginVo bindPhone(WechatBindPhoneBo bo);
}
