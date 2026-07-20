package org.dromara.djs.common.store.context;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * 门店行级隔离的 Web + MyBatis-Plus 集成配置（STORE-PERM-001）。
 *
 * <h3>不动 ruoyi-common（强约束 #1）</h3>
 * <p>{@code MybatisPlusConfig} / {@code SecurityConfig} / {@code ResourcesConfig} 全在 ruoyi-common，
 * 不许改源文件。本类全部走 djs 侧扩展：</p>
 * <ul>
 *   <li><b>HTTP 拦截器</b>：实现 {@link WebMvcConfigurer#addInterceptors}，Spring 自动合并多个
 *       {@code WebMvcConfigurer}，把 {@link StoreContextInterceptor} 加在 SaInterceptor 之后
 *       （preHandle 只读请求头写 SaStorage，不依赖 sa-token 先后）。</li>
 *   <li><b>MP InnerInterceptor</b>：用 {@link BeanPostProcessor} 在容器创建好 ruoyi 的
 *       {@code MybatisPlusInterceptor} bean 后，把门店 {@link TenantLineInnerInterceptor}
 *       （列 {@code store_id}）插入到第一个租户拦截器之后、分页拦截器之前。绝不重定义
 *       {@code MybatisPlusInterceptor} bean，避免与 ruoyi 主链冲突。</li>
 * </ul>
 *
 * @author djs
 * @since STORE-PERM-001
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(StoreProperties.class)
public class DjsStoreWebConfig implements WebMvcConfigurer {

    private final StoreProperties storeProperties;

    /**
     * 门店-人员绑定服务（ObjectProvider 延迟解析，避免本 @Configuration（含 BeanPostProcessor）
     * 创建时强制拉起 MyBatis 早期 bean）。在 {@link #addInterceptors}（web 初始化期）解析，此时 service 已就绪。
     */
    private final ObjectProvider<IStoreUserRelationService> storeUserRelationServiceProvider;

    /**
     * 注册门店上下文拦截器（拦 {@code /**}）。Spring 把本 configurer 与 ruoyi {@code SecurityConfig}
     * 合并，SaInterceptor 先注册先执行；本拦截器 preHandle 读 header → 校验门店权限 → 写 SaStorage。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new StoreContextInterceptor(storeUserRelationServiceProvider.getObject()))
            .addPathPatterns("/**");
    }

    /**
     * 把门店行级 InnerInterceptor 插入现有 {@code MybatisPlusInterceptor}。
     *
     * <p>顺序：第一个 {@link TenantLineInnerInterceptor}（ruoyi 多租户）<b>之后</b>、其余（数据权限 /
     * 分页 / 乐观锁）之前。这样最终 SQL 形如 {@code WHERE tenant_id=? AND store_id=?}。</p>
     */
    @Bean
    public BeanPostProcessor djsStoreLineInterceptorRegistrar() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof MybatisPlusInterceptor mpInterceptor)) {
                    return bean;
                }
                List<InnerInterceptor> current = mpInterceptor.getInterceptors();
                List<InnerInterceptor> rebuilt = new ArrayList<>(current.size() + 1);
                TenantLineInnerInterceptor storeInterceptor =
                    new TenantLineInnerInterceptor(new StoreLineHandler(storeProperties));
                boolean inserted = false;
                for (InnerInterceptor inner : current) {
                    rebuilt.add(inner);
                    // 在第一个租户拦截器之后立即插入门店拦截器
                    if (!inserted && inner instanceof TenantLineInnerInterceptor) {
                        rebuilt.add(storeInterceptor);
                        inserted = true;
                    }
                }
                if (!inserted) {
                    // 未发现租户拦截器（极端配置）→ 放到链首，仍在分页之前
                    rebuilt.add(0, storeInterceptor);
                }
                mpInterceptor.setInterceptors(rebuilt);
                log.info("[djs-store] 门店行级隔离拦截器已注册（store_id），白名单 {} 张表：{}",
                    storeProperties.getIncludes().size(), storeProperties.getIncludes());
                return bean;
            }
        };
    }
}
