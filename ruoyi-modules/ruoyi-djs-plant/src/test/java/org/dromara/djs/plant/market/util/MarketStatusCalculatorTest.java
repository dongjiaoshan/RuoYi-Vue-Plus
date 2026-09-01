package org.dromara.djs.plant.market.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MarketStatusCalculator} 五档状态与边界单测（V6-R158）。
 *
 * <p>全部用固定的 {@code TODAY} 判定，不受运行日影响。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@DisplayName("MarketStatusCalculator 单元测试")
class MarketStatusCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    @Test
    @DisplayName("上市日期在 30 天以后 → 待上市")
    void pending() {
        assertThat(MarketStatusCalculator.resolve(TODAY.plusDays(31), TODAY.plusDays(200), TODAY))
            .isEqualTo(MarketStatusCalculator.PENDING);
    }

    @Test
    @DisplayName("边界：离上市正好 30 天 → 即将上市（>30 才算待上市）")
    void upcomingAtThirtyDays() {
        assertThat(MarketStatusCalculator.resolve(TODAY.plusDays(30), TODAY.plusDays(200), TODAY))
            .isEqualTo(MarketStatusCalculator.UPCOMING);
    }

    @Test
    @DisplayName("边界：当天正好是上市日期 → 即将上市（第 3/4 条要求当前日期严格大于上市日期）")
    void upcomingOnBeginDate() {
        assertThat(MarketStatusCalculator.resolve(TODAY, TODAY.plusDays(100), TODAY))
            .isEqualTo(MarketStatusCalculator.UPCOMING);
    }

    @Test
    @DisplayName("已过上市日期且离下架超过 15 天 → 上市中")
    void onSale() {
        assertThat(MarketStatusCalculator.resolve(TODAY.minusDays(1), TODAY.plusDays(16), TODAY))
            .isEqualTo(MarketStatusCalculator.ON_SALE);
    }

    @Test
    @DisplayName("边界：已过上市日期且离下架正好 15 天 → 即将下市")
    void endingAtFifteenDays() {
        assertThat(MarketStatusCalculator.resolve(TODAY.minusDays(1), TODAY.plusDays(15), TODAY))
            .isEqualTo(MarketStatusCalculator.ENDING);
    }

    @Test
    @DisplayName("边界：当天正好是下架日期 → 即将下市（第 5 条要求当前日期严格大于下架日期）")
    void endingOnEndDate() {
        assertThat(MarketStatusCalculator.resolve(TODAY.minusDays(30), TODAY, TODAY))
            .isEqualTo(MarketStatusCalculator.ENDING);
    }

    @Test
    @DisplayName("已过下架日期 → 已下架；且优先于「离下架 ≤15 天」的字面判定")
    void offShelf() {
        assertThat(MarketStatusCalculator.resolve(TODAY.minusDays(90), TODAY.minusDays(1), TODAY))
            .isEqualTo(MarketStatusCalculator.OFF_SHELF);
    }

    @Test
    @DisplayName("上市日期为空 → 状态为空，不猜")
    void nullBeginGivesNullStatus() {
        assertThat(MarketStatusCalculator.resolve(null, TODAY.plusDays(10), TODAY)).isNull();
        assertThat(MarketStatusCalculator.name(null)).isNull();
    }

    @Test
    @DisplayName("只有上市日期没有下架日期：上市前照常判两档，上市后一律上市中")
    void nullEndDate() {
        assertThat(MarketStatusCalculator.resolve(TODAY.plusDays(60), null, TODAY))
            .isEqualTo(MarketStatusCalculator.PENDING);
        assertThat(MarketStatusCalculator.resolve(TODAY.minusDays(1), null, TODAY))
            .isEqualTo(MarketStatusCalculator.ON_SALE);
    }

    @Test
    @DisplayName("状态码 → 导出用中文名")
    void names() {
        assertThat(MarketStatusCalculator.name(MarketStatusCalculator.PENDING)).isEqualTo("待上市");
        assertThat(MarketStatusCalculator.name(MarketStatusCalculator.UPCOMING)).isEqualTo("即将上市");
        assertThat(MarketStatusCalculator.name(MarketStatusCalculator.ON_SALE)).isEqualTo("上市中");
        assertThat(MarketStatusCalculator.name(MarketStatusCalculator.ENDING)).isEqualTo("即将下市");
        assertThat(MarketStatusCalculator.name(MarketStatusCalculator.OFF_SHELF)).isEqualTo("已下架");
        assertThat(MarketStatusCalculator.name("unknown")).isNull();
    }
}
