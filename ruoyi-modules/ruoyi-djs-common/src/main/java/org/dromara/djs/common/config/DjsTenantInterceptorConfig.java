package org.dromara.djs.common.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.constant.DjsAuthConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Set;

/**
 * 东角山多农场数据范围拦截器配置。
 *
 * <h3>V1（默认 disabled）</h3>
 * <ul>
 *   <li>{@code djs.multi-farm.enabled=false}（{@link ConditionalOnProperty matchIfMissing=false}）→
 *       本类 Bean 全部不注入到 Spring；</li>
 *   <li>多租户行为完全由 ruoyi 自带 {@code ruoyi-common-tenant}（{@code tenant.enable=true}）接管：
 *       <pre>
 *       PlusTenantLineHandler.getTenantId()
 *         → TenantHelper.getTenantId()
 *         → LoginHelper.getTenantId()  // sys_user.tenant_id（admin 用户 = '1001'）
 *       </pre>
 *       所有 djs 业务表 INSERT 自动写 tenant_id='1001'，SELECT 自动加 WHERE tenant_id='1001'；</li>
 *   <li>由于 V1 全部数据 tenant_id='1001'，与"无多农场"行为等价；</li>
 *   <li>V1 启用 ruoyi 默认拦截器是无副作用的（参 doc/05 §4.3.1 关键设计）。</li>
 * </ul>
 *
 * <h3>V2（enabled=true）启用步骤</h3>
 * <ol>
 *   <li>application.yml 改 {@code djs.multi-farm.enabled: true}（一行）；</li>
 *   <li>本配置类注入 {@link #djsTenantLineInnerInterceptor()} Bean，覆盖
 *       ruoyi-common-tenant 的同类型 Bean（通过 {@link Primary}），
 *       从而把"tenant_id 来源"从"sys_user.tenant_id（登录态固定值）"切到
 *       "当前请求 farm_id（运行时 ThreadLocal）"；</li>
 *   <li>{@link DjsTenantLineHandler#getTenantId()} 通过
 *       {@link TenantHelper#getDynamic()} 取当前 ThreadLocal 农场（由
 *       SYS-AUTH-001 的 {@code UserFarmController.switchFarm} 写入），
 *       未设置时回退到 {@code LoginHelper.getTenantId()}（用户默认农场）；</li>
 *   <li>实施 V2 时再补 admin / 小程序顶部 FarmSwitcher 组件 + 多农场用户分配
 *       （详见 doc/_adr/0001-multi-farm-deferred.md §2）。</li>
 * </ol>
 *
 * <h3>设计取舍</h3>
 * <p>之所以复用 {@link TenantHelper}（而不是新建独立 {@code FarmContext} ThreadLocal）：
 * ruoyi 已有完整的 TransmittableThreadLocal + Sa-Token storage + Redis 持久化三级
 * 切农场基础设施，重复造一份会和 ruoyi 的 {@code TenantSpringCacheManager} /
 * {@code TenantSaTokenDao} / {@code TenantKeyPrefixHandler} 全套缓存 key 前缀机制脱节。
 * 复用即"djs 把多农场语义映射到 ruoyi 多租户语义"。</p>
 *
 * <h3>全局共享白名单</h3>
 * <p>{@link #IGNORE_TABLES} 列出 V2 启用后<b>不</b>走 tenant_id 过滤的表（sys_* / sys_farm
 * 主数据 / mp_subscribe_record 等）。该白名单<b>独立于</b> ruoyi-common-tenant 的
 * {@code tenant.excludes}（yml 配的那份），因为本拦截器只看自己的白名单。
 *
 * @author djs
 * @since SYS-INFRA-007
 * @see org.dromara.common.tenant.handle.PlusTenantLineHandler
 * @see org.dromara.djs.common.handler.DjsMetaObjectHandler
 */
@Configuration
@ConditionalOnProperty(prefix = "djs.multi-farm", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DjsTenantInterceptorConfig {

    private static final Logger log = LoggerFactory.getLogger(DjsTenantInterceptorConfig.class);

    /**
     * 全局共享表白名单：V2 启用后这些表的 SQL 不被注入 {@code WHERE tenant_id=?}。
     * <p>核心原则：ruoyi 自带表 + djs 主数据 + 全局推送记录 全部跨农场共享。</p>
     */
    static final Set<String> IGNORE_TABLES = Set.of(
        // ruoyi 自带共享表
        "sys_user", "sys_role", "sys_dept", "sys_post", "sys_menu",
        "sys_dict_type", "sys_dict_data", "sys_config", "sys_client",
        "sys_oss", "sys_oss_config", "sys_log_login", "sys_log_operlog",
        "sys_user_role", "sys_role_menu", "sys_role_dept", "sys_user_post",
        "sys_notice", "sys_social", "sys_tenant", "sys_tenant_package",
        "gen_table", "gen_table_column", "flow_spel",
        // djs 全局共享表
        "sys_farm",                  // 农场主数据表本身（id=1001 单条 V1）
        "mp_subscribe_record"        // 微信订阅消息记录（按 openid 维度，不分农场）
    );

    /**
     * djs 多农场版 TenantLineInnerInterceptor。
     *
     * <p>{@link Primary} 让 ruoyi {@code MybatisPlusConfig#mybatisPlusInterceptor()}
     * 通过 {@code SpringUtils.getBean(TenantLineInnerInterceptor.class)} 优先拿到本 Bean，
     * 从而覆盖 ruoyi-common-tenant 的同类型 Bean。</p>
     *
     * @return 配置好白名单 + DjsTenantLineHandler 的拦截器
     */
    @Bean
    @Primary
    public TenantLineInnerInterceptor djsTenantLineInnerInterceptor() {
        log.info("[djs-tenant] 多农场拦截器启用（djs.multi-farm.enabled=true），白名单 {} 张表", IGNORE_TABLES.size());
        return new TenantLineInnerInterceptor(new DjsTenantLineHandler());
    }

    /**
     * djs 多农场 TenantLineHandler 实现：tenant_id 来源 =
     * {@link TenantHelper#getDynamic()}（运行时切农场结果，ThreadLocal）
     * → fallback 到 {@code LoginHelper.getTenantId()}（用户默认农场，登录时写入 Sa-Token）。
     */
    static class DjsTenantLineHandler implements TenantLineHandler {

        @Override
        public Expression getTenantId() {
            String tenantId = TenantHelper.getTenantId();
            if (tenantId == null || tenantId.isBlank()) {
                // 无登录态时（如系统启动期的健康检查），fallback 到 V1 默认农场
                log.debug("[djs-tenant] tenant_id 为空，fallback 到 V1 默认农场 {}", DjsAuthConstants.DEFAULT_FARM_ID);
                return new StringValue(DjsAuthConstants.DEFAULT_FARM_ID);
            }
            return new StringValue(tenantId);
        }

        @Override
        public String getTenantIdColumn() {
            return "tenant_id";
        }

        @Override
        public boolean ignoreTable(String tableName) {
            if (tableName == null) {
                return true;
            }
            String lower = tableName.toLowerCase();
            // 任意命中白名单 → 不拦截
            return IGNORE_TABLES.contains(lower);
        }

        @SuppressWarnings("unused")
        private Expression nullExpression() {
            return new NullValue();
        }
    }
}
