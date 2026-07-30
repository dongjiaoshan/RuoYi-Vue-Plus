package org.dromara.djs.plant.cropstat.service.impl;

import org.dromara.djs.plant.cropstat.domain.vo.CropPlotStatVo;
import org.dromara.djs.plant.cropstat.mapper.CropPlotStatMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link CropPlotStatServiceImpl} 单测。
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CropPlotStatServiceImpl 单元测试")
class CropPlotStatServiceImplTest {

    @Mock
    private CropPlotStatMapper cropPlotStatMapper;

    private CropPlotStatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CropPlotStatServiceImpl(cropPlotStatMapper);
    }

    @Test
    @DisplayName("happy path：原样透传聚合行（作物 / 关联产品 / 地块数 / 预计产量 / 采摘日期区间）")
    void listPlotStatReturnsAggregatedRows() {
        CropPlotStatVo row = new CropPlotStatVo();
        row.setCropId(9306000000000084L);
        row.setCropName("糯玉米");
        row.setRelatedProduct(9303000000000123L);
        row.setRemainPlotCount(4);
        row.setExpectYield(new BigDecimal("1600.000"));
        row.setEarliestPickDate("2026-07-22");
        row.setLatestPickDate("2026-07-26");
        when(cropPlotStatMapper.selectPlotStat()).thenReturn(List.of(row));

        List<CropPlotStatVo> result = service.listPlotStat();

        assertThat(result).hasSize(1);
        CropPlotStatVo vo = result.get(0);
        assertThat(vo.getCropId()).isEqualTo(9306000000000084L);
        assertThat(vo.getCropName()).isEqualTo("糯玉米");
        assertThat(vo.getRelatedProduct()).isEqualTo(9303000000000123L);
        assertThat(vo.getRemainPlotCount()).isEqualTo(4);
        assertThat(vo.getExpectYield()).isEqualByComparingTo("1600.000");
        assertThat(vo.getEarliestPickDate()).isEqualTo("2026-07-22");
        assertThat(vo.getLatestPickDate()).isEqualTo("2026-07-26");
    }

    @Test
    @DisplayName("空值归一：地块数 null → 0，预计产量 null → 0；日期保持 null 由前端显示占位")
    void listPlotStatNormalizesNulls() {
        CropPlotStatVo row = new CropPlotStatVo();
        row.setCropId(1L);
        row.setCropName("空数据作物");
        List<CropPlotStatVo> mutable = new ArrayList<>();
        mutable.add(row);
        when(cropPlotStatMapper.selectPlotStat()).thenReturn(mutable);

        List<CropPlotStatVo> result = service.listPlotStat();

        assertThat(result.get(0).getRemainPlotCount()).isZero();
        assertThat(result.get(0).getExpectYield()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.get(0).getEarliestPickDate()).isNull();
    }

    @Test
    @DisplayName("mapper 返 null / 空 → 返回空 list（前端不报错）")
    void listPlotStatHandlesEmpty() {
        when(cropPlotStatMapper.selectPlotStat()).thenReturn(null);
        assertThat(service.listPlotStat()).isEmpty();

        when(cropPlotStatMapper.selectPlotStat()).thenReturn(List.of());
        assertThat(service.listPlotStat()).isEmpty();
    }
}
