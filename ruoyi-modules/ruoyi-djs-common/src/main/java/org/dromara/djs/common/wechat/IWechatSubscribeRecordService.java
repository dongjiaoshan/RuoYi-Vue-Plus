package org.dromara.djs.common.wechat;

/**
 * 微信订阅消息授权写入服务（SYS-INFRA-008）。
 *
 * <p>读端在 SYS-INFRA-006 已实现（{@link IWechatSubscribeMessageService} 推送时通过
 * {@link WechatSubscribeRecordMapper#findUsable} 查授权 + {@code markUsed}）。本服务
 * 负责写端：小程序前端 {@code wx.requestSubscribeMessage} 成功回调把授权状态 POST
 * 回后端，落 {@code mp_subscribe_record} 表，供下次推送时 {@code findUsable} 命中。</p>
 *
 * @author djs
 */
public interface IWechatSubscribeRecordService {

    /**
     * 写入一条订阅授权记录。
     *
     * <p><b>重复授权语义</b>：若同 ({@code user_id}, {@code template_id}, {@code used=0})
     * 已存在记录（用户多次点击订阅但还没消耗），则 <strong>UPDATE 覆盖</strong>
     * {@code subscribed_at} / {@code expired_at} / {@code always_keep} / {@code openid}，
     * 而不是再 INSERT 一行 —— 避免一个用户对同一 template 累积多条 used=0 行污染
     * {@code findUsable} 的 FIFO 查询。</p>
     *
     * <p><b>不带 tenant_id</b>：{@code mp_subscribe_record} 在 SYS-INFRA-007 多租户拦截器
     * IGNORE 名单内，全局共享表，不写也不查 {@code tenant_id} 列。</p>
     *
     * @param bo 入参（{@code userId} 由 controller 用 {@code LoginHelper} 覆盖）
     * @return 命中 record 的 id（INSERT 走新雪花 ID；UPDATE 走旧 ID）
     */
    Long insertBo(WechatSubscribeRecordBo bo);
}
