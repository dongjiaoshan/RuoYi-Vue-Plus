package org.dromara.djs.common.validation;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdcardUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link ValidIdCard} 校验逻辑实现。
 *
 * <p>委托 hutool {@link IdcardUtil#isValidCard(String)} 执行 ISO 7064 校验码（Mod 11-2）验证，
 * 覆盖 18 位（含末位 X）及 15 位旧式身份证。空白值直接通过，配合 {@code @NotBlank} 处理必填约束。</p>
 *
 * @author djs
 * @since SYS-FIX-002
 */
public class IdCardValidator implements ConstraintValidator<ValidIdCard, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return CharSequenceUtil.isBlank(value) || IdcardUtil.isValidCard(value);
    }
}
