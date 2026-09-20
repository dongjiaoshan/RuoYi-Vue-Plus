package org.dromara.djs.plant.market.util;

import org.dromara.djs.plant.common.util.DateWindowStatusCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MarketStatusLabel} 导出中文名单测（这些字面值会直接写进甲方拿到的 Excel）。
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@DisplayName("MarketStatusLabel 单元测试")
class MarketStatusLabelTest {

    @Test
    @DisplayName("状态码 → 上市域中文名")
    void names() {
        assertThat(MarketStatusLabel.name(DateWindowStatusCalculator.PENDING)).isEqualTo("待上市");
        assertThat(MarketStatusLabel.name(DateWindowStatusCalculator.UPCOMING)).isEqualTo("即将上市");
        assertThat(MarketStatusLabel.name(DateWindowStatusCalculator.ON_SALE)).isEqualTo("上市中");
        assertThat(MarketStatusLabel.name(DateWindowStatusCalculator.ENDING)).isEqualTo("即将下市");
        assertThat(MarketStatusLabel.name(DateWindowStatusCalculator.OFF_SHELF)).isEqualTo("已下架");
    }

    @Test
    @DisplayName("空码 / 未知码 → null（列表与导出显 -，不猜）")
    void unknownGivesNull() {
        assertThat(MarketStatusLabel.name(null)).isNull();
        assertThat(MarketStatusLabel.name("unknown")).isNull();
    }
}
