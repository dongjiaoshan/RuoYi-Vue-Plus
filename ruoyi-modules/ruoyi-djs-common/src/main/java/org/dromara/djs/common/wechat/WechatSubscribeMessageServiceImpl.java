package org.dromara.djs.common.wechat;

import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.common.mapper.DjsUserExtMapper;
import org.springframework.stereotype.Service;

/**
 * {@link IWechatSubscribeMessageService} 实现（SYS-INFRA-006）。
 *
 * <h3>关键设计</h3>
 * <ul>
 *   <li><b>异步</b>：用 {@link CompletableFuture#supplyAsync} 默认 ForkJoinPool.commonPool
 *       —— 不引入 @EnableAsync / 自定义 Executor，避免对全局 Spring 配置侵入</li>
 *   <li><b>重试</b>：手写指数退避循环（{@link WechatProperties.Retry}），不引 spring-retry
 *       新依赖。最大 {@code maxAttempts} 次，间隔 {@code initialBackoffMs * multiplier^n}</li>
 *   <li><b>Mock</b>：{@link WechatProperties#isEffectiveMock()} 为 true 时，不调微信 API，
 *       只打 {@code [MOCK] push to openid=... type=... data={...}} 日志，仍 mark used=1</li>
 *   <li><b>不抛异常</b>：所有失败路径返回 {@link WxPushResult#fail} / {@link WxPushResult#skipped}，
 *       业务调用方不必 try/catch</li>
 *   <li><b>tenant_id</b>：mp_subscribe_record 是全局表，IGNORE 名单内，无 tenant_id 列，
 *       因此 entity / mapper 都不带租户字段，{@code WechatSubscribeRecord} 不继承 TenantEntity</li>
 * </ul>
 *
 * <h3>真实接入（V2 todo）</h3>
 * <p>当 {@code djs.wechat.miniapp.app-id} 配置为真实 AppID 且 {@code mock=false} 时，
 * 当前实现会走 {@link #realPush} 占位 —— 抛 IllegalStateException 提示「真实微信接入
 * 留 V2」。后续 ticket 接入时实现：</p>
 * <ol>
 *   <li>缓存 access_token（Redis，TTL 7000s 留 200s 安全垫）</li>
 *   <li>POST https://api.weixin.qq.com/cgi-bin/message/subscribe/send</li>
 *   <li>解析 errcode：40001/42001（token 失效）触发 token 刷新 + 重试</li>
 * </ol>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatSubscribeMessageServiceImpl implements IWechatSubscribeMessageService {

    private final DjsUserExtMapper djsUserExtMapper;
    private final WechatSubscribeRecordMapper recordMapper;
    private final WechatProperties properties;

    @Override
    public CompletableFuture<WxPushResult> push(Long userId, WechatMsgType msgType, Map<String, String> data) {
        return CompletableFuture.supplyAsync(() -> doPush(userId, msgType, data));
    }

    @Override
    public CompletableFuture<List<WxPushResult>> pushBatch(List<Long> userIds, WechatMsgType msgType,
                                                            Map<String, String> data) {
        if (userIds == null || userIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<CompletableFuture<WxPushResult>> futures = userIds.stream()
            .map(uid -> push(uid, msgType, data))
            .toList();
        return CompletableFuture
            .allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
    }

    // ---------------------------------------------------------------------
    // 内部实现
    // ---------------------------------------------------------------------

    /**
     * 同步推送主流程（在异步线程内执行）。所有失败路径返 result，不抛异常。
     */
    private WxPushResult doPush(Long userId, WechatMsgType msgType, Map<String, String> data) {
        // 1. 取 openid
        String openid = djsUserExtMapper.selectWxOpenid(userId);
        if (StrUtil.isBlank(openid)) {
            log.info("[wx-push] userId={} 未绑定 wx_openid，跳过 type={}", userId, msgType);
            return WxPushResult.skipped(userId, msgType, "wx_openid not bound");
        }

        // 2. 取 templateId
        String templateId = properties.getMiniapp().getTemplates().get(msgType.getCode());
        if (StrUtil.isBlank(templateId)) {
            log.warn("[wx-push] msgType={} 模板 ID 未配置（djs.wechat.miniapp.templates.{}），跳过",
                msgType, msgType.getCode());
            return WxPushResult.skipped(userId, msgType, "template id not configured");
        }

        // 3. 查授权记录（mock 模式也走查询；如无授权可直接推但不消耗 record）
        WechatSubscribeRecord record = recordMapper.findUsable(userId, templateId);
        if (record == null && !properties.isEffectiveMock()) {
            log.info("[wx-push] userId={} 未授权模板 {}(type={}), 跳过", userId, templateId, msgType);
            return WxPushResult.skipped(userId, msgType, "no usable subscribe record");
        }

        // 4. 执行推送（mock 或真发，带重试）
        int retries = 0;
        Exception lastError = null;
        WechatProperties.Retry retryCfg = properties.getMiniapp().getRetry();
        int maxAttempts = Math.max(1, retryCfg.getMaxAttempts());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (properties.isEffectiveMock()) {
                    mockPush(openid, msgType, templateId, data);
                } else {
                    realPushOverridable(openid, templateId, data);
                }
                // 成功 → mark used（仅当有 record 且 always_keep=0）
                if (record != null) {
                    recordMapper.markUsed(record.getId(), LocalDateTime.now());
                }
                if (attempt > 1) {
                    log.info("[wx-push] userId={} type={} 重试第 {} 次成功", userId, msgType, attempt - 1);
                }
                return WxPushResult.ok(userId, openid, msgType, attempt - 1, properties.isEffectiveMock());
            } catch (Exception ex) {
                lastError = ex;
                retries = attempt;
                log.warn("[wx-push] userId={} type={} 第 {}/{} 次失败: {}",
                    userId, msgType, attempt, maxAttempts, ex.getMessage());
                if (attempt < maxAttempts) {
                    sleepBackoff(retryCfg, attempt);
                }
            }
        }
        // 全部失败
        String err = lastError != null ? lastError.getMessage() : "unknown";
        log.error("[wx-push] userId={} type={} {} 次重试全部失败: {}", userId, msgType, maxAttempts, err);
        return WxPushResult.fail(userId, openid, msgType, retries, err);
    }

    /**
     * Mock 模式：不真调微信 API，只打日志。日志格式固定为
     * {@code [MOCK] push to openid=... type=... templateId=... data={...}}，
     * 供 testing-human 文档观察。
     */
    private void mockPush(String openid, WechatMsgType msgType, String templateId, Map<String, String> data) {
        log.info("[MOCK] push to openid={} type={} templateId={} data={}",
            openid, msgType, templateId, data);
    }

    /**
     * 真实推送占位（V2 接入微信 API 后实现）。
     *
     * <p>{@code protected} 而非 {@code private}：方便单测用 anonymous 子类覆盖以模拟
     * 微信 API 5xx / token 失效 / 重试场景，无需引入 PowerMock 或 wiremock。</p>
     *
     * @throws IllegalStateException 当前阶段总是抛出，提示走 mock 模式
     */
    protected void realPushOverridable(String openid, String templateId, Map<String, String> data) {
        throw new IllegalStateException(
            "真实微信订阅消息推送留 V2 接入（需 AppID + access_token + 模板 ID 真实配置）；"
                + "当前阶段请保持 djs.wechat.miniapp.mock=true / app-id=mock");
    }

    /**
     * 指数退避睡眠：{@code initialBackoffMs * multiplier^(attempt-1)}。
     *
     * @param cfg     配置
     * @param attempt 当前已完成的尝试次数（1 表示第一次失败后）
     */
    private void sleepBackoff(WechatProperties.Retry cfg, int attempt) {
        double factor = Math.pow(Math.max(1.0d, cfg.getBackoffMultiplier()), attempt - 1);
        long sleepMs = (long) (Math.max(0, cfg.getInitialBackoffMs()) * factor);
        if (sleepMs <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("retry backoff interrupted", e);
        }
    }

    // ---------------------------------------------------------------------
    // 测试钩子（package-private）：让单测可以构造 List 结果检查
    // ---------------------------------------------------------------------

    /** 单测可调，按需。 */
    List<WxPushResult> pushAllSync(List<Long> userIds, WechatMsgType msgType, Map<String, String> data) {
        List<WxPushResult> out = new ArrayList<>(userIds.size());
        for (Long uid : userIds) {
            out.add(doPush(uid, msgType, data));
        }
        return out;
    }
}
