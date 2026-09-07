package org.dromara.djs.warehouse.demand.core;

import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DemandArrivedQuantityFiller} 直测。
 *
 * <p>V6-R197 起门店视角状态由到店量决定，这一列填错 / 漏填就直接把状态算成「已发货」——
 * 正是甲方 row197 报的那个 bug。三个调用方（warehouse 分页列表 / store enricher / mp 按天明细）
 * 都 mock 掉本类，不直测的话谁也测不到它。</p>
 *
 * @author djs
 * @since V6-R197
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DemandArrivedQuantityFiller 到店量回填单测")
class DemandArrivedQuantityFillerTest {

    @Mock
    private ProductProductionMapper productProductionMapper;

    @InjectMocks
    private DemandArrivedQuantityFiller filler;

    private static DemandManageVo row(Long id) {
        DemandManageVo vo = new DemandManageVo();
        vo.setId(id);
        return vo;
    }

    @Test
    @DisplayName("批量回填：查到的按值填，没发过车的填 0 而不是 null（'—' 与 0 是两件事）")
    void fillsZeroForDemandsWithoutShipment() {
        DemandManageVo shipped = row(1L);
        DemandManageVo none = row(2L);
        when(productProductionMapper.selectArrivedQuantityByDemandIds(any()))
            .thenReturn(List.of(Map.of("demandId", 1L, "arrivedQty", 3L)));

        filler.fill(List.of(shipped, none));

        assertThat(shipped.getArrivedQuantity()).isEqualByComparingTo("3");
        assertThat(none.getArrivedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("一页只打一次库（禁 N+1），且同 id 去重后再下推")
    void queriesOnceWithDistinctIds() {
        when(productProductionMapper.selectArrivedQuantityByDemandIds(any())).thenReturn(List.of());

        filler.fill(List.of(row(1L), row(1L), row(2L)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(productProductionMapper, times(1)).selectArrivedQuantityByDemandIds(captor.capture());
        assertThat(captor.getValue()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("已有值的行跳过：不覆盖上游结果，全部已填时一次库都不打")
    void skipsAlreadyFilledRows() {
        DemandManageVo prefilled = row(1L);
        prefilled.setArrivedQuantity(new BigDecimal("9"));

        filler.fill(List.of(prefilled));

        assertThat(prefilled.getArrivedQuantity()).isEqualByComparingTo("9");
        verify(productProductionMapper, never()).selectArrivedQuantityByDemandIds(any());
    }

    @Test
    @DisplayName("null / 空 / 全是无 id 的行 → 不打库，安静返回")
    void noopOnEmptyInput() {
        filler.fill(null);
        filler.fill(List.of());
        filler.fill(List.of(row(null)));

        verify(productProductionMapper, never()).selectArrivedQuantityByDemandIds(any());
        assertThat(filler.resolve(null)).isEmpty();
        assertThat(filler.resolve(List.of())).isEmpty();
    }

    @Test
    @DisplayName("resolve：聚合行里类型不对的记录直接跳过（脏数据不能整页崩）")
    void resolveIgnoresMalformedRows() {
        when(productProductionMapper.selectArrivedQuantityByDemandIds(any())).thenReturn(List.of(
            Map.of("demandId", 1L, "arrivedQty", 2L),
            Map.of("demandId", "not-a-number", "arrivedQty", 5L)
        ));

        Map<Long, BigDecimal> got = filler.resolve(List.of(1L, 2L));

        assertThat(got).hasSize(1);
        assertThat(got.get(1L)).isEqualByComparingTo("2");
    }
}
