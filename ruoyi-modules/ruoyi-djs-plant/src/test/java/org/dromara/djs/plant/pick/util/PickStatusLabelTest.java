package org.dromara.djs.plant.pick.util;

import org.dromara.djs.plant.common.util.DateWindowStatusCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PickStatusLabel} 导出中文名单测（这些字面值会直接写进甲方拿到的 Excel）。
 *
 * <p>与上市域共用五档码、中文不同：同一个 {@code on_sale} 在这里是「采摘中」、
 * 在上市计划里是「上市中」。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@DisplayName("PickStatusLabel 单元测试")
class PickStatusLabelTest {

    @Test
    @DisplayName("状态码 → 采摘域中文名")
    void names() {
        assertThat(PickStatusLabel.name(DateWindowStatusCalculator.PENDING)).isEqualTo("待采摘");
        assertThat(PickStatusLabel.name(DateWindowStatusCalculator.UPCOMING)).isEqualTo("即将采摘");
        assertThat(PickStatusLabel.name(DateWindowStatusCalculator.ON_SALE)).isEqualTo("采摘中");
        assertThat(PickStatusLabel.name(DateWindowStatusCalculator.ENDING)).isEqualTo("即将结束采摘");
        assertThat(PickStatusLabel.name(DateWindowStatusCalculator.OFF_SHELF)).isEqualTo("完成采摘");
    }

    @Test
    @DisplayName("空码 / 未知码 → null（列表与导出显 -，不猜）")
    void unknownGivesNull() {
        assertThat(PickStatusLabel.name(null)).isNull();
        assertThat(PickStatusLabel.name("unknown")).isNull();
    }
}
