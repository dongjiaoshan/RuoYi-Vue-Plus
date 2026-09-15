package org.dromara.djs.warehouse.stock.domain;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V6 row223/row224 / D-0068「出库先进先出自动，工人不再手选哪一篮」的算法护栏。
 *
 * <p>库存查询行内出库、猪肉转移、毛菜间出库三条路共用这一份分配，之前只有 VegOut 侧的
 * service 测间接覆盖，算法本身没有直接测。</p>
 */
@Tag("local")
@Tag("dev")
class FifoAllocatorTest {

    private LocationStock basket(long id, String stock) {
        LocationStock s = new LocationStock();
        s.setId(id);
        s.setProductId(10L);
        s.setLocationId(900L);
        s.setProductStock(new BigDecimal(stock));
        return s;
    }

    @Test
    @DisplayName("按给定顺序先扣光前面的篮，剩下的才落到后面")
    void allocatesInOrder() {
        Map<Long, BigDecimal> plan = FifoAllocator.allocate(
            List.of(basket(1L, "42.000"), basket(2L, "32.000")), new BigDecimal("50.000"));

        assertThat(plan.keySet()).containsExactly(1L, 2L);
        assertThat(plan.get(1L)).isEqualByComparingTo("42.000");
        assertThat(plan.get(2L)).isEqualByComparingTo("8.000");
    }

    @Test
    @DisplayName("空篮跳过 —— 扣 0 会白写一条 0 量流水，出库记录里多出一行看不懂的空行")
    void skipsEmptyBaskets() {
        Map<Long, BigDecimal> plan = FifoAllocator.allocate(
            List.of(basket(1L, "0.000"), basket(2L, "8.070")), new BigDecimal("5.000"));

        assertThat(plan).containsOnlyKeys(2L);
        assertThat(plan.get(2L)).isEqualByComparingTo("5.000");
    }

    @Test
    @DisplayName("总量不足：在扣任何一篮之前就抛，不返回一份「扣一半」的计划")
    void rejectsOverTotal() {
        assertThatThrownBy(() -> FifoAllocator.allocate(
            List.of(basket(1L, "42.000"), basket(2L, "32.000")), new BigDecimal("74.001")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("超过该行库存(74)");
    }

    @Test
    @DisplayName("恰好等于合计 → 放行，且每一篮都被扣光")
    void allowsExactTotal() {
        Map<Long, BigDecimal> plan = FifoAllocator.allocate(
            List.of(basket(1L, "42.000"), basket(2L, "32.000")), new BigDecimal("74.000"));

        assertThat(plan.get(1L)).isEqualByComparingTo("42.000");
        assertThat(plan.get(2L)).isEqualByComparingTo("32.000");
    }

    @Test
    @DisplayName("篮组必须同产品/同库位/同耳号/同地块 —— 裸调接口混进别的产品要当场拒")
    void rejectsMixedGroup() {
        LocationStock otherProduct = basket(2L, "146.000");
        otherProduct.setProductId(99L);

        assertThatThrownBy(() -> FifoAllocator.allocate(
            List.of(basket(1L, "42.000"), otherProduct), new BigDecimal("50.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不属于同一个产品");
    }

    @Test
    @DisplayName("同产品但不同库位也拒（库位是列表上看得见的一列）")
    void rejectsMixedLocation() {
        LocationStock otherLoc = basket(2L, "20.000");
        otherLoc.setLocationId(901L);

        assertThatThrownBy(() -> FifoAllocator.allocate(
            List.of(basket(1L, "42.000"), otherLoc), new BigDecimal("50.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不属于同一个产品");
    }

    @Test
    @DisplayName("空组直接拒")
    void rejectsEmpty() {
        assertThatThrownBy(() -> FifoAllocator.allocate(List.of(), BigDecimal.ONE))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("未指定要出库的库存行");
    }

    // -------- 重复篮 id：篮组是前端传的一串 id，同一个篮说两遍不能被当成两个篮 --------

    /**
     * 同一个篮 id 传两次时，可用量只能算<b>一遍</b>。
     *
     * <p>不去重的话这里会连错两步：先是合计把这一篮数两遍（10+10=20）骗过总量闸，
     * 接着分配结果按篮 id 收进 map，后写覆盖前写 —— 净效果是「校验放行、实际只扣一篮、
     * 接口还返回成功」，库存凭空多出一截且不报任何错。
     * {@code assertSameGroup} 拦不住它：同一个篮跟它自己当然同组。</p>
     */
    @Test
    @DisplayName("同一篮 id 传两次：可用量不翻倍，超量照样拒（不会静默少扣）")
    void dedupesRepeatedBasketId() {
        LocationStock only = basket(1L, "10.000");

        assertThatThrownBy(() -> FifoAllocator.allocate(
            List.of(only, only), new BigDecimal("15.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("超过该行库存")
            .as("合计必须报 10 而不是被数成 20")
            .hasMessageContaining("10");
    }

    @Test
    @DisplayName("重复 id 在量够时也只出现一次，且扣的是真实余量")
    void dedupedPlanKeepsSingleEntry() {
        LocationStock a = basket(1L, "10.000");
        Map<Long, BigDecimal> plan = FifoAllocator.allocate(
            List.of(a, a, basket(2L, "20.000")), new BigDecimal("25.000"));

        assertThat(plan).hasSize(2);
        assertThat(plan.get(1L)).isEqualByComparingTo("10.000");
        assertThat(plan.get(2L)).isEqualByComparingTo("15.000");
        assertThat(plan.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
            .as("分配总量必须恰好等于申请量，不能因为去重少扣")
            .isEqualByComparingTo("25.000");
    }
}
