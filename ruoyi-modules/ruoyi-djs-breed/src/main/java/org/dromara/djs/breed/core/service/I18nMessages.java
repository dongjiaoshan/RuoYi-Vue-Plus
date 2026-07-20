package org.dromara.djs.breed.core.service;

/**
 * @deprecated 已 promote 到 {@link org.dromara.djs.common.util.I18nMessages}（D08 closing）。
 * 本类作转发壳保留 binary compatibility；新代码请直接 import djs-common 那份。
 */
@Deprecated
public final class I18nMessages {

    private I18nMessages() {
    }

    public static String t(String key, Object... args) {
        return org.dromara.djs.common.util.I18nMessages.t(key, args);
    }
}
