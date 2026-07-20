package org.dromara.djs.common.store.context;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 门店行级隔离配置（STORE-PERM-001）。
 *
 * <p>{@code store.includes} 是<b>白名单</b>：仅清单中的表会被 {@link StoreLineHandler} 注入
 * {@code WHERE store_id=?}。其余所有表（ruoyi 自带 / breed / plant / 非门店 warehouse / sys_*）
 * 一律不碰。白名单语义与 ruoyi {@code tenant.excludes}（黑名单）相反 —— 门店隔离是<b>opt-in</b>，
 * 默认不过滤，防止误伤全 app SQL。</p>
 *
 * <p>配置于 {@code application.yml} 的 {@code store.includes:}。</p>
 *
 * @author djs
 * @since STORE-PERM-001
 */
@Data
@ConfigurationProperties(prefix = "store")
public class StoreProperties {

    /**
     * 需要门店行级隔离的表白名单（小写表名）。不在清单中的表不加 {@code store_id} 过滤。
     */
    private List<String> includes = new ArrayList<>();
}
