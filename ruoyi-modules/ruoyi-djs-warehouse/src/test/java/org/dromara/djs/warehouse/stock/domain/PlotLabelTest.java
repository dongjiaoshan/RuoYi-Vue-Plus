package org.dromara.djs.warehouse.stock.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「地块」展示文案口径（V6 row92）。
 *
 * <p>这份规则同时被两边实现：后端 {@link PlotLabel}（导出件）与前端
 * {@code plus-ui/src/utils/plotTag.ts#formatPlotLabel}（页面）。本测试锁死后端这一半 ——
 * 两边一旦漂移，甲方拿导出对账会看到与页面不一致的地块，重报同一条问题。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@DisplayName("三期「地块」展示文案口径（V6 row92）")
class PlotLabelTest {

    @Test
    @DisplayName("三期行恒显示「三期」—— 即便它底下挂着真实地块名也不显示地块名")
    void thirdPhaseAlwaysWinsOverPlotName() {
        // 作物恰好 1 块在种地块时，三期篮会带真实 plot_id / plotName，此时仍必须显示「三期」
        assertThat(PlotLabel.of(1, "连栋棚2-1号")).isEqualTo("三期");
        assertThat(PlotLabel.of(1, null)).isEqualTo("三期");
        assertThat(PlotLabel.of(1, "")).isEqualTo("三期");
    }

    @Test
    @DisplayName("非三期行显示真实地块名")
    void normalRowShowsPlotName() {
        assertThat(PlotLabel.of(0, "连栋棚2-1号")).isEqualTo("连栋棚2-1号");
        assertThat(PlotLabel.of(null, "东三块")).isEqualTo("东三块");
    }

    @Test
    @DisplayName("既不是三期、也没有地块 → 「-」（存量行 thirdPhase 读出来是 null，不能当成三期）")
    void noPlotNoThirdPhaseFallsBackToDash() {
        assertThat(PlotLabel.of(0, null)).isEqualTo("-");
        assertThat(PlotLabel.of(null, null)).isEqualTo("-");
        assertThat(PlotLabel.of(0, "")).isEqualTo("-");
        assertThat(PlotLabel.of(0, "   ")).isEqualTo("-");
    }

    @Test
    @DisplayName("只有恰好 1 才算三期 —— 其他任何值都不是")
    void onlyOneMeansThirdPhase() {
        assertThat(PlotLabel.of(2, null)).isEqualTo("-");
        assertThat(PlotLabel.of(-1, "东三块")).isEqualTo("东三块");
    }
}
