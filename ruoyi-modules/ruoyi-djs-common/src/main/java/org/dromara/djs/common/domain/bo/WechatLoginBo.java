package org.dromara.djs.common.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 微信小程序登录入参。
 *
 * 流程：小程序 wx.login() → 拿 jsCode → 调本端点 → 后端 jscode2session 拿 openid →
 *   1) 命中 sys_user.wx_openid → 直接颁发 Sa-Token；
 *   2) 未命中 → 返 BIZ_CODE_WECHAT_NEED_BIND（40001）+ 携 openid 给前端走 bind-phone 流程。
 *
 * @author djs
 * @see org.dromara.djs.common.controller.WechatLoginController#login
 */
@Data
public class WechatLoginBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 微信小程序 wx.login() 返回的 code（≤32 位临时凭证，5min 有效）
     */
    @NotBlank(message = "微信登录 code 不能为空")
    private String code;

    /**
     * 小程序 client_id（sys_client.client_id，固定 'mp-applet-dongjiaoshan'）
     * SaTokenLogin 颁发 token 时绑定到此 client。
     */
    @NotBlank(message = "clientId 不能为空")
    private String clientId;
}
