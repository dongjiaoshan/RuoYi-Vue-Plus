package org.dromara.djs.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 身份证号 ISO 7064 校验码校验注解。
 *
 * <p>校验逻辑委托给 {@link IdCardValidator}，底层使用 hutool {@code IdcardUtil.isValidCard}
 * 支持 18 位居民身份证（含末位 X）及 15 位旧式身份证，同时对 GB 11643-1999 校验码做严格验证。
 * 空值（null / blank）视为合法，可结合 {@code @NotBlank} 约束必填场景。</p>
 *
 * @author djs
 * @since SYS-FIX-002
 */
@Documented
@Constraint(validatedBy = IdCardValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface ValidIdCard {

    String message() default "身份证号格式不正确";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
