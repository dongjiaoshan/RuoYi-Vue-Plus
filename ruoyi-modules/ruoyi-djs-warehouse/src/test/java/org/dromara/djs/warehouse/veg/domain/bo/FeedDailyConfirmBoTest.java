package org.dromara.djs.warehouse.veg.domain.bo;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FeedDailyConfirmBo} 校验注解单测（V6 row98：框数放开到 1 位小数）。
 *
 * <p>只跑 Bean Validation 本身，不起 Spring 上下文 —— 这里要钉死的就是注解本身的口径：
 * 1 位小数必须能过、2 位小数必须被挡在 400（不能漏到 DB 层被 DECIMAL(10,2) 悄悄四舍五入，
 * 那样 mp 上填 1.55 会静默存成 1.55 而卡片按 1 位显示成 1.6，账面对不上）。</p>
 *
 * @author djs
 * @since V6-R98
 */
@Tag("local")
@Tag("dev")
@DisplayName("FeedDailyConfirmBo 框数校验（row98：最多 1 位小数）")
class FeedDailyConfirmBoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private FeedDailyConfirmBo bo(String boxCount) {
        FeedDailyConfirmBo bo = new FeedDailyConfirmBo();
        bo.setFeedDate(LocalDate.of(2026, 8, 13));
        bo.setConfirmUserId(1L);
        bo.setBoxCount(boxCount == null ? null : new BigDecimal(boxCount));
        return bo;
    }

    private Set<ConstraintViolation<FeedDailyConfirmBo>> violations(String boxCount) {
        return validator.validate(bo(boxCount));
    }

    @Test
    @DisplayName("整数照常通过（存量口径不能被这次放开打破）")
    void testIntegerStillPasses() {
        assertThat(violations("5")).isEmpty();
    }

    @Test
    @DisplayName("1 位小数通过 —— row98 要的就是这个")
    void testOneFractionDigitPasses() {
        assertThat(violations("1.5")).isEmpty();
        assertThat(violations("0.5")).isEmpty();
    }

    @Test
    @DisplayName("2 位小数被挡（DECIMAL(10,2) 存得下，正因此必须在校验层挡，否则静默入库）")
    void testTwoFractionDigitsRejected() {
        assertThat(violations("1.55"))
            .extracting(ConstraintViolation::getPropertyPath)
            .extracting(Object::toString)
            .containsExactly("boxCount");
    }

    @Test
    @DisplayName("0 与负数被挡（框数必须大于 0）")
    void testNonPositiveRejected() {
        assertThat(violations("0")).isNotEmpty();
        assertThat(violations("-1.5")).isNotEmpty();
    }

    @Test
    @DisplayName("整数位超 8 位被挡（不留给 JDBC 溢出成 500）")
    void testTooManyIntegerDigitsRejected() {
        assertThat(violations("999999999.5")).isNotEmpty();
        assertThat(violations("99999999.5")).isEmpty();
    }

    @Test
    @DisplayName("框数为空被挡")
    void testNullRejected() {
        assertThat(violations(null)).isNotEmpty();
    }
}
