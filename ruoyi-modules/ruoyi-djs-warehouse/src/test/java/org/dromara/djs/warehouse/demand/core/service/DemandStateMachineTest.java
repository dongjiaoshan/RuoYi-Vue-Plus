package org.dromara.djs.warehouse.demand.core.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DemandStateMachine} 单元测试（WMS-DEMAND-001）。
 *
 * <p>覆盖 7×6 = 42 转移矩阵中所有合法 + 关键非法用例（共 20+ 用例 / 覆盖率 ≥ 80%）。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Tag("local")
@Tag("dev")
class DemandStateMachineTest {

    private final DemandStateMachine sm = new DemandStateMachine();

    @Nested
    @DisplayName("合法转移路径")
    class Happy {

        @Test
        @DisplayName("DRAFT + SUBMIT → SUBMITTED")
        void draftSubmit() {
            assertThat(sm.nextStatus(DemandStatus.DRAFT, DemandEvent.SUBMIT)).isEqualTo(DemandStatus.SUBMITTED);
        }

        @Test
        @DisplayName("SUBMITTED + CONFIRM → CONFIRMED")
        void submittedConfirm() {
            assertThat(sm.nextStatus(DemandStatus.SUBMITTED, DemandEvent.CONFIRM)).isEqualTo(DemandStatus.CONFIRMED);
        }

        @Test
        @DisplayName("CONFIRMED + START_PRODUCTION → IN_PRODUCTION")
        void confirmedStart() {
            assertThat(sm.nextStatus(DemandStatus.CONFIRMED, DemandEvent.START_PRODUCTION))
                .isEqualTo(DemandStatus.IN_PRODUCTION);
        }

        @Test
        @DisplayName("IN_PRODUCTION + PARTIAL_SHIP → PARTIAL_SHIPPED")
        void inProductionPartialShip() {
            assertThat(sm.nextStatus(DemandStatus.IN_PRODUCTION, DemandEvent.PARTIAL_SHIP))
                .isEqualTo(DemandStatus.PARTIAL_SHIPPED);
        }

        @Test
        @DisplayName("PARTIAL_SHIPPED + PARTIAL_SHIP → PARTIAL_SHIPPED（continue partial）")
        void partialShipReentry() {
            assertThat(sm.nextStatus(DemandStatus.PARTIAL_SHIPPED, DemandEvent.PARTIAL_SHIP))
                .isEqualTo(DemandStatus.PARTIAL_SHIPPED);
        }

        @Test
        @DisplayName("IN_PRODUCTION + COMPLETE → COMPLETED")
        void inProductionComplete() {
            assertThat(sm.nextStatus(DemandStatus.IN_PRODUCTION, DemandEvent.COMPLETE))
                .isEqualTo(DemandStatus.COMPLETED);
        }

        @Test
        @DisplayName("PARTIAL_SHIPPED + COMPLETE → COMPLETED")
        void partialShippedComplete() {
            assertThat(sm.nextStatus(DemandStatus.PARTIAL_SHIPPED, DemandEvent.COMPLETE))
                .isEqualTo(DemandStatus.COMPLETED);
        }

        @Test
        @DisplayName("DRAFT + CANCEL → CANCELLED")
        void draftCancel() {
            assertThat(sm.nextStatus(DemandStatus.DRAFT, DemandEvent.CANCEL)).isEqualTo(DemandStatus.CANCELLED);
        }

        @Test
        @DisplayName("SUBMITTED + CANCEL → CANCELLED")
        void submittedCancel() {
            assertThat(sm.nextStatus(DemandStatus.SUBMITTED, DemandEvent.CANCEL)).isEqualTo(DemandStatus.CANCELLED);
        }

