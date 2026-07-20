package org.dromara.djs.common.wechat;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信订阅授权落库 Controller（SYS-INFRA-008）。
 *
 * <p>URL 前缀 {@code /applet/wechat/subscribe/*}：</p>
 * <ul>
 *   <li>{@code POST /applet/wechat/subscribe/record} — 写入一条订阅授权记录</li>
 * </ul>
 *
 * <p><b>鉴权</b>：{@code @SaCheckLogin} 走真 sa-token 链路（与
 * {@link org.dromara.djs.common.controller.applet.AppletAuthController} 颁发的小程序
 * mp token 兼容）。<strong>不读 BO 里的 userId 字段</strong>，而是从
 * {@link LoginHelper#getLoginUser()} 取，避免前端伪造他人 userId 写脏数据。</p>
 *
 * <p><b>与 SYS-INFRA-006 联动</b>：本端点写入后，
 * {@link WechatSubscribeMessageServiceImpl} 推送时通过
 * {@link WechatSubscribeRecordMapper#findUsable} 查到本次授权 →
 * mock 模式打日志 + {@code markUsed}（{@code alwaysKeep=1} 时不消耗）。</p>
 *
 * @author djs
 */
@Slf4j
@RestController
@RequestMapping("/applet/wechat/subscribe")
@RequiredArgsConstructor
@Validated
public class WechatSubscribeRecordController {

    private final IWechatSubscribeRecordService subscribeRecordService;

    /**
     * 写入一条订阅授权记录（小程序端 {@code wx.requestSubscribeMessage} 成功回调调用）。
     *
     * <p>请求体示例：</p>
     * <pre>
     * POST /applet/wechat/subscribe/record
     * Authorization: Bearer &lt;mp token&gt;
     * {
     *   "openid": "o_xxx",
     *   "templateId": "tmpl_slaughter_001",
     *   "subscribedAt": "2026-05-21T14:30:00",
     *   "alwaysKeep": false,
     *   "expiredAt": "2026-05-28T14:30:00"
     * }
     * </pre>
     *
     * <p>响应：{@code R.ok(recordId)} —— INSERT 返回新雪花 ID，UPDATE 返回已存在行 ID。</p>
     *
     * @param bo 入参（{@code userId} 由本方法用 sa-token 覆盖）
     * @return 命中 record 主键 id
     */
    @SaCheckLogin
    @PostMapping("/record")
    public R<Long> record(@Valid @RequestBody WechatSubscribeRecordBo bo) {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new ServiceException("未登录", 401);
        }
        // 强制覆盖：以 sa-token 当前用户为准，不信任前端传入
        bo.setUserId(loginUser.getUserId());
        Long recordId = subscribeRecordService.insertBo(bo);
        log.info("[subscribe-record] userId={} templateId={} → recordId={}",
            loginUser.getUserId(), bo.getTemplateId(), recordId);
        return R.ok(recordId);
    }
}
