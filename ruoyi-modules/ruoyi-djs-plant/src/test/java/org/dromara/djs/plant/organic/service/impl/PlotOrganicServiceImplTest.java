package org.dromara.djs.plant.organic.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.plant.organic.domain.OrganicPlotno;
import org.dromara.djs.plant.organic.domain.PlotOrganic;
import org.dromara.djs.plant.organic.domain.bo.PlotOrganicBo;
import org.dromara.djs.plant.organic.domain.vo.PlotOrganicVo;
import org.dromara.djs.plant.organic.mapper.OrganicPlotnoMapper;
import org.dromara.djs.plant.organic.mapper.PlotOrganicMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
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

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlotOrganicServiceImpl} 单测（PLT-MD-003）。
 *
 * <p>4 case 覆盖：</p>
 * <ol>
 *   <li>insertByBo happy path：默认 isWarning=2 + 关联 3 地块批量 INSERT</li>
 *   <li>updateByBo 编辑关联：旧关联软删 + 新关联批量 INSERT</li>
 *   <li>insertByBo 关联地块不存在 → 抛 ServiceException</li>
 *   <li>queryPageList enrich relatedPlots：联表反查 PlotInfo.plot_name</li>
 * </ol>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlotOrganicServiceImpl 单元测试")
class PlotOrganicServiceImplTest {

    @Mock
    private PlotOrganicMapper plotOrganicMapper;

    @Mock
    private OrganicPlotnoMapper plotnoMapper;

    @Mock
    private PlotInfoMapper plotInfoMapper;

    private TestablePlotOrganicServiceImpl service;

    static class TestablePlotOrganicServiceImpl extends PlotOrganicServiceImpl {
        TestablePlotOrganicServiceImpl(PlotOrganicMapper baseMapper,
                                      OrganicPlotnoMapper plotnoMapper,
                                      PlotInfoMapper plotInfoMapper) {
            super(baseMapper, plotnoMapper, plotInfoMapper);
        }

        @Override
        protected PlotOrganic toEntity(PlotOrganicBo bo) {
            if (bo == null) return null;
            PlotOrganic e = new PlotOrganic();
            e.setId(bo.getId());
            e.setOrganicNo(bo.getOrganicNo());
            e.setOrganicCompany(bo.getOrganicCompany());
            e.setOrganicValid(bo.getOrganicValid());
            e.setOrganicImagePreview(bo.getOrganicImagePreview());
            e.setOrganicImageUrl(bo.getOrganicImageUrl());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestablePlotOrganicServiceImpl(plotOrganicMapper, plotnoMapper, plotInfoMapper);
    }

