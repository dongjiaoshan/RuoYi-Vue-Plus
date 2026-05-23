package org.dromara.djs.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IdCardValidator} 单元测试（SYS-FIX-002）。
 *
 * <p>不启 Spring；直接用 Jakarta Validation API 构造 {@link Validator} 实例。</p>
 *
 * @author djs
 * @since SYS-FIX-002
 */
@Tag("local")
@Tag("dev")
@DisplayName("IdCardValidator 单元测试")
class IdCardValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Data
    static class Stub {
        @ValidIdCard
        private String idCard;
    }

    private Set<ConstraintViolation<Stub>> validate(String value) {
        Stub stub = new Stub();
        stub.setIdCard(value);
        return validator.validate(stub);
    }

    @Test
    @DisplayName("合法 18 位身份证（ISO 7064 校验码正确）→ 通过")
    void validIdCard18_passes() {
        // 110101198901150211 — 北京，已通过 hutool isValidCard 验证
        assertThat(validate("110101198901150211")).isEmpty();
    }

    @Test
    @DisplayName("合法 15 位旧式身份证 → 通过")
    void validIdCard15_passes() {
        // 130503670401001 — 已通过 hutool isValidCard 验证
        assertThat(validate("130503670401001")).isEmpty();
    }

    @Test
    @DisplayName("18 位号码校验位错误 → 拦截")
    void invalidCheckDigit_fails() {
        // 110101198901150211（合法）最后一位改为 2 → 校验位不匹配
        assertThat(validate("110101198901150212")).isNotEmpty();
    }

    @Test
    @DisplayName("地区码不合法（00 不存在）→ 拦截")
    void invalidRegionCode_fails() {
        assertThat(validate("000101198901150211")).isNotEmpty();
    }

    @Test
    @DisplayName("null → 通过（空值视为合法，配合 @NotBlank 处理必填）")
    void nullIdCard_passes() {
        assertThat(validate(null)).isEmpty();
    }

    @Test
    @DisplayName("空字符串 → 通过")
    void blankIdCard_passes() {
        assertThat(validate("")).isEmpty();
    }

    @Test
    @DisplayName("纯空格 → 通过")
    void whitespaceIdCard_passes() {
        assertThat(validate("   ")).isEmpty();
    }

    @Test
    @DisplayName("含非数字字符 → 拦截")
    void invalidChars_fails() {
        assertThat(validate("31010119900101ABCD")).isNotEmpty();
    }
}
