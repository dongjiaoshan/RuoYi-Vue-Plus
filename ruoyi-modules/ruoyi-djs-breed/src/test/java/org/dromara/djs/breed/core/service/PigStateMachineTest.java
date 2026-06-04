package org.dromara.djs.breed.core.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PigStateMachine} 单元测试（BRD-CORE-001 ★ 业务心脏覆盖）。
 *
 * <p>覆盖 11×10 transition 矩阵的关键路径：简单 6 / 复杂 OESTRUS 2 / 复杂 NULL_RETURN 3 /
 * 性别校验 2 / 状态不变 2 / 终态聚合 3 / 终态拒绝 1 / INTRO 拒绝 1 / 非法 abnormalType 1
 * = 21 用例（超出 prompt 要求的 ≥ 15）。</p>
 *
 * <p>{@code MessageUtils.message} 在无 Spring 上下文时返回 key 本身（参 i18n 源码兜底逻辑），
 * 故本测试不启动 Spring 容器，断言异常的 message 包含 i18n key 子串。</p>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Tag("local")
@Tag("dev")
@DisplayName("PigStateMachine 单元测试 (BRD-CORE-001)")
class PigStateMachineTest {

    private final PigStateMachine sm = new PigStateMachine();

    // ─────────────────────── 简单 transition：BREED 5 from + FARROW + WEAN ───────────────────────

    @Test
    @DisplayName("BREED: HB → PZ (母猪)")
    void breed_HB_to_PZ() {
        assertThat(sm.nextStatus(PigLifecycle.HB, PigStatusEvent.BREED, "F", null)).isEqualTo(PigLifecycle.PZ);
    }

    @Test
    @DisplayName("BREED: DN → PZ (母猪)")
    void breed_DN_to_PZ() {
        assertThat(sm.nextStatus(PigLifecycle.DN, PigStatusEvent.BREED, "F", null)).isEqualTo(PigLifecycle.PZ);
    }

    @Test
    @DisplayName("BREED: LC → PZ (母猪)")
    void breed_LC_to_PZ() {
        assertThat(sm.nextStatus(PigLifecycle.LC, PigStatusEvent.BREED, "F", null)).isEqualTo(PigLifecycle.PZ);
    }

    @Test
    @DisplayName("BREED: KH → PZ (母猪)")
    void breed_KH_to_PZ() {
        assertThat(sm.nextStatus(PigLifecycle.KH, PigStatusEvent.BREED, "F", null)).isEqualTo(PigLifecycle.PZ);
    }

    @Test
    @DisplayName("BREED: FQ → PZ (母猪)")
    void breed_FQ_to_PZ() {
        assertThat(sm.nextStatus(PigLifecycle.FQ, PigStatusEvent.BREED, "F", null)).isEqualTo(PigLifecycle.PZ);
    }

    @Test
    @DisplayName("FARROW: PZ → FM")
    void farrow_PZ_to_FM() {
        assertThat(sm.nextStatus(PigLifecycle.PZ, PigStatusEvent.FARROW, "F", null)).isEqualTo(PigLifecycle.FM);
    }

