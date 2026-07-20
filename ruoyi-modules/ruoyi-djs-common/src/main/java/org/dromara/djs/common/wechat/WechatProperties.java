package org.dromara.djs.common.wechat;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 东角山微信集成配置（SYS-INFRA-006 + SYS-AUTH-001 复用同一 prefix）。
 *
 * <p>application.yml 节选：</p>
 * <pre>{@code
 * djs:
 *   wechat:
 *     miniapp:
 *       app-id: ${WX_MA_APPID:mock}          # mock 表示走 dev mock 模式
 *       secret: ${WX_MA_SECRET:}
 *       mock: ${WX_MA_MOCK:true}             # 显式开关；app-id=mock 时强制 true
 *       templates:                            # 4 类订阅消息模板 ID（V1 占位空字符串）
 *         slaughter_notice: ""
 *         stock_alert: ""
 *         sales_summary: ""
 *         internal_feedback: ""
 *       retry:
 *         max-attempts: 3                     # 含首次，共尝试 3 次
 *         initial-backoff-ms: 500             # 指数退避起点
 *         backoff-multiplier: 2.0             # 退避倍数
 * }</pre>
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li>{@code app-id} 默认 {@code "mock"} —— 没拿到真实 AppID 前安全可跑</li>
 *   <li>{@link #isEffectiveMock()}：app-id="mock" 或 mock=true 都视为 mock 模式</li>
 *   <li>templates 中某 key 留空 → 推送时跳过该类型并日志 WARN</li>
 * </ul>
 *
 * @author djs
 */
@Data
@Component
@ConfigurationProperties(prefix = "djs.wechat")
public class WechatProperties {

    /** 小程序配置。 */
    private Miniapp miniapp = new Miniapp();

    /**
     * 是否处于 mock 模式（不真调微信 API）。
     *
     * <p>判定规则：{@code miniapp.mock=true} 或 {@code miniapp.appId} 为空 / 等于 "mock"。</p>
     */
    public boolean isEffectiveMock() {
        if (miniapp.isMock()) {
            return true;
        }
        String appId = miniapp.getAppId();
        return appId == null || appId.isBlank() || "mock".equalsIgnoreCase(appId);
    }

    @Data
    public static class Miniapp {

        /** 小程序 AppID，默认 "mock"。 */
        private String appId = "mock";

        /** 小程序 AppSecret，默认空（mock 模式下不需要）。 */
        private String secret = "";

        /** 显式 mock 开关；与 app-id="mock" 任一为 true 即走 mock 通道。 */
        private boolean mock = true;

        /**
         * 模板 ID 映射：key 是 {@link WechatMsgType#getCode()}，value 是微信后台分配的 template_id。
         *
         * <p>V1 占位空字符串；真实接入后由运维填入。</p>
         */
        private Map<String, String> templates = new HashMap<>();

        /** 推送重试配置。 */
        private Retry retry = new Retry();
    }

    @Data
    public static class Retry {

        /** 最大尝试次数（含首次），默认 3。 */
        private int maxAttempts = 3;

        /** 首次失败后等待毫秒数，默认 500ms。 */
        private long initialBackoffMs = 500L;

        /** 指数退避倍数，默认 2.0（500 → 1000 → 2000）。 */
        private double backoffMultiplier = 2.0d;
    }
}
