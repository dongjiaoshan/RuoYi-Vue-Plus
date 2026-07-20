package org.dromara.djs.plant.plot.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.domain.bo.PlotInfoBo;
import org.dromara.djs.plant.plot.domain.query.PlotInfoQuery;
import org.dromara.djs.plant.plot.domain.vo.PlotInfoVo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.zone.domain.PlotZone;
import org.dromara.djs.plant.zone.mapper.PlotZoneMapper;
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
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlotInfoServiceImpl} 单测（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlotInfoServiceImpl 单元测试")
class PlotInfoServiceImplTest {

    @Mock
    private PlotInfoMapper plotInfoMapper;

    @Mock
    private PlotZoneMapper plotZoneMapper;

    private TestablePlotInfoServiceImpl service;

    static class TestablePlotInfoServiceImpl extends PlotInfoServiceImpl {
        TestablePlotInfoServiceImpl(PlotInfoMapper baseMapper, PlotZoneMapper plotZoneMapper) {
            super(baseMapper, plotZoneMapper);
        }

        @Override
        protected PlotInfo toEntity(PlotInfoBo bo) {
            if (bo == null) return null;
            PlotInfo e = new PlotInfo();
            e.setId(bo.getId());
            e.setPlotCode(bo.getPlotCode());
            e.setPlotName(bo.getPlotName());
            e.setZoneId(bo.getZoneId());
            e.setPlotType(bo.getPlotType());
            e.setPlotStatus(bo.getPlotStatus());
            e.setIsLease(bo.getIsLease());
            e.setPlotArea(bo.getPlotArea());
            e.setPlotImagePreview(bo.getPlotImagePreview());
            e.setPlotImageUrl(bo.getPlotImageUrl());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestablePlotInfoServiceImpl(plotInfoMapper, plotZoneMapper);
    }

    private PlotInfoBo sampleBo() {
        PlotInfoBo bo = new PlotInfoBo();
        bo.setPlotCode("P001");
        bo.setPlotName("A 区 1 号地块");
        bo.setZoneId(70001L);
        bo.setPlotType("open");
        bo.setPlotStatus(1);
        bo.setIsLease(0);
        bo.setPlotArea(new BigDecimal("5.50"));
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → 默认值兜底（status=1, isLease=0）+ zone 校验通过")
    void testInsertByBo_HappyPath() {
        PlotInfoBo bo = sampleBo();
        bo.setPlotStatus(null);
        bo.setIsLease(null);
        PlotZone zone = new PlotZone();
        zone.setId(70001L);
        zone.setZoneName("A 区");
        when(plotZoneMapper.selectById(70001L)).thenReturn(zone);
        when(plotInfoMapper.insert(any(PlotInfo.class))).thenAnswer(inv -> {
            PlotInfo e = inv.getArgument(0);
            e.setId(80001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<PlotInfo> captor = ArgumentCaptor.forClass(PlotInfo.class);
        verify(plotInfoMapper, times(1)).insert(captor.capture());
        PlotInfo saved = captor.getValue();
        assertThat(saved.getPlotStatus()).as("默认 1").isEqualTo(1);
        assertThat(saved.getIsLease()).as("默认 0").isEqualTo(0);
        assertThat(saved.getZoneId()).isEqualTo(70001L);
    }

    @Test
    @DisplayName("insertByBo: zoneId 不存在 → 抛 ServiceException")
    void testInsertByBo_ZoneNotExist() {
        PlotInfoBo bo = sampleBo();
        when(plotZoneMapper.selectById(70001L)).thenReturn(null);

        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("所属片区不存在");

        verify(plotInfoMapper, never()).insert(any(PlotInfo.class));
    }

    @Test
    @DisplayName("queryPageList: VO enrich zoneName")
    void testQueryPageList_EnrichZoneName() {
        PlotInfoQuery query = new PlotInfoQuery();
        query.setZoneId(70001L);
        PageQuery pageQuery = new PageQuery(1, 10);

        PlotInfoVo vo = new PlotInfoVo();
        vo.setId(80001L);
        vo.setPlotCode("P001");
        vo.setZoneId(70001L);
        Page<PlotInfoVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(plotInfoMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        PlotZone zone = new PlotZone();
        zone.setId(70001L);
        zone.setZoneName("A 区");
        when(plotZoneMapper.selectByIds(any(Collection.class))).thenReturn(List.of(zone));

        TableDataInfo<PlotInfoVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getZoneName()).isEqualTo("A 区");
    }

    @Test
    @DisplayName("updateByBo: plot_code 强制锁回旧值")
    void testUpdateByBo_LockCode() {
        PlotInfo existing = new PlotInfo();
        existing.setId(80001L);
        existing.setPlotCode("P001");
        existing.setZoneId(70001L);
        when(plotInfoMapper.selectById(80001L)).thenReturn(existing);
        when(plotInfoMapper.updateById(any(PlotInfo.class))).thenReturn(1);

        PlotInfoBo bo = sampleBo();
        bo.setId(80001L);
        bo.setPlotCode("P9999");
        bo.setPlotName("A 区 1 号地块（改）");

        int rows = service.updateByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<PlotInfo> captor = ArgumentCaptor.forClass(PlotInfo.class);
        verify(plotInfoMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPlotCode()).isEqualTo("P001");
        assertThat(captor.getValue().getPlotName()).isEqualTo("A 区 1 号地块（改）");
    }

    @Test
    @DisplayName("deleteWithValidByIds: D8 阶段 stub → 直接走基类 softDelete")
    void testDeleteWithValidByIds_HappyPath() {
        when(plotInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(80001L, 80002L));

        assertThat(rows).isEqualTo(2);
        verify(plotInfoMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("queryById: D8 阶段 currentCropName 返回 null（t_plant_plant_details 0 行）")
    void testQueryById_CurrentCropNameNull() {
        PlotInfoVo vo = new PlotInfoVo();
        vo.setId(80001L);
        vo.setZoneId(70001L);
        when(plotInfoMapper.selectVoById(80001L)).thenReturn(vo);

        PlotZone zone = new PlotZone();
        zone.setId(70001L);
        zone.setZoneName("A 区");
        when(plotZoneMapper.selectById(70001L)).thenReturn(zone);

        PlotInfoVo result = service.queryById(80001L);

        assertThat(result.getZoneName()).isEqualTo("A 区");
        assertThat(result.getCurrentCropName()).as("D8 阶段无种植明细，返 null").isNull();
    }
}