        @Test
        @DisplayName("CONFIRMED + CANCEL → CANCELLED")
        void confirmedCancel() {
            assertThat(sm.nextStatus(DemandStatus.CONFIRMED, DemandEvent.CANCEL)).isEqualTo(DemandStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("非法转移路径（应抛 ServiceException）")
    class Illegal {

        @Test
        @DisplayName("IN_PRODUCTION + CANCEL → 拒绝（排产后不能取消）")
        void inProductionCancelForbidden() {
            assertThatThrownBy(() -> sm.nextStatus(DemandStatus.IN_PRODUCTION, DemandEvent.CANCEL))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("illegal_transition");
        }

        @Test
        @DisplayName("PARTIAL_SHIPPED + CANCEL → 拒绝（排产后不能取消）")
        void partialShippedCancelForbidden() {
            assertThatThrownBy(() -> sm.nextStatus(DemandStatus.PARTIAL_SHIPPED, DemandEvent.CANCEL))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("illegal_transition");
        }

        @Test
        @DisplayName("COMPLETED + any → 拒绝（终态）")
        void completedTerminal() {
            for (DemandEvent ev : DemandEvent.values()) {
                assertThatThrownBy(() -> sm.nextStatus(DemandStatus.COMPLETED, ev))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("terminal");
            }
        }

        @Test
        @DisplayName("CANCELLED + any → 拒绝（终态）")
        void cancelledTerminal() {
            for (DemandEvent ev : DemandEvent.values()) {
                assertThatThrownBy(() -> sm.nextStatus(DemandStatus.CANCELLED, ev))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("terminal");
            }
        }

        @Test
        @DisplayName("DRAFT + CONFIRM → 拒绝（跳态）")
        void draftConfirmSkip() {
            assertThatThrownBy(() -> sm.nextStatus(DemandStatus.DRAFT, DemandEvent.CONFIRM))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("illegal_transition");
        }

        @Test
        @DisplayName("SUBMITTED + START_PRODUCTION → 拒绝（跳过确认）")
        void submittedStartSkip() {
            assertThatThrownBy(() -> sm.nextStatus(DemandStatus.SUBMITTED, DemandEvent.START_PRODUCTION))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("illegal_transition");
        }

        @Test
        @DisplayName("DRAFT + PARTIAL_SHIP → 拒绝")
        void draftPartialShip() {
            assertThatThrownBy(() -> sm.nextStatus(DemandStatus.DRAFT, DemandEvent.PARTIAL_SHIP))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("illegal_transition");
        }

        @Test
        @DisplayName("CONFIRMED + COMPLETE → 拒绝（必须先 START_PRODUCTION）")
        void confirmedCompleteForbidden() {
            assertThatThrownBy(() -> sm.nextStatus(DemandStatus.CONFIRMED, DemandEvent.COMPLETE))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("illegal_transition");
        }
    }

    @Nested
    @DisplayName("参数校验")
    class ArgumentValidation {

        @Test
        @DisplayName("from=null → NPE")
        void nullFrom() {
            assertThatThrownBy(() -> sm.nextStatus(null, DemandEvent.SUBMIT))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("event=null → NPE")
        void nullEvent() {
            assertThatThrownBy(() -> sm.nextStatus(DemandStatus.DRAFT, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @DisplayName("终态枚举：COMPLETED / CANCELLED isTerminal() = true，其他 false")
    void terminalFlag() {
        assertThat(DemandStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(DemandStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(DemandStatus.DRAFT.isTerminal()).isFalse();
        assertThat(DemandStatus.SUBMITTED.isTerminal()).isFalse();
        assertThat(DemandStatus.CONFIRMED.isTerminal()).isFalse();
        assertThat(DemandStatus.IN_PRODUCTION.isTerminal()).isFalse();
        assertThat(DemandStatus.PARTIAL_SHIPPED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("fromCodeSafe: 已知字面量返枚举，未知/null 返 null")
    void fromCodeSafe() {
        assertThat(DemandStatus.fromCodeSafe("DRAFT")).isEqualTo(DemandStatus.DRAFT);
        assertThat(DemandStatus.fromCodeSafe("COMPLETED")).isEqualTo(DemandStatus.COMPLETED);
        assertThat(DemandStatus.fromCodeSafe(null)).isNull();
        assertThat(DemandStatus.fromCodeSafe("UNKNOWN_STATE")).isNull();
        assertThat(DemandStatus.fromCodeSafe("")).isNull();
    }
}
