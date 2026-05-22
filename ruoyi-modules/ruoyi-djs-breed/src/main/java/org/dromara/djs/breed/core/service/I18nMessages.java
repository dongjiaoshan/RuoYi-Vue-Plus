package org.dromara.djs.breed.core.service;

import org.dromara.common.core.utils.MessageUtils;

/**
 * 猪只状态机 i18n 消息桥接（BRD-CORE-001）。
 *
 * <p>{@link MessageUtils#message} 内部依赖
 * {@code SpringUtils.getBean(MessageSource.class)} 的 static 初始化，单测无 Spring
 * 上下文时整个 {@code MessageUtils} 类 init 阶段就 {@code ExceptionInInitializerError}。
 * 本类提供 try/catch 兜底：拿不到 MessageSource 时回退为
 * {@code "<key>: arg1, arg2..."} 串，业务异常依然能落到 {@link org.dromara.common.core.exception.ServiceException#getMessage}。</p>
 *
 * <p>生产路径（admin 启动后 Spring 上下文完整）行为与直接调用
 * {@link MessageUtils#message} 完全一致。</p>
 *
 * @author djs
 * @since BRD-CORE-001
 */
public final class I18nMessages {

    private I18nMessages() {
    }

    public static String t(String key, Object... args) {
        try {
            return MessageUtils.message(key, args);
        } catch (Throwable e) {
            // 无 Spring 上下文（单测 / 早期启动）：返回 key + 参数串，方便单测断言 key 子串
            if (args == null || args.length == 0) {
                return key;
            }
            StringBuilder sb = new StringBuilder(key).append(": ");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(args[i]);
            }
            return sb.toString();
        }
    }
}
