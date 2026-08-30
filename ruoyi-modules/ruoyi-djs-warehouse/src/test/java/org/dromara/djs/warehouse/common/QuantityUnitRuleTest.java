package org.dromara.djs.warehouse.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数量单位口径（V6 row141 / row143）。
 *
 * <p>这份名单同时被采购入库（{@code ProductInfoServiceImpl#assertQuantityScale}）、产品出库与
 * 猪肉库位转移（{@code LocationStockServiceImpl#assertManualOutQuantity} /
 * {@code #assertManualTransferQuantity}）三处使用，且与前端
 * {@code plus-ui/src/utils/weight.ts#isCountingUnit} 必须逐字一致 ——
 * 改名单会同时改这几处的拦截行为，所以在这里钉住。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@DisplayName("计数类单位口径：只有它们强制整数（V6 row141/row143）")
class QuantityUnitRuleTest {

    @Test
    @DisplayName("计数类单位命中：瓶 / 袋 / 盒 / 个 / 桶 / 枚 / 头 …")
    void countingUnitsHit() {
        for (String u : new String[]{"瓶", "袋", "盒", "个", "桶", "罐", "枚", "件", "条", "头", "份", "提"}) {
            assertThat(QuantityUnitRule.isCountingUnit(u)).as(u).isTrue();
        }
    }

    @Test
    @DisplayName("计量类单位不锁整数 —— 2.5 吨 / 0.5 斤 是合法业务值，锁了就录不进来")
    void measurementUnitsNotLocked() {
        for (String u : new String[]{"kg", "公斤", "吨", "升", "斤", "米", "平方米", "亩"}) {
            assertThat(QuantityUnitRule.isCountingUnit(u)).as(u).isFalse();
        }
    }

    @Test
    @DisplayName("空 / 未知单位按「可小数」放过（fail-open：拦错了挡住干活，放过只是多个小数位）")
    void unknownUnitFailsOpen() {
        assertThat(QuantityUnitRule.isCountingUnit(null)).isFalse();
        assertThat(QuantityUnitRule.isCountingUnit("")).isFalse();
        assertThat(QuantityUnitRule.isCountingUnit("  ")).isFalse();
        assertThat(QuantityUnitRule.isCountingUnit("盒 / 片")).isFalse();   // 库里真实存在的复合单位
    }

    @Test
    @DisplayName("单位两侧空格不影响判定（甲方 Excel 粘过来常带空格）")
    void trimsUnit() {
        assertThat(QuantityUnitRule.isCountingUnit(" 瓶 ")).isTrue();
    }

    @Test
    @DisplayName("计数类带小数才算违规；3.000 这种尾零归一后算整数")
    void nonIntegerDetection() {
        assertThat(QuantityUnitRule.isNonIntegerForCountingUnit(new BigDecimal("1.6"), "瓶")).isTrue();
        assertThat(QuantityUnitRule.isNonIntegerForCountingUnit(new BigDecimal("0.5"), "瓶")).isTrue();
        assertThat(QuantityUnitRule.isNonIntegerForCountingUnit(new BigDecimal("3"), "瓶")).isFalse();
        assertThat(QuantityUnitRule.isNonIntegerForCountingUnit(new BigDecimal("3.000"), "瓶")).isFalse();
        assertThat(QuantityUnitRule.isNonIntegerForCountingUnit(new BigDecimal("1e2"), "瓶")).isFalse();
    }

    @Test
    @DisplayName("计量类单位 / 空值一律不判违规")
    void neverBlocksMeasurementOrNull() {
        assertThat(QuantityUnitRule.isNonIntegerForCountingUnit(new BigDecimal("2.5"), "吨")).isFalse();
        assertThat(QuantityUnitRule.isNonIntegerForCountingUnit(new BigDecimal("65.880"), "kg")).isFalse();
        assertThat(QuantityUnitRule.isNonIntegerForCountingUnit(null, "瓶")).isFalse();
        assertThat(QuantityUnitRule.isNonIntegerForCountingUnit(new BigDecimal("1.6"), null)).isFalse();
    }

    @Test
    @DisplayName("小数位上限与库表 DECIMAL(12,3) 对齐")
    void maxScaleMatchesDdl() {
        assertThat(QuantityUnitRule.MAX_SCALE).isEqualTo(3);
    }
}
