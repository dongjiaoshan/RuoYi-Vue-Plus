package org.dromara.djs.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 跨农场放行标记。
 * <p>
 * 用法：在 Mapper 接口方法 / Service 方法 / Controller 方法上标注，
 * 表示该查询/更新需要跨农场执行（典型场景：统一报表 / 集团驾驶舱）。
 * </p>
 *
 * <h3>V1 行为</h3>
 * djs.multi-farm.enabled=false，{@link org.dromara.djs.common.tenant.FarmInterceptorAspect}
 * 不注入到 Spring，因此本注解仅为代码层 marker，不产生副作用。
 *
 * <h3>V2 行为</h3>
 * djs.multi-farm.enabled=true 时，{@link org.dromara.djs.common.tenant.FarmInterceptorAspect}
 * AOP 切面拦截被本注解标记的方法，执行期间通过
 * {@link com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper#handle} 或
 * ruoyi 自带 {@code TenantHelper.ignore(...)} 跳过 tenant_id 过滤，方法返回后自动恢复。
 *
 * <p>注：当前 V1 disabled 状态下，本注解仅作为 mark 占位（编译期被识别但运行时无 AOP 拦截）；
 * V2 启用 multi-farm 时需补 AOP 切面实现，参考 doc/05 §4.3.1 §6.4。</p>
 *
 * @author djs
 * @since SYS-INFRA-007
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreFarm {

    /**
     * 跳过原因（可选，仅作文档用途）。
     */
    String reason() default "";
}