    @Test
    @DisplayName("FARROW: 非 PZ from 抛 invalid_transition")
    void farrow_non_PZ_throws() {
        assertThatThrownBy(() -> sm.nextStatus(PigLifecycle.HB, PigStatusEvent.FARROW, "F", null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.invalid_transition");
    }

    @Test
    @DisplayName("WEAN: FM → DN")
    void wean_FM_to_DN() {
        assertThat(sm.nextStatus(PigLifecycle.FM, PigStatusEvent.WEAN, "F", null)).isEqualTo(PigLifecycle.DN);
    }

    // ─────────────────────── 复杂 transition：OESTRUS ───────────────────────

    @Test
    @DisplayName("OESTRUS: PZ + isPregnantConfirmed=true → 保持 PZ（ADR-0010 确认妊娠不切态）")
    void oestrus_PZ_confirmed_stays_PZ() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("isPregnantConfirmed", true);
        assertThat(sm.nextStatus(PigLifecycle.PZ, PigStatusEvent.OESTRUS, "F", payload)).isEqualTo(PigLifecycle.PZ);
    }

    @Test
    @DisplayName("OESTRUS: PZ 无 isPregnantConfirmed → 保持 PZ（仅记录）")
    void oestrus_PZ_no_confirmation_stays_PZ() {
        assertThat(sm.nextStatus(PigLifecycle.PZ, PigStatusEvent.OESTRUS, "F", null)).isEqualTo(PigLifecycle.PZ);
    }

    @Test
    @DisplayName("OESTRUS: 非 PZ from 抛 invalid_transition")
    void oestrus_non_PZ_throws() {
        assertThatThrownBy(() -> sm.nextStatus(PigLifecycle.HB, PigStatusEvent.OESTRUS, "F", null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.invalid_transition");
    }

    // ─────────────────────── 复杂 transition：NULL_RETURN ───────────────────────

    @Test
    @DisplayName("NULL_RETURN: PZ + abnormalType=abort → LC")
    void null_return_abort_to_LC() {
        Map<String, Object> payload = Map.of("abnormalType", "abort");
        assertThat(sm.nextStatus(PigLifecycle.PZ, PigStatusEvent.NULL_RETURN, "F", payload)).isEqualTo(PigLifecycle.LC);
    }

    @Test
    @DisplayName("NULL_RETURN: PZ + abnormalType=idle → KH")
    void null_return_idle_to_KH() {
        Map<String, Object> payload = Map.of("abnormalType", "idle");
        assertThat(sm.nextStatus(PigLifecycle.PZ, PigStatusEvent.NULL_RETURN, "F", payload)).isEqualTo(PigLifecycle.KH);
    }

    @Test
    @DisplayName("NULL_RETURN: PZ + abnormalType=return → FQ")
    void null_return_return_to_FQ() {
        Map<String, Object> payload = Map.of("abnormalType", "return");
        assertThat(sm.nextStatus(PigLifecycle.PZ, PigStatusEvent.NULL_RETURN, "F", payload)).isEqualTo(PigLifecycle.FQ);
    }

    @Test
    @DisplayName("NULL_RETURN: 未知 abnormalType 抛异常")
    void null_return_unknown_type_throws() {
        Map<String, Object> payload = Map.of("abnormalType", "weird");
        assertThatThrownBy(() -> sm.nextStatus(PigLifecycle.PZ, PigStatusEvent.NULL_RETURN, "F", payload))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.invalid_abnormal_type");
    }

    @Test
    @DisplayName("NULL_RETURN: 非 PZ from 抛 invalid_transition")
    void null_return_invalid_from_throws() {
        Map<String, Object> payload = Map.of("abnormalType", "abort");
        assertThatThrownBy(() -> sm.nextStatus(PigLifecycle.HB, PigStatusEvent.NULL_RETURN, "F", payload))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.invalid_transition");
    }

    // ─────────────────────── 性别校验 ───────────────────────

    @Test
    @DisplayName("BREED: 公猪触发 → female_only")
    void breed_male_throws_female_only() {
        assertThatThrownBy(() -> sm.nextStatus(PigLifecycle.HB, PigStatusEvent.BREED, "M", null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.female_only");
    }

    @Test
    @DisplayName("CASTRATE: 母猪触发 → male_only")
    void castrate_female_throws_male_only() {
        assertThatThrownBy(() -> sm.nextStatus(PigLifecycle.HB, PigStatusEvent.CASTRATE, "F", null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.male_only");
    }

    // ─────────────────────── 状态不变事件：CASTRATE / TRANSFER ───────────────────────

    @Test
    @DisplayName("CASTRATE: BOAR_ACTIVE → BOAR_ACTIVE (状态不变)")
    void castrate_boar_keeps_state() {
        assertThat(sm.nextStatus(PigLifecycle.BOAR_ACTIVE, PigStatusEvent.CASTRATE, "M", null))
            .isEqualTo(PigLifecycle.BOAR_ACTIVE);
    }

    @Test
    @DisplayName("TRANSFER: PZ → PZ (状态不变)")
    void transfer_PZ_keeps_state() {
        assertThat(sm.nextStatus(PigLifecycle.PZ, PigStatusEvent.TRANSFER, "F", null)).isEqualTo(PigLifecycle.PZ);
    }

    // ─────────────────────── 终态聚合：DIE / ELIMINATE / SLAUGHTER ───────────────────────

    @Test
    @DisplayName("DIE: 任何 (非 END) from → END")
    void die_any_to_END() {
        assertThat(sm.nextStatus(PigLifecycle.PZ, PigStatusEvent.DIE, "F", null)).isEqualTo(PigLifecycle.END);
        assertThat(sm.nextStatus(PigLifecycle.BOAR_ACTIVE, PigStatusEvent.DIE, "M", null)).isEqualTo(PigLifecycle.END);
    }

    @Test
    @DisplayName("ELIMINATE: 任何 (非 END) from → END")
    void eliminate_to_END() {
        assertThat(sm.nextStatus(PigLifecycle.FM, PigStatusEvent.ELIMINATE, "F", null)).isEqualTo(PigLifecycle.END);
    }

    @Test
    @DisplayName("SLAUGHTER: BOAR_ACTIVE → END")
    void slaughter_to_END() {
        assertThat(sm.nextStatus(PigLifecycle.BOAR_ACTIVE, PigStatusEvent.SLAUGHTER, "M", null)).isEqualTo(PigLifecycle.END);
    }

    // ─────────────────────── 终态拒绝继续 ───────────────────────

    @Test
    @DisplayName("END → 任何事件都拒绝（terminal）")
    void terminal_state_rejects() {
        assertThatThrownBy(() -> sm.nextStatus(PigLifecycle.END, PigStatusEvent.BREED, "F", null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.state.terminal");
        assertThatThrownBy(() -> sm.nextStatus(PigLifecycle.END, PigStatusEvent.TRANSFER, "F", null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.state.terminal");
    }

    // ─────────────────────── INTRO 不走 fireEvent ───────────────────────

    @Test
    @DisplayName("INTRO: 走 fireEvent 直接抛（应使用 createPig）")
    void intro_via_fire_throws() {
        assertThatThrownBy(() -> sm.nextStatus(PigLifecycle.HB, PigStatusEvent.INTRO, "F", null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.intro_use_create");
    }

    // ─────────────────────── 非法 BREED from（HB/DN/LC/KH/FQ 之外） ───────────────────────

    @Test
    @DisplayName("BREED: PZ → PZ 是非法 transition（已配种不能再配）")
    void breed_PZ_invalid_transition() {
        assertThatThrownBy(() -> sm.nextStatus(PigLifecycle.PZ, PigStatusEvent.BREED, "F", null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.event.invalid_transition");
    }
}
