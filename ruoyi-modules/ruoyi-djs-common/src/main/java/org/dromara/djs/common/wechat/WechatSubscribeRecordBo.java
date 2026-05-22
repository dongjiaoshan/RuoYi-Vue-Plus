package org.dromara.djs.common.wechat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 微信订阅消息授权落库入参（SYS-INFRA-008）。
 *
 * <p>小程序前端 {@code wx.requestSubscribeMessage} 成功回调里 POST
 * {@code /applet/wechat/subscribe/record} 提交。后端 controller 以 {@code @SaCheckLogin}
 * 通过 sa-token 拿当前登录 userId（**不读 BO 里的 userId 字段**，避免被前端伪造）。
 * BO 里仍保留 {@code userId} 字段供 service 接受 controller 注入后的真实 userId，
 * 单测里也可直接构造此 BO。</p>
 *
 * <p>字段语义对齐 {@code mp_subscribe_record} 表（SYS-INIT-001）：</p>
 * <ul>
 *   <li>{@code openid}：微信 openid（前端 wx.login 拿到的）</li>
 *   <li>{@code templateId}：模板 ID，对应 {@code djs.wechat.miniapp.templates.*} 配置</li>
 *   <li>{@code subscribedAt}：授权时间，前端传 client side now</li>
 *   <li>{@code alwaysKeep}：用户是否勾选「总是保持以上选择」</li>
 *   <li>{@code expiredAt}：一次性订阅过期时间（alwaysKeep=false 时建议填）</li>
 * </ul>
 *
 * @author djs
 */
@Data
public class WechatSubscribeRecordBo {

    /**
     * 小程序登录用户 ID。
     *
     * <p>由 controller 用 {@code LoginHelper.getLoginUser().getUserId()} 覆盖写入，
     * **不信任前端传入的值**。BO 上仍声明 {@code @NotNull} 是为了 service 层重复授权
     * UPDATE 分支能根据 userId 命中已有记录；controller 入口阶段会在反序列化后覆盖。</p>
     */
    @NotNull
    private Long userId;

    /** 微信 openid（wx.login → code2Session 拿到）。 */
    @NotBlank
    private String openid;

    /** 微信订阅消息模板 ID（对齐后端 application.yml {@code djs.wechat.miniapp.templates.*}）。 */
    @NotBlank
    private String templateId;

    /** 授权时间（前端 wx.requestSubscribeMessage success 回调里 new Date()）。 */
    @NotNull
    private LocalDateTime subscribedAt;

    /**
     * 是否「总是保留以上选择」（对应微信 {@code acceptItemSettings.alwaysKeep}）。
     *
     * <p>true → 长期订阅，推送时不消耗 record（{@code used} 保持 0）；
     * false → 一次性订阅，推送成功后 mark {@code used=1}。</p>
     */
    @NotNull
    private Boolean alwaysKeep;

    /**
     * 一次性订阅过期时间（{@code alwaysKeep=false} 时建议传，微信侧 7 天有效期）。
     *
     * <p>留空表示「不设过期」，{@code findUsable} 仅按 {@code used} / {@code always_keep}
     * 筛选。</p>
     */
    private LocalDateTime expiredAt;
}
