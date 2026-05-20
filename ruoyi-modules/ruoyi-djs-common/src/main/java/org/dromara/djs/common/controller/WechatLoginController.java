package org.dromara.djs.common.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.domain.bo.WechatBindPhoneBo;
import org.dromara.djs.common.domain.bo.WechatLoginBo;
import org.dromara.djs.common.domain.vo.WechatLoginVo;
import org.dromara.djs.common.service.IWechatLoginService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信小程序登录 Controller。
 *
 * <p>独立于 ruoyi 自带 {@code org.dromara.web.controller.AuthController}（强约束 #1 不动 ruoyi 自带
 * 模块源码）。URL 前缀 {@code /djs/auth/wechat/*} 区别于 ruoyi 的 {@code /auth/*}。</p>
 *
 * <p>两个端点都用 {@link SaIgnore} 跳过登录校验（登录前无 token）。</p>
 *
 * @author djs
 */
@Slf4j
@SaIgnore
@RestController
@RequestMapping("/djs/auth/wechat")
@RequiredArgsConstructor
public class WechatLoginController {

    private final IWechatLoginService wechatLoginService;

    /**
     * 微信小程序登录（pure openid 流程）。
     *
     * <p>入参示例：</p>
     * <pre>
     * POST /djs/auth/wechat/login
     * {
     *   "code": "wx_login_code_here",
     *   "clientId": "mp-applet-dongjiaoshan"
     * }
     * </pre>
     *
     * <p>响应：</p>
     * <ul>
     *   <li>200 + WechatLoginVo（含 access_token）→ openid 已绑定，登录成功</li>
     *   <li>40001 + message → openid 未绑定，前端需引导走 bind-phone</li>
     *   <li>500 + message → 微信 jscode2session 失败 / 系统错误</li>
     * </ul>
     */
    @PostMapping("/login")
    public R<WechatLoginVo> login(@Valid @RequestBody WechatLoginBo bo) {
        try {
            return R.ok(wechatLoginService.wechatLogin(bo));
        } catch (ServiceException e) {
            log.info("微信登录业务异常 code={} msg={}", e.getCode(), e.getMessage());
            // ServiceException.code 用 Integer，可能 null（默认 ok 路径走不到这）
            int code = e.getCode() != null ? e.getCode() : 500;
            return R.fail(code, e.getMessage());
        }
    }

    /**
     * 首次绑定手机号（dev mode 直接传 phone；生产用 phoneCode 走微信"手机号快速验证"）。
     *
     * <p>入参示例：</p>
     * <pre>
     * POST /djs/auth/wechat/bind-phone
     * {
     *   "openid":   "wx_openid_returned_from_login",
     *   "clientId": "mp-applet-dongjiaoshan",
     *   "phone":    "13800138000"   // dev 模式
     *   // 或 "phoneCode": "wx_get_phone_number_code"  // 生产模式
     * }
     * </pre>
     *
     * <p>响应：</p>
     * <ul>
     *   <li>200 + WechatLoginVo → 绑定 + 登录成功</li>
     *   <li>40002 + message → 手机号未在员工库</li>
     * </ul>
     */
    @PostMapping("/bind-phone")
    public R<WechatLoginVo> bindPhone(@Valid @RequestBody WechatBindPhoneBo bo) {
        try {
            return R.ok(wechatLoginService.bindPhone(bo));
        } catch (ServiceException e) {
            log.info("微信绑定手机号业务异常 code={} msg={}", e.getCode(), e.getMessage());
            int code = e.getCode() != null ? e.getCode() : 500;
            return R.fail(code, e.getMessage());
        }
    }
}
