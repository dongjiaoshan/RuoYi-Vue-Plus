package org.dromara.djs.plant.crop.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.domain.bo.CropInfoBo;
import org.dromara.djs.plant.crop.domain.query.CropInfoQuery;
import org.dromara.djs.plant.crop.domain.vo.CropInfoVo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.common.image.service.IImageLibraryService;
import org.dromara.djs.common.image.service.ImageUrlResolver;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CropInfoServiceImpl} 单测（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CropInfoServiceImpl 单元测试")
class CropInfoServiceImplTest {

    @Mock
    private CropInfoMapper cropInfoMapper;

    @Mock
    private IImageLibraryService imageLibraryService;

    @Mock
    private ImageUrlResolver imageUrlResolver;

    private TestableCropInfoServiceImpl service;

    static class TestableCropInfoServiceImpl extends CropInfoServiceImpl {
        TestableCropInfoServiceImpl(CropInfoMapper baseMapper,
                                    IImageLibraryService imageLibraryService,
                                    ImageUrlResolver imageUrlResolver) {
            super(baseMapper, imageLibraryService, imageUrlResolver);
        }

        @Override
        protected CropInfo toEntity(CropInfoBo bo) {
            if (bo == null) return null;
            CropInfo e = new CropInfo();
            e.setId(bo.getId());
            e.setCropCode(bo.getCropCode());
            e.setCropName(bo.getCropName());
            e.setVarietyName(bo.getVarietyName());
            e.setCropFamily(bo.getCropFamily());
            e.setRelatedProduct(bo.getRelatedProduct());
            e.setPlantingSeason(bo.getPlantingSeason());
            e.setMaxCycle(bo.getMaxCycle());
            e.setMinCycle(bo.getMinCycle());
            e.setPredictedPer(bo.getPredictedPer());
            e.setPickUnitPrice(bo.getPickUnitPrice());
            e.setCropImagePreview(bo.getCropImagePreview());
            e.setCropImageUrl(bo.getCropImageUrl());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableCropInfoServiceImpl(cropInfoMapper, imageLibraryService, imageUrlResolver);
    }

    private CropInfoBo sampleBo() {
        CropInfoBo bo = new CropInfoBo();
        bo.setCropCode("C001");
        bo.setCropName("白菜");
        bo.setVarietyName("京白菜 4 号");
        bo.setCropFamily("leafy");
        bo.setPlantingSeason("spring,autumn");
        bo.setMaxCycle(120);
        bo.setMinCycle(60);
        bo.setPredictedPer(new BigDecimal("3500.000"));
        bo.setPickUnitPrice(new BigDecimal("3.50"));
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → mapper.insert 调一次")
    void testInsertByBo_HappyPath() {
        CropInfoBo bo = sampleBo();
        when(cropInfoMapper.insert(any(CropInfo.class))).thenAnswer(inv -> {
            CropInfo e = inv.getArgument(0);
            e.setId(90001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<CropInfo> captor = ArgumentCaptor.forClass(CropInfo.class);
        verify(cropInfoMapper, times(1)).insert(captor.capture());
        CropInfo saved = captor.getValue();
        assertThat(saved.getCropCode()).isEqualTo("C001");
        assertThat(saved.getCropName()).isEqualTo("白菜");
        assertThat(saved.getPlantingSeason()).isEqualTo("spring,autumn");
        assertThat(saved.getMaxCycle()).isEqualTo(120);
    }

    @Test
    @DisplayName("queryPageList: 走 mapper.selectVoPage 返 TableDataInfo")
    void testQueryPageList() {
        CropInfoQuery query = new CropInfoQuery();
        query.setCropName("白");
        PageQuery pageQuery = new PageQuery(1, 10);

        CropInfoVo vo = new CropInfoVo();
        vo.setId(90001L);
        vo.setCropCode("C001");
        vo.setCropName("白菜");
        Page<CropInfoVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(cropInfoMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        TableDataInfo<CropInfoVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getCropCode()).isEqualTo("C001");
    }

    @Test
    @DisplayName("updateByBo: cropCode 强制锁回旧值")
    void testUpdateByBo_LockCode() {
        CropInfo existing = new CropInfo();
        existing.setId(90001L);
        existing.setCropCode("C001");
        when(cropInfoMapper.selectById(90001L)).thenReturn(existing);
        when(cropInfoMapper.updateById(any(CropInfo.class))).thenReturn(1);

        CropInfoBo bo = sampleBo();
        bo.setId(90001L);
        bo.setCropCode("C9999");
        bo.setCropName("白菜（改）");

        int rows = service.updateByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<CropInfo> captor = ArgumentCaptor.forClass(CropInfo.class);
        verify(cropInfoMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCropCode()).isEqualTo("C001");
    }

    @Test
    @DisplayName("updateByBo: id 不存在 → 抛 ServiceException")
    void testUpdateByBo_NotExist() {
        when(cropInfoMapper.selectById(90001L)).thenReturn(null);

        CropInfoBo bo = sampleBo();
        bo.setId(90001L);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在或已删除");
    }

    @Test
    @DisplayName("deleteWithValidByIds: D8 阶段 stub → 走基类 softDelete")
    void testDeleteWithValidByIds_HappyPath() {
        when(cropInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(90001L, 90002L));

        assertThat(rows).isEqualTo(2);
        verify(cropInfoMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合短路")
    void testDeleteWithValidByIds_Empty() {
        int rows = service.deleteWithValidByIds(List.of());
        assertThat(rows).isZero();
        verify(cropInfoMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }
}
