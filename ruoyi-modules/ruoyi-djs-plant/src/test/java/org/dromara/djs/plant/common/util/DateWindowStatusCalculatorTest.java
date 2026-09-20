package org.dromara.djs.plant.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DateWindowStatusCalculator} 五档状态与边界单测。
 *
 * <p>果蔬上市计划与采摘计划共用这一份判定，两页的状态列 / 筛选 / 统计全都压在这些用例上。
 * 全部用固定的 {@code TODAY} 判定，不受运行日影响。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@DisplayName("DateWindowStatusCalculator 单元测试")
class DateWindowStatusCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    @Test
    @DisplayName("开始日期在 30 天以后 → 第 1 档（待上市 / 待采摘）")
    void pending() {
        assertThat(DateWindowStatusCalculator.resolve(TODAY.plusDays(31), TODAY.plusDays(200), TODAY))
            .isEqualTo(DateWindowStatusCalculator.PENDING);
    }

    @Test
    @DisplayName("边界：离开始正好 30 天 → 第 2 档（>30 才算第 1 档）")
    void upcomingAtThirtyDays() {
        assertThat(DateWindowStatusCalculator.resolve(TODAY.plusDays(30), TODAY.plusDays(200), TODAY))
            .isEqualTo(DateWindowStatusCalculator.UPCOMING);
    }

    @Test
    @DisplayName("边界：当天正好是开始日期 → 第 2 档（第 3/4 条要求当前日期严格大于开始日期）")
    void upcomingOnBeginDate() {
        assertThat(DateWindowStatusCalculator.resolve(TODAY, TODAY.plusDays(100), TODAY))
            .isEqualTo(DateWindowStatusCalculator.UPCOMING);
    }

    @Test
    @DisplayName("已过开始日期且离结束超过 15 天 → 第 3 档（上市中 / 采摘中）")
    void onSale() {
        assertThat(DateWindowStatusCalculator.resolve(TODAY.minusDays(1), TODAY.plusDays(16), TODAY))
            .isEqualTo(DateWindowStatusCalculator.ON_SALE);
    }

    @Test
    @DisplayName("边界：已过开始日期且离结束正好 15 天 → 第 4 档")
    void endingAtFifteenDays() {
        assertThat(DateWindowStatusCalculator.resolve(TODAY.minusDays(1), TODAY.plusDays(15), TODAY))
            .isEqualTo(DateWindowStatusCalculator.ENDING);
    }

    @Test
    @DisplayName("边界：当天正好是结束日期 → 第 4 档（第 5 条要求当前日期严格大于结束日期）")
    void endingOnEndDate() {
        assertThat(DateWindowStatusCalculator.resolve(TODAY.minusDays(30), TODAY, TODAY))
            .isEqualTo(DateWindowStatusCalculator.ENDING);
    }

    @Test
    @DisplayName("已过结束日期 → 第 5 档；且优先于「离结束 ≤15 天」的字面判定")
    void offShelf() {
        assertThat(DateWindowStatusCalculator.resolve(TODAY.minusDays(90), TODAY.minusDays(1), TODAY))
            .isEqualTo(DateWindowStatusCalculator.OFF_SHELF);
    }

    @Test
    @DisplayName("开始日期为空 → 状态为空，不猜")
    void nullBeginGivesNullStatus() {
        assertThat(DateWindowStatusCalculator.resolve(null, TODAY.plusDays(10), TODAY)).isNull();
    }

    @Test
    @DisplayName("只有开始日期没有结束日期：开始前照常判两档，开始后一律第 3 档")
    void nullEndDate() {
        assertThat(DateWindowStatusCalculator.resolve(TODAY.plusDays(60), null, TODAY))
            .isEqualTo(DateWindowStatusCalculator.PENDING);
        assertThat(DateWindowStatusCalculator.resolve(TODAY.minusDays(1), null, TODAY))
            .isEqualTo(DateWindowStatusCalculator.ON_SALE);
    }

    @Test
    @DisplayName("CODES 覆盖全部五档且不重复 —— 统计版块靠它遍历，漏一档就少一个格子")
    void codesCoverAllFiveBuckets() {
        assertThat(DateWindowStatusCalculator.CODES)
            .containsExactly(
                DateWindowStatusCalculator.PENDING,
                DateWindowStatusCalculator.UPCOMING,
                DateWindowStatusCalculator.ON_SALE,
                DateWindowStatusCalculator.ENDING,
                DateWindowStatusCalculator.OFF_SHELF)
            .doesNotHaveDuplicates();
    }
}