    private PlotOrganicBo sampleBo() {
        PlotOrganicBo bo = new PlotOrganicBo();
        bo.setOrganicNo("GB-2026-001");
        bo.setOrganicCompany("南京国环");
        bo.setOrganicValid(LocalDate.now().plusYears(2));
        bo.setOrganicImagePreview("oss-thumb-1");
        bo.setOrganicImageUrl("oss-img-1,oss-img-2");
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → 默认 isWarning=2 + 批量 INSERT 3 个关联")
    void testInsertByBo_HappyPath() {
        PlotOrganicBo bo = sampleBo();
        bo.setPlotIds(List.of(80001L, 80002L, 80003L));

        // 三个地块都存在
        PlotInfo p1 = new PlotInfo(); p1.setId(80001L);
        PlotInfo p2 = new PlotInfo(); p2.setId(80002L);
        PlotInfo p3 = new PlotInfo(); p3.setId(80003L);
        when(plotInfoMapper.selectByIds(any(Collection.class))).thenReturn(List.of(p1, p2, p3));

        when(plotOrganicMapper.insert(any(PlotOrganic.class))).thenAnswer(inv -> {
            PlotOrganic e = inv.getArgument(0);
            e.setId(90001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<PlotOrganic> captor = ArgumentCaptor.forClass(PlotOrganic.class);
        verify(plotOrganicMapper).insert(captor.capture());
        assertThat(captor.getValue().getIsWarning()).as("默认 isWarning=2 正常").isEqualTo(2);

        // 软删（即使没旧关联也跑一次 update wrapper）
        verify(plotnoMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
        // 批量插入 3 条
        ArgumentCaptor<List<OrganicPlotno>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(plotnoMapper).insertBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(3);
        assertThat(batchCaptor.getValue()).allMatch(r -> r.getOrganicId().equals(90001L));
    }

    @Test
    @DisplayName("updateByBo: 编辑关联 → 软删旧 + 插入新 + organic_no 锁回旧值")
    void testUpdateByBo_RelationRewrite() {
        PlotOrganic exists = new PlotOrganic();
        exists.setId(90001L);
        exists.setOrganicNo("GB-2026-001");
        when(plotOrganicMapper.selectById(90001L)).thenReturn(exists);
        when(plotOrganicMapper.updateById(any(PlotOrganic.class))).thenReturn(1);

        PlotInfo p4 = new PlotInfo(); p4.setId(80004L);
        PlotInfo p5 = new PlotInfo(); p5.setId(80005L);
        when(plotInfoMapper.selectByIds(any(Collection.class))).thenReturn(List.of(p4, p5));

        PlotOrganicBo bo = sampleBo();
        bo.setId(90001L);
        bo.setOrganicNo("GB-2026-999");  // 试图篡改编号
        bo.setPlotIds(List.of(80004L, 80005L));  // 新关联 2 块

        int rows = service.updateByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<PlotOrganic> captor = ArgumentCaptor.forClass(PlotOrganic.class);
        verify(plotOrganicMapper).updateById(captor.capture());
        assertThat(captor.getValue().getOrganicNo()).as("organic_no 强制锁回").isEqualTo("GB-2026-001");

        // 软删旧关联
        verify(plotnoMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
        // 插入新关联（2 条）
        ArgumentCaptor<List<OrganicPlotno>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(plotnoMapper).insertBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("insertByBo: 关联地块包含无效 ID → 抛 ServiceException，不写主表")
    void testInsertByBo_InvalidPlotId() {
        PlotOrganicBo bo = sampleBo();
        bo.setPlotIds(List.of(80001L, 99999L));  // 99999 不存在

        // 只返 1 个，校验 size != distinct.size 应抛
        PlotInfo p1 = new PlotInfo(); p1.setId(80001L);
        when(plotInfoMapper.selectByIds(any(Collection.class))).thenReturn(List.of(p1));

        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("关联地块包含不存在");

        verify(plotOrganicMapper, never()).insert(any(PlotOrganic.class));
        verify(plotnoMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("queryPageList: enrich relatedPlots → 反查 plot_name")
    void testQueryPageList_EnrichRelatedPlots() {
        PlotOrganicVo vo = new PlotOrganicVo();
        vo.setId(90001L);
        vo.setOrganicNo("GB-2026-001");
        Page<PlotOrganicVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(plotOrganicMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        // 该证书关联 2 个地块
        OrganicPlotno r1 = new OrganicPlotno();
        r1.setOrganicId(90001L); r1.setPlotId(80001L);
        OrganicPlotno r2 = new OrganicPlotno();
        r2.setOrganicId(90001L); r2.setPlotId(80002L);
        when(plotnoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r1, r2));

        PlotInfo p1 = new PlotInfo(); p1.setId(80001L); p1.setPlotName("A 区 1 号");
        PlotInfo p2 = new PlotInfo(); p2.setId(80002L); p2.setPlotName("A 区 2 号");
        when(plotInfoMapper.selectByIds(any(Collection.class))).thenReturn(List.of(p1, p2));

        var result = service.queryPageList(null, new org.dromara.common.mybatis.core.page.PageQuery(1, 10));

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getRelatedPlots()).hasSize(2);
        assertThat(result.getRows().get(0).getRelatedPlots())
            .extracting(p -> p.getPlotName())
            .containsExactlyInAnyOrder("A 区 1 号", "A 区 2 号");
    }
}
