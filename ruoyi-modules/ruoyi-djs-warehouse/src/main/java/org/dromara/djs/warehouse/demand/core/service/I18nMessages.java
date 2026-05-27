package org.dromara.djs.warehouse.demand.core.service;

import org.dromara.common.core.utils.MessageUtils;

/**
 * 需求状态机 i18n 桥接（WMS-DEMAND-001）。
 *
 * <p>{@link MessageUtils#message} 内部依赖 {@code SpringUtils.getBean(MessageSource.class)} 的
 * static 初始化；单测无 Spring 上下文时整个 {@code MessageUtils} 类 init 阶段就
 * {@code ExceptionInInitializerError}。本类提供 try/catch 兜底：拿不到 MessageSource 时回退为
 * {@code "<key>: arg1, arg2..."} 串，业务异常依然能落到
 * {@link org.dromara.common.core.exception.ServiceException#getMessage}。</p>
 *
 * <p>生产路径（admin 启动后 Spring 上下文完整）行为与直接调用 {@link MessageUtils#message} 一致。
 * 与 {@code djs-breed.core.service.I18nMessages} 完全同实现 —— 暂未 promote 到 djs-common
 * （等 3 个模块都用到时再抽，参 D08 _open-issues 跟踪）。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
public final class I18nMessages {

    private I18nMessages() {
    }

    public static String t(String key, Object... args) {
        try {
            return MessageUtils.message(key, args);
        } catch (Throwable e) {
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
