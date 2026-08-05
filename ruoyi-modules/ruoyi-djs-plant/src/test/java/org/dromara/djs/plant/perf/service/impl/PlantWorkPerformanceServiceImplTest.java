package org.dromara.djs.plant.perf.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.plant.farm.service.IFarmRecordsService;
import org.dromara.djs.plant.perf.domain.PlantWorkPerformance;
import org.dromara.djs.plant.perf.domain.vo.PerfActivityAggRow;
import org.dromara.djs.plant.perf.domain.vo.PerfAggRow;
import org.dromara.djs.plant.perf.domain.vo.PlotCropTeamRow;
import org.dromara.djs.plant.perf.mapper.PlantWorkPerformanceMapper;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlantWorkPerformanceServiceImpl} 单测（PLT-PERF-001）。
 *
 * <p>覆盖核心 generate(statMonth)：聚合(公斤) × 单价快照(元/公斤) = 应付金额
 * + 幂等软删 + 月份校验。</p>
 *
 * <p>row39 口径：聚合源为毛菜处理间采摘重量录入的各班组对应作物称重重量
 * （{@code aggregateByMonth} 由 t_plant_plant_details 改取 t_warehouse_handle_record）。
 * 本类 mock baseMapper.aggregateByMonth，逻辑层（单价快照 × 换算 × 幂等软删）不受聚合源变更影响，
 * 预期金额/行数不变；agg() 构造的 (teamId × cropId × pickWeight 公斤) 现语义为各组称重总重量。</p>
 *
 * @author djs
 * @since PLT-PERF-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlantWorkPerformanceServiceImpl 单元测试")
class PlantWorkPerformanceServiceImplTest {

    @Mock
    private PlantWorkPerformanceMapper baseMapper;

    @Mock
    private IFarmRecordsService farmRecordsService;

    private PlantWorkPerformanceServiceImpl service;

    @BeforeEach
    void setup() {
        service = new PlantWorkPerformanceServiceImpl(baseMapper, farmRecordsService);
    }

    private PerfAggRow agg(Long teamId, Long cropId, String weight) {
        PerfAggRow r = new PerfAggRow();
        r.setTeamId(teamId);
        r.setCropId(cropId);
        r.setPickWeight(new BigDecimal(weight));
        return r;
    }

    @Test
    @DisplayName("generate: happy path → 软删旧月 + 2 组 INSERT，金额=量(公斤)×单价(元/公斤)")
    void testGenerate_HappyPath() {
        String month = "2026-04";
        // 2 组聚合：班组1×作物10 = 100.500 公斤；班组2×作物20 = 50.000 公斤
        when(baseMapper.aggregateByMonth(month)).thenReturn(List.of(
            agg(1L, 10L, "100.500"),
            agg(2L, 20L, "50.000")
        ));
        // 作物单价快照：作物10 = 1.20 元/公斤；作物20 = 2.00 元/公斤
        when(baseMapper.selectCropUnitPrices(anyCollection())).thenReturn(List.of(
            Map.of("cropId", 10L, "pickUnitPrice", new BigDecimal("1.20")),
            Map.of("cropId", 20L, "pickUnitPrice", new BigDecimal("2.00"))
        ));
        when(baseMapper.update(eq(null), any())).thenReturn(0);
        when(baseMapper.insert(any(PlantWorkPerformance.class))).thenReturn(1);

        int rows = service.generate(month);

        assertThat(rows).as("2 个聚合分组 → 2 行").isEqualTo(2);
        // 幂等软删先于聚合执行一次
        verify(baseMapper, times(1)).update(eq(null), any());

        ArgumentCaptor<PlantWorkPerformance> captor = ArgumentCaptor.forClass(PlantWorkPerformance.class);
        verify(baseMapper, times(2)).insert(captor.capture());
        List<PlantWorkPerformance> inserted = captor.getAllValues();

        PlantWorkPerformance r1 = inserted.stream().filter(p -> p.getTeamId().equals(1L)).findFirst().orElseThrow();
        assertThat(r1.getStatMonth()).isEqualTo(month);
        assertThat(r1.getCropId()).isEqualTo(10L);
        assertThat(r1.getPickWeight()).isEqualByComparingTo("100.500");
        assertThat(r1.getUnitPriceSnapshot()).isEqualByComparingTo("1.20");
        // 100.500 公斤 × 1.20 元/公斤 = 120.60
        assertThat(r1.getPerformanceAmount()).isEqualByComparingTo("120.60");
        assertThat(r1.getPerformanceRule()).isEqualTo("1.2 元/公斤");

        PlantWorkPerformance r2 = inserted.stream().filter(p -> p.getTeamId().equals(2L)).findFirst().orElseThrow();
        // 50.000 公斤 × 2.00 元/公斤 = 100.00
        assertThat(r2.getPerformanceAmount()).isEqualByComparingTo("100.00");
        // 绩效行不手工赋 tenant_id（走 MetaObjectHandler）
        assertThat(r2.getTenantId()).isNull();
    }

