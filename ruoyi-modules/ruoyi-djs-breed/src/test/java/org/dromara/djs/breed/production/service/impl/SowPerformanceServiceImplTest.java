package org.dromara.djs.breed.production.service.impl;

import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.SowPerformanceMpVo;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.mapper.SowDetailAggMapper;
import org.dromara.djs.breed.core.service.ISowDetailService;
import org.dromara.djs.breed.production.domain.SowPerformance;
import org.dromara.djs.breed.production.domain.vo.SowPerformanceVo;
import org.dromara.djs.breed.production.mapper.SowPerformanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@DisplayName("SowPerformanceServiceImpl admin 实时兜底测试")
class SowPerformanceServiceImplTest {

    @Mock
    private SowPerformanceMapper sowPerformanceMapper;
    @Mock
    private SowDetailAggMapper aggMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private ISowDetailService sowDetailService;

    private SowPerformanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SowPerformanceServiceImpl(sowPerformanceMapper, aggMapper, pigMapper, sowDetailService);
    }

    @Test
    @DisplayName("汇总表缺行但已有分娩时返回实时指标，不写汇总表")
    void listByPigId_returns_live_fallback_when_farrow_exists() {
        when(sowPerformanceMapper.selectVoList(any())).thenReturn(List.of());
        when(aggMapper.countFarrowRecords(91L)).thenReturn(1);
        Pig sow = new Pig();
        sow.setId(91L);
        sow.setEarNo("260324-001");
        sow.setPigType("sow");
        sow.setParity(1);
        when(pigMapper.selectById(91L)).thenReturn(sow);

        SowPerformanceMpVo live = new SowPerformanceMpVo();
        live.setTotalBorn(12);
        live.setTotalLiveBorn(10);
        live.setAvgBornWeight(new BigDecimal("1.250"));
        when(sowDetailService.querySowPerformance(91L)).thenReturn(live);

        List<SowPerformanceVo> result = service.listByPigId(91L);

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.getPigId()).isEqualTo(91L);
            assertThat(row.getEarNo()).isEqualTo("260324-001");
            assertThat(row.getParity()).isEqualTo(1);
            assertThat(row.getTotalBorn()).isEqualTo(12);
            assertThat(row.getTotalLiveBorn()).isEqualTo(10);
            assertThat(row.getAvgBornWeight()).isEqualByComparingTo("1.250");
        });
        verify(sowPerformanceMapper, never()).insert(any(SowPerformance.class));
        verify(sowPerformanceMapper, never()).updateById(any(SowPerformance.class));
    }

    @Test
    @DisplayName("汇总表缺行且没有分娩时保持空列表")
    void listByPigId_stays_empty_without_farrow() {
        when(sowPerformanceMapper.selectVoList(any())).thenReturn(List.of());
        when(aggMapper.countFarrowRecords(92L)).thenReturn(0);

        assertThat(service.listByPigId(92L)).isEmpty();

        verify(sowDetailService, never()).querySowPerformance(any());
    }
}
