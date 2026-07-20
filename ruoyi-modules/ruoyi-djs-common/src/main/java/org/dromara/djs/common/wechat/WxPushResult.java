package org.dromara.djs.common.wechat;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 微信订阅消息单次推送结果（SYS-INFRA-006）。
 *
 * <p>{@link IWechatSubscribeMessageService#push(Long, WechatMsgType, java.util.Map)} 与
 * {@code pushBatch} 的返回元素。包含 success 标志 + 上下文（userId / openid / msgType /
 * 失败原因 / 已重试次数）便于调用方记录或上报告警。</p>
 *
 * <p>注意：本对象**不**对外暴露 mp_subscribe_record 的主键 —— 因为该表是「订阅授权记录」
 * 而非「推送日志」，一条推送可能没有对应 record（如用户未授权或 mock 模式跳过）。</p>
 *
 * @author djs
 */
@Getter
@Builder
@ToString
public class WxPushResult {

    /** 是否成功推送（mock 模式下，只要找到 openid 即视为成功）。 */
    private final boolean success;

    /** 接收者 user_id。 */
    private final Long userId;

    /** 命中的 openid；未绑定时为 null。 */
    private final String openid;

    /** 消息类型。 */
    private final WechatMsgType msgType;

    /** 失败原因；success=true 时为 null。 */
    private final String errorMsg;

    /** 实际重试次数（0 表示一次成功）。 */
    private final int retryCount;

    /** 是否走了 mock 通道（未真调微信 API）。 */
    private final boolean mock;

    /** 快捷构造成功结果（mock 或真发都用）。 */
    public static WxPushResult ok(Long userId, String openid, WechatMsgType type, int retries, boolean mock) {
        return WxPushResult.builder()
            .success(true).userId(userId).openid(openid).msgType(type)
            .retryCount(retries).mock(mock).build();
    }

    /** 快捷构造失败结果。 */
    public static WxPushResult fail(Long userId, String openid, WechatMsgType type, int retries, String err) {
        return WxPushResult.builder()
            .success(false).userId(userId).openid(openid).msgType(type)
            .retryCount(retries).errorMsg(err).mock(false).build();
    }

    /** 快捷构造「未授权/未绑定 openid」跳过结果（不算失败但 success=false）。 */
    public static WxPushResult skipped(Long userId, WechatMsgType type, String reason) {
        return WxPushResult.builder()
            .success(false).userId(userId).msgType(type).errorMsg(reason)
            .retryCount(0).mock(false).build();
    }
}
