package org.dromara.djs.warehouse.demand.core;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link StoreDemandStatusMapping} 单测（门店视角 6 态的读口径 + 筛选 SQL 口径）。
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

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("derive：仓库 7 态 + 收货标记 → 门店态（发货态按到店量满量算 已发货）")
    void deriveCoversAllStatuses() {
        BigDecimal full = bd("2");
        BigDecimal demand = bd("2");
        assertThat(StoreDemandStatusMapping.derive("SUBMITTED", false, full, demand)).isEqualTo("SUBMITTED");
        // SUBMITTED 不看 received_time（业务上不可能已收货，但即便脏数据也仍算待确认）
        assertThat(StoreDemandStatusMapping.derive("SUBMITTED", true, full, demand)).isEqualTo("SUBMITTED");
        assertThat(StoreDemandStatusMapping.derive("CONFIRMED", false, full, demand)).isEqualTo("CONFIRMED");
        assertThat(StoreDemandStatusMapping.derive("CONFIRMED", true, full, demand)).isEqualTo("ARRIVED");
        assertThat(StoreDemandStatusMapping.derive("IN_PRODUCTION", false, full, demand)).isEqualTo("CONFIRMED");
        assertThat(StoreDemandStatusMapping.derive("PARTIAL_SHIPPED", false, full, demand)).isEqualTo("SHIPPED");
        assertThat(StoreDemandStatusMapping.derive("PARTIAL_SHIPPED", true, full, demand)).isEqualTo("ARRIVED");
        assertThat(StoreDemandStatusMapping.derive("COMPLETED", false, full, demand)).isEqualTo("SHIPPED");
        assertThat(StoreDemandStatusMapping.derive("COMPLETED", true, full, demand)).isEqualTo("ARRIVED");
        assertThat(StoreDemandStatusMapping.derive("DELETED", false, full, demand)).isEqualTo("DELETED");
        assertThat(StoreDemandStatusMapping.derive("CANCELLED", false, full, demand)).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("derive：已发货态按到店量三分 —— 0 → 已确认 / 不足 → 部分到店 / 够 → 已发货（V6-R197）")
    void deriveSplitsShippedByArrivedQuantity() {
        for (String status : List.of("PARTIAL_SHIPPED", "COMPLETED")) {
            // 甲方原话：「到店量等于 0 时，状态还是【已确认】状态」（缺量出车把需求推到 COMPLETED，但一件没到）
            assertThat(StoreDemandStatusMapping.derive(status, false, bd("0"), bd("5")))
                .as(status + " + 到店量 0").isEqualTo("CONFIRMED");
            // 负数是脏数据，与 0 同处置（不当成「到过货」）
            assertThat(StoreDemandStatusMapping.derive(status, false, bd("-1"), bd("5")))
                .as(status + " + 到店量 -1").isEqualTo("CONFIRMED");
            assertThat(StoreDemandStatusMapping.derive(status, false, bd("1"), bd("5")))
                .as(status + " + 部分到店").isEqualTo("PARTIAL_ARRIVED");
            assertThat(StoreDemandStatusMapping.derive(status, false, bd("4.999"), bd("5")))
                .as(status + " + 差一点").isEqualTo("PARTIAL_ARRIVED");
            assertThat(StoreDemandStatusMapping.derive(status, false, bd("5"), bd("5")))
                .as(status + " + 刚好满").isEqualTo("SHIPPED");
            // 超发（多发一件）仍算已发货，不新造第七态
            assertThat(StoreDemandStatusMapping.derive(status, false, bd("6"), bd("5")))
                .as(status + " + 超发").isEqualTo("SHIPPED");
            // 精度差异（5.000 vs 5）按数值比较，不按 equals
            assertThat(StoreDemandStatusMapping.derive(status, false, bd("5.000"), bd("5")))
                .as(status + " + 精度不同的满量").isEqualTo("SHIPPED");
        }
    }

    @Test
    @DisplayName("derive：已收货优先于到店量 —— 到店量 0 / 部分 / 满 都算 确认到店")
    void deriveReceivedWinsOverArrivedQuantity() {
        assertThat(StoreDemandStatusMapping.derive("COMPLETED", true, bd("0"), bd("5"))).isEqualTo("ARRIVED");
        assertThat(StoreDemandStatusMapping.derive("COMPLETED", true, bd("2"), bd("5"))).isEqualTo("ARRIVED");
        assertThat(StoreDemandStatusMapping.derive("PARTIAL_SHIPPED", true, bd("5"), bd("5"))).isEqualTo("ARRIVED");
    }

    @Test
    @DisplayName("derive：到店量 null（没查）→ 不细分，退回 已发货；需求量 null（比不出）→ 也退回 已发货")
    void deriveNullQuantitiesFallBackToShipped() {
        // 调用方没回填到店量：宁可维持 R197 之前的行为，也不猜成「已确认」
        assertThat(StoreDemandStatusMapping.derive("COMPLETED", false, null, bd("5"))).isEqualTo("SHIPPED");
        assertThat(StoreDemandStatusMapping.derive("PARTIAL_SHIPPED", false, null, null)).isEqualTo("SHIPPED");
        // 到店量已知 > 0 但需求量未知：比不出「够不够」→ 维持 已发货
        assertThat(StoreDemandStatusMapping.derive("COMPLETED", false, bd("3"), null)).isEqualTo("SHIPPED");
        // 到店量已知且为 0：这一判断不需要需求量，仍归 已确认
        assertThat(StoreDemandStatusMapping.derive("COMPLETED", false, bd("0"), null)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("deriveIgnoringArrival：与 derive(status, received, null, null) 完全同解（不是第二套口径）")
    void deriveIgnoringArrivalDelegates() {
        for (String status : List.of("SUBMITTED", "CONFIRMED", "IN_PRODUCTION",
            "PARTIAL_SHIPPED", "COMPLETED", "DELETED", "CANCELLED", "DRAFT")) {
            for (boolean received : new boolean[]{true, false}) {
                assertThat(StoreDemandStatusMapping.deriveIgnoringArrival(status, received))
                    .as(status + " received=" + received)
                    .isEqualTo(StoreDemandStatusMapping.derive(status, received, null, null));
            }
        }
    }

    @Test
    @DisplayName("derive：null → null；DRAFT 等门店端不可见态回退原值")
    void deriveEdgeCases() {
        assertThat(StoreDemandStatusMapping.derive(null, false, bd("1"), bd("1"))).isNull();
        assertThat(StoreDemandStatusMapping.derive("DRAFT", false, bd("1"), bd("1"))).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("labelOf：6 态中文与字典 djs_store_demand_status 逐字一致；未知态裹中文不甩枚举")
    void labelOfCoversSixStatuses() {
        assertThat(StoreDemandStatusMapping.labelOf("SUBMITTED")).isEqualTo("待确认");
        assertThat(StoreDemandStatusMapping.labelOf("CONFIRMED")).isEqualTo("已确认");
        assertThat(StoreDemandStatusMapping.labelOf("PARTIAL_ARRIVED")).isEqualTo("部分到店");
        assertThat(StoreDemandStatusMapping.labelOf("SHIPPED")).isEqualTo("已发货");
        assertThat(StoreDemandStatusMapping.labelOf("ARRIVED")).isEqualTo("已到店");
        assertThat(StoreDemandStatusMapping.labelOf("DELETED")).isEqualTo("已删除");
        assertThat(StoreDemandStatusMapping.labelOf(null)).isEmpty();
        assertThat(StoreDemandStatusMapping.labelOf("WAT")).isEqualTo("未知状态（WAT）");
    }

    @Test
    @DisplayName("sqlPredicate：5 个可筛门店态 → 与 derive 逐条同构的 WHERE 片段")
    void sqlPredicateMatchesMappingTable() {
        assertThat(StoreDemandStatusMapping.sqlPredicate("SUBMITTED"))
            .isEqualTo("(demand_status = 'SUBMITTED')");
        // 已确认 = 仓库已确认未收货，「或」已发货态但一件都没到（R197）
        assertThat(StoreDemandStatusMapping.sqlPredicate("CONFIRMED"))
            .startsWith("((demand_status IN ('CONFIRMED','IN_PRODUCTION') AND received_time IS NULL)"
                + " OR (demand_status IN ('PARTIAL_SHIPPED','COMPLETED') AND received_time IS NULL AND ")
            .endsWith(" <= 0))")
            .contains("pp.is_delivery_check = 1")
            .contains("pp.demand_id = t_warehouse_demand_manage.id");
        assertThat(StoreDemandStatusMapping.sqlPredicate("PARTIAL_ARRIVED"))
            .startsWith("(demand_status IN ('PARTIAL_SHIPPED','COMPLETED') AND received_time IS NULL AND ")
            .contains(" > 0 AND demand_quantity IS NOT NULL AND ")
            .endsWith(" < demand_quantity)");
        assertThat(StoreDemandStatusMapping.sqlPredicate("SHIPPED"))
            .startsWith("(demand_status IN ('PARTIAL_SHIPPED','COMPLETED') AND received_time IS NULL AND ")
            .contains(" > 0 AND (demand_quantity IS NULL OR ")
            .endsWith(" >= demand_quantity))");
        assertThat(StoreDemandStatusMapping.sqlPredicate("ARRIVED"))
            .isEqualTo("(demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED') "
                + "AND received_time IS NOT NULL)");
    }

    @Test
    @DisplayName("sqlPredicate：到店量子查询在 4 个用到它的态里逐字相同（防两套口径）")
    void arrivedQtySubquerySharedAcrossPredicates() {
        String confirmed = StoreDemandStatusMapping.sqlPredicate("CONFIRMED");
        String partial = StoreDemandStatusMapping.sqlPredicate("PARTIAL_ARRIVED");
        String shipped = StoreDemandStatusMapping.sqlPredicate("SHIPPED");
        String subquery = partial.substring(partial.indexOf("(SELECT COUNT(*)"), partial.indexOf(") > 0") + 1);
        assertThat(confirmed).contains(subquery);
        assertThat(shipped).contains(subquery);
        // 部分到店里出现两次（> 0 与 < demand_quantity），必须是同一份
        assertThat(partial.split(java.util.regex.Pattern.quote(subquery), -1)).hasSize(3);
    }

    @Test
    @DisplayName("sqlPredicate：大小写 / 首尾空白容错")
    void sqlPredicateNormalizesInput() {
        assertThat(StoreDemandStatusMapping.sqlPredicate(" shipped "))
            .isEqualTo(StoreDemandStatusMapping.sqlPredicate("SHIPPED"));
        assertThat(StoreDemandStatusMapping.sqlPredicate(" partial_arrived "))
            .isEqualTo(StoreDemandStatusMapping.sqlPredicate("PARTIAL_ARRIVED"));
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
        assertThat(sql).isEqualTo("(" + StoreDemandStatusMapping.sqlPredicate("SUBMITTED")
            + " OR " + StoreDemandStatusMapping.sqlPredicate("CONFIRMED") + ")");
    }

    @Test
    @DisplayName("sqlPredicateAny：新态 PARTIAL_ARRIVED 可与其余态一起多选")
    void sqlPredicateAnyAcceptsPartialArrived() {
        String sql = StoreDemandStatusMapping.sqlPredicateAny(List.of("PARTIAL_ARRIVED", "SHIPPED"));
        assertThat(sql).isEqualTo("(" + StoreDemandStatusMapping.sqlPredicate("PARTIAL_ARRIVED")
            + " OR " + StoreDemandStatusMapping.sqlPredicate("SHIPPED") + ")");
    }

    @Test
    @DisplayName("sqlPredicateAny：重复项去重保序，不产生重复 OR 分支")
    void sqlPredicateAnyDeduplicates() {
        assertThat(StoreDemandStatusMapping.sqlPredicateAny(List.of("SHIPPED", "shipped", " SHIPPED ")))
            .isEqualTo("(" + StoreDemandStatusMapping.sqlPredicate("SHIPPED") + ")");
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
