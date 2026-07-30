package org.dromara.djs.breed.core.service.impl;

import org.dromara.common.core.service.DictService;
import org.dromara.djs.breed.breeding.mapper.BreedInfoMapper;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.SowPerformanceMpVo;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.mapper.SowDetailAggMapper;
import org.dromara.djs.breed.production.mapper.SowPerformanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@DisplayName("SowDetailServiceImpl 实时生产指标测试")
class SowDetailServiceImplTest {

    @Mock
    private SowDetailAggMapper aggMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private DictService dictService;
    @Mock
    private BreedInfoMapper breedInfoMapper;
    @Mock
    private SowPerformanceMapper sowPerformanceMapper;

    private SowDetailServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SowDetailServiceImpl(aggMapper, pigMapper, dictService, breedInfoMapper, sowPerformanceMapper);
    }

    @Test
    @DisplayName("汇总表缺行时实时聚合累计产仔、活仔、断奶与平均重量")
    void querySowPerformance_falls_back_to_complete_live_totals() {
        Pig sow = new Pig();
        sow.setId(91L);
        sow.setPigType("sow");
        when(pigMapper.selectById(91L)).thenReturn(sow);
        when(sowPerformanceMapper.selectLatestByPigId(org.mockito.ArgumentMatchers.eq(91L), anyString())).thenReturn(null);

        SowPerformanceMpVo farrowTotals = new SowPerformanceMpVo();
        farrowTotals.setTotalBorn(12);
        farrowTotals.setTotalLiveBorn(10);
        farrowTotals.setAvgBornWeight(new BigDecimal("1.250"));
        farrowTotals.setAvgLiveBornPerLitter(new BigDecimal("10.000"));
        when(aggMapper.farrowProductionTotals(91L)).thenReturn(farrowTotals);

        SowPerformanceMpVo weanTotals = new SowPerformanceMpVo();
        weanTotals.setTotalWeaned(9);
        weanTotals.setAvgWeanedWeight(new BigDecimal("7.500"));
        when(aggMapper.weanProductionTotals(91L)).thenReturn(weanTotals);

        SowPerformanceMpVo result = service.querySowPerformance(91L);

        assertThat(result.getTotalBorn()).isEqualTo(12);
        assertThat(result.getTotalLiveBorn()).isEqualTo(10);
        assertThat(result.getTotalWeaned()).isEqualTo(9);
        assertThat(result.getAvgBornWeight()).isEqualByComparingTo("1.250");
        assertThat(result.getAvgWeanedWeight()).isEqualByComparingTo("7.500");
        assertThat(result.getAvgLiveBornPerLitter()).isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("已有分娩但尚无断奶记录时累计断奶为零，平均断奶重保持空")
    void querySowPerformance_without_weaning_returns_zero_total() {
        Pig sow = new Pig();
        sow.setId(92L);
        sow.setPigType("sow");
        when(pigMapper.selectById(92L)).thenReturn(sow);
        when(sowPerformanceMapper.selectLatestByPigId(org.mockito.ArgumentMatchers.eq(92L), anyString())).thenReturn(null);

        SowPerformanceMpVo farrowTotals = new SowPerformanceMpVo();
        farrowTotals.setTotalBorn(8);
        farrowTotals.setTotalLiveBorn(7);
        when(aggMapper.farrowProductionTotals(92L)).thenReturn(farrowTotals);

        // Mapper 的 COALESCE(SUM(weaned_count), 0) 在无断奶行时返回 0；
        // 平均重量没有样本，仍为 null。
        SowPerformanceMpVo weanTotals = new SowPerformanceMpVo();
        weanTotals.setTotalWeaned(0);
        when(aggMapper.weanProductionTotals(92L)).thenReturn(weanTotals);

        SowPerformanceMpVo result = service.querySowPerformance(92L);

        assertThat(result.getTotalBorn()).isEqualTo(8);
        assertThat(result.getTotalLiveBorn()).isEqualTo(7);
        assertThat(result.getTotalWeaned()).isZero();
        assertThat(result.getAvgWeanedWeight()).isNull();
    }
}
