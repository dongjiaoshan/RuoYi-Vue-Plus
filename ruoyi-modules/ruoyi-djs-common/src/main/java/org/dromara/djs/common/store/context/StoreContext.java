package org.dromara.djs.common.store.context;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaStorage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;

import java.util.function.Supplier;

/**
 * 门店上下文助手（STORE-PERM-001）。
 *
 * <h3>定位</h3>
 * <p>承载「当前请求作用的门店 ID」，驱动 {@link StoreLineHandler} 的行级数据隔离。
 * 仿 {@code TenantHelper}：上下文存在 {@link SaHolder#getStorage()}（Sa-Token 请求级存储，
 * 请求结束自动销毁），无需手动 clear，避免线程池串号。</p>
 *
 * <h3>写入</h3>
 * <p>由 {@link StoreContextInterceptor#preHandle} 读请求头 {@code Current-Store-Id} 写入。</p>
 *
 * <h3>读取</h3>
 * <p>{@link StoreLineHandler#getStoreId()} 取值拼 {@code WHERE store_id=?}；为空（非门店请求 /
 * 未选门店）则 {@link StoreLineHandler#ignoreTable} 一律不加过滤（空上下文不过滤铁律）。</p>
 *
 * <h3>放行</h3>
 * <p>聚合 / 管理后台跨门店视图用 {@link #ignore(Runnable)} / {@link #ignore(Supplier)} 临时关闭过滤，
 * 内部用 ThreadLocal&lt;Boolean&gt; 标记（与 SaStorage 的 storeId 正交：storeId 存"过滤哪家店"，
 * ignore 标记"本段代码完全不过滤"）。</p>
 *
 * @author djs
 * @since STORE-PERM-001
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StoreContext {

    /**
     * SaStorage 中存当前门店 ID 的 key。
     */
    private static final String STORE_ID_KEY = "djs:store:currentStoreId";

    /**
     * 请求头名：前端全局门店选择器注入。
     */
    public static final String HEADER_STORE_ID = "Current-Store-Id";

    /**
     * 忽略门店过滤标记（{@link #ignore} 期间为 true）。请求级，但跨方法调用走 ThreadLocal，
     * 必须在 finally 中复位（{@link #ignore} 已保证）。
     */
    private static final ThreadLocal<Boolean> IGNORE_STORE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 设置当前请求门店 ID（null / 空白视为清除）。
     *
     * @param storeId 门店 ID（字符串形式，便于直接来自请求头）
     */
    public static void setStoreId(String storeId) {
        SaStorage storage = currentStorage();
        if (storage == null) {
            return;
        }
        if (StringUtils.isBlank(storeId)) {
            storage.delete(STORE_ID_KEY);
        } else {
            storage.set(STORE_ID_KEY, storeId.trim());
        }
    }

    /**
     * 获取当前请求门店 ID（无则返 null）。
     *
     * @return 门店 ID 字符串；非门店请求 / 未选门店 / 无请求上下文 → null
     */
    public static String getStoreId() {
        SaStorage storage = currentStorage();
        if (storage == null) {
            return null;
        }
        String storeId = storage.getString(STORE_ID_KEY);
        return StringUtils.isBlank(storeId) ? null : storeId;
    }

    /**
     * 当前是否处于忽略门店过滤状态（{@link #ignore} 段内 / 超管 / 租管）。
     *
     * @return true=不应过滤
     */
    public static boolean isIgnore() {
        if (Boolean.TRUE.equals(IGNORE_STORE.get())) {
            return true;
        }
        // 超管 / 租管 bypass 门店墙（看全部门店）
        try {
            return LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin();
        } catch (Exception e) {
            // 无登录上下文（系统初始化 / 健康检查）→ 不视为放行，由空 storeId 决定不过滤
            return false;
        }
    }

    /**
     * 在忽略门店过滤的上下文中执行（无返回值）。聚合 / 管理后台跨门店视图用。
     *
     * @param handle 执行体
     */
    public static void ignore(Runnable handle) {
        boolean previous = Boolean.TRUE.equals(IGNORE_STORE.get());
        IGNORE_STORE.set(Boolean.TRUE);
        try {
            handle.run();
        } finally {
            IGNORE_STORE.set(previous);
        }
    }

    /**
     * 在忽略门店过滤的上下文中执行（有返回值）。
     *
     * @param handle 执行体
     * @param <T>    返回类型
     * @return 执行体返回值
     */
    public static <T> T ignore(Supplier<T> handle) {
        boolean previous = Boolean.TRUE.equals(IGNORE_STORE.get());
        IGNORE_STORE.set(Boolean.TRUE);
        try {
            return handle.get();
        } finally {
            IGNORE_STORE.set(previous);
        }
    }

    /**
     * 取当前 Sa-Token 请求级存储；无请求上下文时返 null（不抛异常）。
     */
    private static SaStorage currentStorage() {
        try {
            return SaHolder.getStorage();
        } catch (Exception e) {
            return null;
        }
    }
}
