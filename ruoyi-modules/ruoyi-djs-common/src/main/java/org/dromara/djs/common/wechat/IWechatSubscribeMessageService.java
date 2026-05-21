package org.dromara.djs.common.wechat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 微信小程序订阅消息推送服务（SYS-INFRA-006）。
 *
 * <p>负责把业务事件（出栏 / 库存告警 / 销售汇总 / 内测反馈）以微信订阅消息形式
 * 推送到员工小程序。所有 push 方法**异步**返回 {@link CompletableFuture}，
 * 调用方不阻塞、不需 try/catch（失败由 service 内部记录 + 返回 fail result）。</p>
 *
 * <h3>使用方式（业务 ticket 调用样例）</h3>
 * <pre>{@code
 * // 出栏审核通过事件 listener 中
 * wechatSubscribeMessageService.push(
 *     evt.getCreatorUserId(),
 *     WechatMsgType.SLAUGHTER_NOTICE,
 *     Map.of(
 *         "thing1", evt.getEarNo(),
 *         "date2",  evt.getApprovedAt().toString(),
 *         "thing3", "已审核通过"
 *     )
 * );
 * }</pre>
 *
 * <p>调用方<strong>不必</strong> {@code .get()} —— 推送失败不应阻塞业务回滚。
 * 如需观察结果（如审计），订阅 {@code .thenAccept(result -> ...)} 即可。</p>
 *
 * @author djs
 */
public interface IWechatSubscribeMessageService {

    /**
     * 推送一条订阅消息给指定用户。
     *
     * <p>处理流程（impl 见 {@link WechatSubscribeMessageServiceImpl}）：</p>
     * <ol>
     *   <li>查 sys_user.wx_openid，未绑定 → 返 {@link WxPushResult#skipped}</li>
     *   <li>查 application.yml 中该 msgType 的 templateId，留空 → 返 skipped</li>
     *   <li>查 mp_subscribe_record 找一条可用授权，未授权 → 返 skipped</li>
     *   <li>mock 模式：日志 [MOCK] + 返 ok；真发：HTTP POST 微信 API + 重试</li>
     *   <li>成功后 mark used=1（always_keep=1 时跳过 mark）</li>
     * </ol>
     *
     * @param userId  接收者用户 ID（sys_user.user_id）
     * @param msgType 消息类型枚举
     * @param data    模板变量 map（key 是模板字段如 thing1 / date2，value 是实际值）
     * @return 异步推送结果
     */
    CompletableFuture<WxPushResult> push(Long userId, WechatMsgType msgType, Map<String, String> data);

    /**
     * 批量推送相同消息给多个用户（如出栏给一组人 / 库存告警给整个仓管组）。
     *
     * <p>内部对每个 userId 调 {@link #push}，allOf 等待所有完成后返回结果列表。
     * 任一失败不影响其他人的推送。</p>
     *
     * @param userIds 接收者 user_id 列表
     * @param msgType 消息类型
     * @param data    模板变量
     * @return 异步结果列表（顺序与 userIds 一致）
     */
    CompletableFuture<List<WxPushResult>> pushBatch(List<Long> userIds, WechatMsgType msgType, Map<String, String> data);
}
