package org.dromara.djs.common.validate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link BizReferenceCheckerImpl} 单元测试（BRD-EVENT-001）。
 *
 * @author djs
 * @since BRD-EVENT-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BizReferenceCheckerImpl 单元测试 (BRD-EVENT-001)")
class BizReferenceCheckerImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private BizReferenceCheckerImpl checker;

    @BeforeEach
    void setup() {
        checker = new BizReferenceCheckerImpl(jdbcTemplate);
    }

    @Test
    @DisplayName("register + isReferenced: 命中 → true")
    void registerAndHit() {
        checker.register("t_md_supplier", "t_farm_pig_introduce", "supplier_id");
        when(jdbcTemplate.queryForList(
            contains("FROM t_farm_pig_introduce"), eq(Integer.class), eq(123L)))
            .thenReturn(List.of(1));

        assertThat(checker.isReferenced("t_md_supplier", 123L)).isTrue();
    }

    @Test
    @DisplayName("register + isReferenced: 未命中 → false")
    void notHit() {
        checker.register("t_md_supplier", "t_farm_pig_introduce", "supplier_id");
        when(jdbcTemplate.queryForList(any(String.class), eq(Integer.class), any()))
            .thenReturn(Collections.emptyList());

        assertThat(checker.isReferenced("t_md_supplier", 123L)).isFalse();
    }

    @Test
    @DisplayName("isReferenced: id=null → false（短路）")
    void nullIdShortCircuit() {
        checker.register("t_md_supplier", "t_farm_pig_introduce", "supplier_id");
        assertThat(checker.isReferenced("t_md_supplier", null)).isFalse();
    }

    @Test
    @DisplayName("isReferenced: 未注册的 sourceTable → false")
    void unregisteredSource() {
        assertThat(checker.isReferenced("t_md_unknown", 1L)).isFalse();
    }

    @Test
    @DisplayName("register: 同一 (source, ref, col) 重复注册幂等")
    void registerIdempotent() {
        checker.register("t_md_supplier", "t_farm_pig_introduce", "supplier_id");
        checker.register("t_md_supplier", "t_farm_pig_introduce", "supplier_id");
        // dumpRegistered 只会有 1 条
        assertThat(checker.dumpRegistered())
            .hasSize(1)
            .contains("t_md_supplier <- t_farm_pig_introduce.supplier_id");
    }

    @Test
    @DisplayName("register: 多张引用表都会被检查（短路：第一张命中即返）")
    void registerMultipleRefs() {
        checker.register("t_md_supplier", "t_farm_pig_introduce", "supplier_id");
        checker.register("t_md_supplier", "t_farm_pig_info", "supplier_id");
        when(jdbcTemplate.queryForList(
            contains("FROM t_farm_pig_introduce"), eq(Integer.class), eq(123L)))
            .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForList(
            contains("FROM t_farm_pig_info"), eq(Integer.class), eq(123L)))
            .thenReturn(List.of(1));

        assertThat(checker.isReferenced("t_md_supplier", 123L)).isTrue();
    }

    @Test
    @DisplayName("register: 非法标识符（含空格 / SQL 注入字符）→ IllegalArgumentException")
    void registerInvalidIdentifier() {
        assertThatThrownBy(() ->
            checker.register("t_md_supplier; DROP TABLE", "t_farm_pig", "supplier_id"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> checker.register("t_md_supplier", "", "col"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> checker.register("t_md_supplier", "t_a", "col with space"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