    @Test
    @DisplayName("generate: 当月活动全是销售去向(plot_id 全空) → 不查地块班组、不拼空 IN，按活动直接指定班组平摊")
    void testGenerate_allActivitiesAreSaleWithNullPlot() {
        String month = "2026-08";
        // 过磅聚合为空，只有一条销售去向活动（pick_dest='sale' → plot_id 恒 NULL）
        when(baseMapper.aggregateByMonth(month)).thenReturn(List.of());
        PerfActivityAggRow act = new PerfActivityAggRow();
        act.setCropId(10L);
        act.setPlotId(null);
        act.setPickWeight(new BigDecimal("100.000"));
        when(baseMapper.selectActivityAggByMonth(month)).thenReturn(List.of(act));
        // 活动自身直接指定 2 个班组（销售去向没有地块，只能走这条）
        PlotCropTeamRow d1 = new PlotCropTeamRow();
        d1.setPlotId(null);
        d1.setCropId(10L);
        d1.setTeamId(1L);
        PlotCropTeamRow d2 = new PlotCropTeamRow();
        d2.setPlotId(null);
        d2.setCropId(10L);
        d2.setTeamId(2L);
        when(baseMapper.selectActivityDirectTeamsByMonth(month)).thenReturn(List.of(d1, d2));
        when(baseMapper.selectCropUnitPrices(anyCollection())).thenReturn(List.of(
            Map.of("cropId", 10L, "pickUnitPrice", new BigDecimal("1.00"))
        ));
        when(baseMapper.update(eq(null), any())).thenReturn(0);
        when(baseMapper.insert(any(PlantWorkPerformance.class))).thenReturn(1);

        int rows = service.generate(month);

        // 核心断言：plotIds 为空时绝不能调 selectHarvestTeamsByPlots（它用 foreach 拼 IN，空集合会拼出 `IN ()` 让整个结算 500）
        verify(baseMapper, never()).selectHarvestTeamsByPlots(anyCollection());
        assertThat(rows).as("100kg 由 2 个直接班组各摊 50").isEqualTo(2);
        ArgumentCaptor<PlantWorkPerformance> captor = ArgumentCaptor.forClass(PlantWorkPerformance.class);
        verify(baseMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(r -> {
            assertThat(r.getPickWeight()).isEqualByComparingTo("50.000");
            assertThat(r.getPerformanceAmount()).isEqualByComparingTo("50.00");
        });
    }

    @Test
    @DisplayName("generate: 采摘活动量按地块采收班组平摊并入（row12：多班组各 1/N）")
    void testGenerate_ActivitySharesSplitAcrossTeams() {
        String month = "2026-04";
        // 过磅聚合：班组1×作物10 = 100.000 公斤
        when(baseMapper.aggregateByMonth(month)).thenReturn(List.of(agg(1L, 10L, "100.000")));
        // 活动聚合：作物10×地块7 = 10.000 公斤 → 地块7采收班组 {1,2} → 各摊 5.000
        PerfActivityAggRow act = new PerfActivityAggRow();
        act.setCropId(10L);
        act.setPlotId(7L);
        act.setPickWeight(new BigDecimal("10.000"));
        when(baseMapper.selectActivityAggByMonth(month)).thenReturn(List.of(act));
        PlotCropTeamRow t1 = new PlotCropTeamRow();
        t1.setPlotId(7L);
        t1.setCropId(10L);
        t1.setTeamId(1L);
        PlotCropTeamRow t2 = new PlotCropTeamRow();
        t2.setPlotId(7L);
        t2.setCropId(10L);
        t2.setTeamId(2L);
        when(baseMapper.selectHarvestTeamsByPlots(anyCollection())).thenReturn(List.of(t1, t2));
        when(baseMapper.selectCropUnitPrices(anyCollection())).thenReturn(List.of(
            Map.of("cropId", 10L, "pickUnitPrice", new BigDecimal("1.00"))
        ));
        when(baseMapper.update(eq(null), any())).thenReturn(0);
        when(baseMapper.insert(any(PlantWorkPerformance.class))).thenReturn(1);

        int rows = service.generate(month);

        assertThat(rows).as("班组1(过磅+摊) + 班组2(纯摊) → 2 行").isEqualTo(2);
        ArgumentCaptor<PlantWorkPerformance> captor = ArgumentCaptor.forClass(PlantWorkPerformance.class);
        verify(baseMapper, times(2)).insert(captor.capture());
        List<PlantWorkPerformance> inserted = captor.getAllValues();

        PlantWorkPerformance r1 = inserted.stream().filter(p -> p.getTeamId().equals(1L)).findFirst().orElseThrow();
        // 100.000 过磅 + 10/2=5.000 平摊 = 105.000 公斤 × 1.00 元/公斤 = 105.00
        assertThat(r1.getPickWeight()).isEqualByComparingTo("105.000");
        assertThat(r1.getPerformanceAmount()).isEqualByComparingTo("105.00");

        PlantWorkPerformance r2 = inserted.stream().filter(p -> p.getTeamId().equals(2L)).findFirst().orElseThrow();
        // 纯平摊 5.000 公斤 × 1.00 元/公斤 = 5.00
        assertThat(r2.getPickWeight()).isEqualByComparingTo("5.000");
        assertThat(r2.getPerformanceAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("generate: 作物无单价（null）→ 单价快照兜 0，金额 0，仍 INSERT")
    void testGenerate_CropWithoutPrice() {
        String month = "2026-05";
        when(baseMapper.aggregateByMonth(month)).thenReturn(List.of(agg(1L, 10L, "30.000")));
        // 作物 10 无单价行返回（pick_unit_price 为 null 时该 crop 不在结果或值为 null）
        when(baseMapper.selectCropUnitPrices(anyCollection())).thenReturn(List.of());
        when(baseMapper.insert(any(PlantWorkPerformance.class))).thenReturn(1);

        int rows = service.generate(month);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<PlantWorkPerformance> captor = ArgumentCaptor.forClass(PlantWorkPerformance.class);
        verify(baseMapper).insert(captor.capture());
        assertThat(captor.getValue().getUnitPriceSnapshot()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getPerformanceAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("generate: 该月无可聚合采摘数据 → 仍软删旧行但 0 INSERT")
    void testGenerate_EmptyAggregation() {
        String month = "2026-06";
        when(baseMapper.aggregateByMonth(month)).thenReturn(List.of());

        int rows = service.generate(month);

        assertThat(rows).isZero();
        verify(baseMapper, times(1)).update(eq(null), any());
        verify(baseMapper, never()).insert(any(PlantWorkPerformance.class));
    }

    @Test
    @DisplayName("generate: 月份格式非法 → 抛 ServiceException，无任何 DB 访问")
    void testGenerate_InvalidMonth() {
        assertThatThrownBy(() -> service.generate("2026/04"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("yyyy-MM");

        assertThatThrownBy(() -> service.generate("2026-4"))
            .isInstanceOf(ServiceException.class);

        verify(baseMapper, never()).aggregateByMonth(anyString());
        verify(baseMapper, never()).update(any(), any());
    }
}
