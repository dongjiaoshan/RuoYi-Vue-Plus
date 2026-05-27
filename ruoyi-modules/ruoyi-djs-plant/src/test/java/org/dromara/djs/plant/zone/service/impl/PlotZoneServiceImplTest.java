package org.dromara.djs.plant.zone.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.zone.domain.PlotZone;
import org.dromara.djs.plant.zone.domain.bo.PlotZoneBo;
import org.dromara.djs.plant.zone.domain.query.PlotZoneQuery;
import org.dromara.djs.plant.zone.domain.vo.PlotZoneVo;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlotZoneServiceImpl} 单测（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlotZoneServiceImpl 单元测试")
class PlotZoneServiceImplTest {

    @Mock
    private PlotZoneMapper plotZoneMapper;

    @Mock
    private PlotInfoMapper plotInfoMapper;

    private TestablePlotZoneServiceImpl service;

    /** 覆盖 toEntity 钩子，避开 MapStruct-Plus 容器。 */
    static class TestablePlotZoneServiceImpl extends PlotZoneServiceImpl {
        TestablePlotZoneServiceImpl(PlotZoneMapper baseMapper, PlotInfoMapper plotInfoMapper) {
            super(baseMapper, plotInfoMapper);
        }

        @Override
        protected PlotZone toEntity(PlotZoneBo bo) {
            if (bo == null) return null;
            PlotZone e = new PlotZone();
            e.setId(bo.getId());
            e.setZoneCode(bo.getZoneCode());
            e.setZoneName(bo.getZoneName());
            e.setZoneDesc(bo.getZoneDesc());
            e.setZoneBelong(bo.getZoneBelong());
            e.setZoneStatus(bo.getZoneStatus());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestablePlotZoneServiceImpl(plotZoneMapper, plotInfoMapper);
    }

    private PlotZoneBo sampleBo() {
        PlotZoneBo bo = new PlotZoneBo();
        bo.setZoneCode("Z001");
        bo.setZoneName("A 区");
        bo.setZoneDesc("东部温室区");
        bo.setZoneBelong("东部");
        bo.setZoneStatus(1);
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → zoneStatus 默认 1 兜底")
    void testInsertByBo_HappyPath() {
        PlotZoneBo bo = sampleBo();
        bo.setZoneStatus(null);
        when(plotZoneMapper.insert(any(PlotZone.class))).thenAnswer(inv -> {
            PlotZone e = inv.getArgument(0);
            e.setId(70001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<PlotZone> captor = ArgumentCaptor.forClass(PlotZone.class);
        verify(plotZoneMapper, times(1)).insert(captor.capture());
        PlotZone saved = captor.getValue();
        assertThat(saved.getZoneCode()).isEqualTo("Z001");
        assertThat(saved.getZoneName()).isEqualTo("A 区");
        assertThat(saved.getZoneStatus()).as("null 时兜底 1").isEqualTo(1);
    }

    @Test
    @DisplayName("queryPageList: 走 mapper.selectVoPage 返 TableDataInfo")
    void testQueryPageList() {
        PlotZoneQuery query = new PlotZoneQuery();
        query.setZoneName("A");
        PageQuery pageQuery = new PageQuery(1, 10);

        PlotZoneVo vo = new PlotZoneVo();
        vo.setId(70001L);
        vo.setZoneCode("Z001");
        vo.setZoneName("A 区");
        Page<PlotZoneVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(plotZoneMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        TableDataInfo<PlotZoneVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getZoneCode()).isEqualTo("Z001");
    }

    @Test
    @DisplayName("updateByBo: happy path → zoneCode 强制锁回旧值")
    void testUpdateByBo_HappyPath() {
        PlotZone existing = new PlotZone();
        existing.setId(70001L);
        existing.setZoneCode("Z001");
        when(plotZoneMapper.selectById(70001L)).thenReturn(existing);
        when(plotZoneMapper.updateById(any(PlotZone.class))).thenReturn(1);

        PlotZoneBo bo = sampleBo();
        bo.setId(70001L);
        bo.setZoneCode("Z9999");  // 尝试改编码
        bo.setZoneName("A 区（改）");

        int rows = service.updateByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<PlotZone> captor = ArgumentCaptor.forClass(PlotZone.class);
        verify(plotZoneMapper).updateById(captor.capture());
        assertThat(captor.getValue().getZoneName()).isEqualTo("A 区（改）");
        assertThat(captor.getValue().getZoneCode()).as("编辑端点不允许改 zoneCode").isEqualTo("Z001");
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → 抛 ServiceException")
    void testUpdateByBo_NullId() {
        PlotZoneBo bo = sampleBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ID 不能为空");
    }

    @Test
    @DisplayName("deleteWithValidByIds: 无关联地块 happy → 走基类 softDelete")
    void testDeleteWithValidByIds_HappyPath() {
        when(plotInfoMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(plotZoneMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(70001L, 70002L));

        assertThat(rows).isEqualTo(2);
        verify(plotZoneMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("deleteWithValidByIds: 片区仍有地块 → 抛 plant.zone.has_plot，不走 softDelete")
    void testDeleteWithValidByIds_HasPlot() {
        when(plotInfoMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        PlotZone zone = new PlotZone();
        zone.setId(70001L);
        zone.setZoneName("A 区");
        when(plotZoneMapper.selectById(70001L)).thenReturn(zone);

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(70001L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("A 区")
            .hasMessageContaining("不允许删除");

        verify(plotZoneMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合 → 0 短路")
    void testDeleteWithValidByIds_EmptyShortCircuit() {
        int rows = service.deleteWithValidByIds(List.of());
        assertThat(rows).isZero();
        verify(plotInfoMapper, never()).selectCount(any(LambdaQueryWrapper.class));
    }
}
