package org.dromara.djs.warehouse.tare.domain;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D-0147 ③ 秤读数契约：都不传 = NULL；都传须 称重 − 去皮 = 实重（≤ 0.001kg）。 */
@Tag("local")
@Tag("dev")
class ScaleReadingTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("都不传 → null（旧客户端，落库 NULL）")
    void absentReadingIsNull() {
        assertThat(ScaleReading.of(null, null, d("5"))).isNull();
        assertThat(ScaleReading.of(null, null, null)).isNull();
    }

    @Test
    @DisplayName("一致 → 规范为三位小数；不去皮传 0；误差 0.001 内放行")
    void consistentReadingIsNormalized() {
        ScaleReading r = ScaleReading.of(d("5.6"), d("0.6"), d("5.000"));
        assertThat(r.grossWeight()).isEqualTo(d("5.600"));
        assertThat(r.tareWeight()).isEqualTo(d("0.600"));
        assertThat(ScaleReading.of(d("5"), d("0"), d("5")).tareWeight()).isEqualTo(d("0.000"));
        assertThat(ScaleReading.of(d("5.601"), d("0.600"), d("5.000"))).isNotNull();
        assertThat(ScaleReading.of(d("5.6"), d("0.6"), d("5")).fingerprint()).isEqualTo("5.6/0.6");
    }

    @Test
    @DisplayName("不一致 / 只传一个 / 负数 / 超三位小数 / 有读数无实重 → 业务异常")
    void invalidReadingsAreRejected() {
        assertThatThrownBy(() -> ScaleReading.of(d("5.602"), d("0.600"), d("5"))).isInstanceOf(ServiceException.class)
            .hasMessageContaining("不一致");
        assertThatThrownBy(() -> ScaleReading.of(d("5"), null, d("5"))).hasMessageContaining("同时提交");
        assertThatThrownBy(() -> ScaleReading.of(null, d("0"), d("5"))).hasMessageContaining("同时提交");
        assertThatThrownBy(() -> ScaleReading.of(d("-1"), d("0"), d("5"))).hasMessageContaining("负数");
        assertThatThrownBy(() -> ScaleReading.of(d("5.0001"), d("0"), d("5"))).hasMessageContaining("三位小数");
        assertThatThrownBy(() -> ScaleReading.of(d("5"), d("0"), null)).hasMessageContaining("实重");
    }
}
