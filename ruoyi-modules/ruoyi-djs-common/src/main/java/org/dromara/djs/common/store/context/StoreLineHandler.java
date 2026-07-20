package org.dromara.djs.common.store.context;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.dromara.common.core.utils.StringUtils;

/**
 * 门店行级隔离处理器（STORE-PERM-001）。
 *
 * <h3>复用 MP 单列等值注入机制</h3>
 * <p>MyBatis-Plus 的 {@code TenantLineInnerInterceptor} 本质是「按 handler 指定的列做等值过滤 +
 * INSERT 补列」的通用拦截器，不绑定语义。这里把它的「列」指定为 {@code store_id}、「值」指定为
 * {@link StoreContext#getStoreId()}，即得到独立于 ruoyi 多租户的第二条行级隔离链。两个
 * {@code TenantLineInnerInterceptor} 实例（ruoyi tenant + 本 store）叠加生效：
 * {@code WHERE tenant_id=? AND store_id=?}。</p>
 *
 * <h3>三条安全铁律（{@link #ignoreTable}）</h3>
 * <ol>
 *   <li><b>非白名单表 → 不过滤</b>：仅 {@link StoreProperties#getIncludes()} 中的表参与；
 *       其余所有表（ruoyi 自带 / 其他业务域 / sys_*）一律放行，绝不误伤全 app SQL。</li>
 *   <li><b>编程式 ignore() → 不过滤</b>：{@link StoreContext#isProgrammaticIgnore()} 为 true 时放行
 *       （聚合 / 跨门店视图主动 {@link StoreContext#ignore} 包裹）。</li>
 *   <li><b>空上下文 → 不过滤</b>：{@link StoreContext#getStoreId()} 为空（非门店请求 /
 *       未选门店 / 非 /djs/store/** 域）时，即便是白名单表也不加 WHERE，否则会错误清空数据。</li>
 *   <li><b>显式选店 → 按店过滤（含超管 / 租管）</b>：{@link StoreContext#getStoreId()} 非空时一律
 *       拼 {@code WHERE store_id=?}，超管 / 租管不再自动放行——「选了哪家店就只看哪家」优先于身份
 *       （门店板块统一口径，option B）。门店上下文仅由 {@link StoreContextInterceptor} 对 /djs/store/**
 *       域注入，故仓库 /djs/warehouse/** 等跨门店共享表读取时 storeId 恒空 → 看全部，不受影响。</li>
 * </ol>
 *
 * @author djs
 * @since STORE-PERM-001
 */
@Slf4j
@RequiredArgsConstructor
public class StoreLineHandler implements TenantLineHandler {

    /**
     * 隔离列名。
     */
    private static final String STORE_ID_COLUMN = "store_id";

    private final StoreProperties storeProperties;

    /**
     * 注入到 WHERE / INSERT 的门店 ID 表达式。仅在 {@link #ignoreTable} 返 false（即需要过滤）时被调用，
     * 此时 storeId 必非空（否则铁律 2 已放行）；解析失败时记日志并由 ignoreTable 保证不会走到这里。
     */
    @Override
    public Expression getTenantId() {
        String storeId = StoreContext.getStoreId();
        // ignoreTable 已保证非空才会到这里；防御性兜底解析失败抛运行时（不吞）
        return new LongValue(Long.parseLong(storeId));
    }

    @Override
    public String getTenantIdColumn() {
        return STORE_ID_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (tableName == null) {
            return true;
        }
        // 铁律 1：仅白名单门店表参与
        if (!isStoreTable(tableName)) {
            return true;
        }
        // 铁律 2：编程式 ignore() 块放行（聚合 / 跨门店视图主动跨店）。
        // 注意：超管 / 租管不再在此自动放行——见铁律 4（option B：显式选店优先于身份）。
        if (StoreContext.isProgrammaticIgnore()) {
            return true;
        }
        // 铁律 3：空上下文不过滤（未选门店 / 非门店请求 / 非 /djs/store/** 域，含仓库跨门店共享表读取）
        String storeId = StoreContext.getStoreId();
        if (StringUtils.isBlank(storeId)) {
            return true;
        }
        // 门店 ID 非法（非数字）→ 放行而非抛异常，避免污染所有 SQL（写操作另由校验拦）
        if (!isLongParsable(storeId)) {
            log.warn("[djs-store] Current-Store-Id 非法（非数字），跳过门店过滤：{}", storeId);
            return true;
        }
        // 铁律 4（option B）：显式选了门店即按该店过滤，超管 / 租管也不例外
        return false;
    }

    /**
     * 表名是否在门店隔离白名单（忽略大小写）。
     */
    private boolean isStoreTable(String tableName) {
        String lower = tableName.toLowerCase();
        for (String include : storeProperties.getIncludes()) {
            if (include != null && lower.equalsIgnoreCase(include.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isLongParsable(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
