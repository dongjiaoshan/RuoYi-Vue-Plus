package org.dromara.djs.warehouse.demand.core;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link StoreDemandStatusMapping} 单测（门店视角 5 态的读口径 + 筛选 SQL 口径）。
 *
 * <p>这两件事必须永远一致，所以两个方向都测：{@code derive} 把行算成门店态，
 * {@code sqlPredicate} 反过来生成筛这些行的 WHERE 片段。</p>
 *
 * @author djs
 * @since STORE-MP-BOARD-001
 */
@Tag("local")
@Tag("dev")
@DisplayName("StoreDemandStatusMapping 门店态映射单测")
class StoreDemandStatusMappingTest {

    @Test
    @DisplayName("derive：仓库 7 态 + 收货标记 → 门店 5 态")
    void deriveCoversAllStatuses() {
        assertThat(StoreDemandStatusMapping.derive("SUBMITTED", false)).isEqualTo("SUBMITTED");
        // SUBMITTED 不看 received_time（业务上不可能已收货，但即便脏数据也仍算待确认）
        assertThat(StoreDemandStatusMapping.derive("SUBMITTED", true)).isEqualTo("SUBMITTED");
        assertThat(StoreDemandStatusMapping.derive("CONFIRMED", false)).isEqualTo("CONFIRMED");
        assertThat(StoreDemandStatusMapping.derive("CONFIRMED", true)).isEqualTo("ARRIVED");
        assertThat(StoreDemandStatusMapping.derive("PARTIAL_SHIPPED", false)).isEqualTo("SHIPPED");
        assertThat(StoreDemandStatusMapping.derive("PARTIAL_SHIPPED", true)).isEqualTo("ARRIVED");
        assertThat(StoreDemandStatusMapping.derive("COMPLETED", false)).isEqualTo("SHIPPED");
        assertThat(StoreDemandStatusMapping.derive("COMPLETED", true)).isEqualTo("ARRIVED");
        assertThat(StoreDemandStatusMapping.derive("DELETED", false)).isEqualTo("DELETED");
        assertThat(StoreDemandStatusMapping.derive("CANCELLED", false)).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("derive：null → null；DRAFT 等门店端不可见态回退原值")
    void deriveEdgeCases() {
        assertThat(StoreDemandStatusMapping.derive(null, false)).isNull();
        assertThat(StoreDemandStatusMapping.derive("DRAFT", false)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("sqlPredicate：4 个可筛门店态 → 与映射表逐条对应的 WHERE 片段")
    void sqlPredicateMatchesMappingTable() {
        assertThat(StoreDemandStatusMapping.sqlPredicate("SUBMITTED"))
            .isEqualTo("(demand_status = 'SUBMITTED')");
        assertThat(StoreDemandStatusMapping.sqlPredicate("CONFIRMED"))
            .isEqualTo("(demand_status = 'CONFIRMED' AND received_time IS NULL)");
        assertThat(StoreDemandStatusMapping.sqlPredicate("SHIPPED"))
            .isEqualTo("(demand_status IN ('PARTIAL_SHIPPED','COMPLETED') AND received_time IS NULL)");
        assertThat(StoreDemandStatusMapping.sqlPredicate("ARRIVED"))
            .isEqualTo("(demand_status IN ('CONFIRMED','PARTIAL_SHIPPED','COMPLETED') AND received_time IS NOT NULL)");
    }

    @Test
    @DisplayName("sqlPredicate：大小写 / 首尾空白容错")
    void sqlPredicateNormalizesInput() {
        assertThat(StoreDemandStatusMapping.sqlPredicate(" shipped "))
            .isEqualTo(StoreDemandStatusMapping.sqlPredicate("SHIPPED"));
    }

    @Test
    @DisplayName("sqlPredicate：空 / 未知态 / DELETED 一律报错，不静默放行")
    void sqlPredicateRejectsBadInput() {
        assertThatThrownBy(() -> StoreDemandStatusMapping.sqlPredicate(null))
            .isInstanceOf(ServiceException.class).hasMessageContaining("不能为空");
        assertThatThrownBy(() -> StoreDemandStatusMapping.sqlPredicate("  "))
            .isInstanceOf(ServiceException.class).hasMessageContaining("不能为空");
        assertThatThrownBy(() -> StoreDemandStatusMapping.sqlPredicate("PENDING"))
            .isInstanceOf(ServiceException.class).hasMessageContaining("不支持的门店需求状态");
        // 门店端永不返回已删除行，允许它筛会与列表口径自相矛盾
        assertThatThrownBy(() -> StoreDemandStatusMapping.sqlPredicate("DELETED"))
            .isInstanceOf(ServiceException.class).hasMessageContaining("已删除");
    }

    @Test
    @DisplayName("sqlPredicateAny：多选 OR 拼接 + 外层括号")
    void sqlPredicateAnyJoinsWithOr() {
        String sql = StoreDemandStatusMapping.sqlPredicateAny(List.of("SUBMITTED", "CONFIRMED"));
        assertThat(sql).isEqualTo(
            "((demand_status = 'SUBMITTED') OR (demand_status = 'CONFIRMED' AND received_time IS NULL))");
    }

    @Test
    @DisplayName("sqlPredicateAny：重复项去重保序，不产生重复 OR 分支")
    void sqlPredicateAnyDeduplicates() {
        assertThat(StoreDemandStatusMapping.sqlPredicateAny(List.of("SHIPPED", "shipped", " SHIPPED ")))
            .isEqualTo("((demand_status IN ('PARTIAL_SHIPPED','COMPLETED') AND received_time IS NULL))");
    }

    @Test
    @DisplayName("sqlPredicateAny：null / 空集 / 全空白 → null（调用方不加该条件）")
    void sqlPredicateAnyEmptyReturnsNull() {
        assertThat(StoreDemandStatusMapping.sqlPredicateAny(null)).isNull();
        assertThat(StoreDemandStatusMapping.sqlPredicateAny(List.of())).isNull();
        assertThat(StoreDemandStatusMapping.sqlPredicateAny(List.of("", "  "))).isNull();
    }

    @Test
    @DisplayName("sqlPredicateAny：含非法态整体报错（不是丢掉那一项继续）")
    void sqlPredicateAnyRejectsBadElement() {
        assertThatThrownBy(() -> StoreDemandStatusMapping.sqlPredicateAny(List.of("SUBMITTED", "DELETED")))
            .isInstanceOf(ServiceException.class);
    }
}
