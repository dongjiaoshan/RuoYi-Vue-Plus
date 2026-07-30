package org.dromara.djs.breed.farm.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link PenCountUpdater} 单元测试（FIX-BRD-PENCOUNT-001）。
 *
 * <p>断言落到实际生成的 SQL 片段上：加法原子 {@code +}、减法带 {@code GREATEST(..., 0)} 防负、
 * 迁栏一减一加、空栏位 / 非正数 / 同栏短路不发 UPDATE。</p>
 *
 * <p>wrapper 全部用列名字符串（{@code .eq("id", ...)}）而非 lambda，故无需 MP TableInfo 缓存预热。</p>
 *
 * @author djs
 * @since FIX-BRD-PENCOUNT-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PenCountUpdater 单元测试 (FIX-BRD-PENCOUNT-001)")
class PenCountUpdaterTest {

    @Mock
    private PenMapper penMapper;

    private PenCountUpdater updater;

    @BeforeEach
    void setup() {
        updater = new PenCountUpdater(penMapper);
    }

    @SuppressWarnings("unchecked")
    private List<Wrapper<Pen>> captureWrappers(int expectedCount) {
        ArgumentCaptor<Wrapper<Pen>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(penMapper, times(expectedCount)).update(eq(null), captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("increase: current_count 原子 +N")
    void increase_atomic_plus() {
        updater.increase(8100L, 5);

        String sql = captureWrappers(1).get(0).getSqlSet();
        assertThat(sql).isEqualTo("current_count = COALESCE(current_count, 0) + 5");
    }

    @Test
    @DisplayName("decrease: current_count 原子 -N 且 GREATEST(...,0) 防负")
    void decrease_atomic_minus_clamped_at_zero() {
        updater.decrease(8100L, 1);

        String sql = captureWrappers(1).get(0).getSqlSet();
        assertThat(sql).isEqualTo("current_count = GREATEST(COALESCE(current_count, 0) - 1, 0)");
    }

    @Test
    @DisplayName("move: 旧栏 -N 在前、新栏 +N 在后，共两条 UPDATE")
    void move_decreases_source_then_increases_target() {
        updater.move(10L, 20L, 3);

        List<Wrapper<Pen>> wrappers = captureWrappers(2);
        assertThat(wrappers.get(0).getSqlSet())
            .isEqualTo("current_count = GREATEST(COALESCE(current_count, 0) - 3, 0)");
        assertThat(wrappers.get(1).getSqlSet())
            .isEqualTo("current_count = COALESCE(current_count, 0) + 3");
    }

    @Test
    @DisplayName("move: 同栏（含两侧都为 null）短路，不发 UPDATE")
    void move_same_pen_is_noop() {
        updater.move(10L, 10L, 1);
        updater.move(null, null, 1);

        verifyNoInteractions(penMapper);
    }

    @Test
    @DisplayName("move: 转出到「只落栋舍不落栏」→ 只减旧栏；从无栏转入 → 只加新栏")
    void move_handles_null_side() {
        updater.move(10L, null, 1);
        updater.move(null, 20L, 1);

        List<Wrapper<Pen>> wrappers = captureWrappers(2);
        assertThat(wrappers.get(0).getSqlSet()).contains("GREATEST");
        assertThat(wrappers.get(1).getSqlSet()).contains("+ 1");
    }

    @Test
    @DisplayName("penId 为 null / 头数 ≤ 0 → 空操作，不发 UPDATE")
    void null_pen_or_non_positive_delta_is_noop() {
        updater.increase(null, 1);
        updater.decrease(null, 1);
        updater.increase(8100L, 0);
        updater.decrease(8100L, 0);
        updater.increase(8100L, -2);
        updater.decrease(8100L, -2);
        updater.move(10L, 20L, 0);

        verify(penMapper, never()).update(eq(null), any());
    }
}
