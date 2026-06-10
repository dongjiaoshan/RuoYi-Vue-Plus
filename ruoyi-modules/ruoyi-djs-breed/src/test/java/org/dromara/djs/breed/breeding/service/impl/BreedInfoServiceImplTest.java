package org.dromara.djs.breed.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.breeding.domain.BreedInfo;
import org.dromara.djs.breed.breeding.domain.bo.BreedInfoBo;
import org.dromara.djs.breed.breeding.domain.query.BreedInfoQuery;
import org.dromara.djs.breed.breeding.domain.vo.BreedInfoVo;
import org.dromara.djs.breed.breeding.mapper.BreedInfoMapper;
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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BreedInfoServiceImpl} 单测（BRD-MD-001）。
 *
 * <p>覆盖 happy path：insert → list → query by id → update → soft-delete；
 * 关键验证 deleteWithValidByIds 走基类 softDelete，{@code wrapper.getSqlSet()} 含
 * {@code del_flag = '1'}（D03 _open-issues #18 教训：仅断言 entity 字段会被 mock 假阳性骗）。</p>
 *
 * @author djs
 * @since BRD-MD-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BreedInfoServiceImpl 单元测试")
class BreedInfoServiceImplTest {

    @Mock
    private BreedInfoMapper breedInfoMapper;

    private TestableBreedInfoServiceImpl service;

    /**
     * 子类覆盖 toEntity 钩子，避开 SpringUtils.getBean(Converter.class)。
     */
    static class TestableBreedInfoServiceImpl extends BreedInfoServiceImpl {
        TestableBreedInfoServiceImpl(BreedInfoMapper mapper) {
            super(mapper);
        }

        @Override
        protected BreedInfo toEntity(BreedInfoBo bo) {
            if (bo == null) {
                return null;
            }
            BreedInfo e = new BreedInfo();
            e.setId(bo.getId());
            e.setBreedStrain(bo.getBreedStrain());
            e.setBreedStrainCode(bo.getBreedStrainCode());
            e.setBreedStrainName(bo.getBreedStrainName());
            e.setParentCode(bo.getParentCode());
            e.setDescription(bo.getDescription());
            e.setRemark(bo.getRemark());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableBreedInfoServiceImpl(breedInfoMapper);
    }

    private BreedInfoBo sampleBreedBo() {
        BreedInfoBo bo = new BreedInfoBo();
        bo.setBreedStrain(1);
        bo.setBreedStrainCode("04");
        bo.setBreedStrainName("杜洛克");
        bo.setDescription("产肉性能优");
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → mapper.insert 调一次")
    void testInsertByBo_HappyPath() {
        BreedInfoBo bo = sampleBreedBo();
        when(breedInfoMapper.insert(any(BreedInfo.class))).thenAnswer(inv -> {
            BreedInfo e = inv.getArgument(0);
            e.setId(30001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<BreedInfo> captor = ArgumentCaptor.forClass(BreedInfo.class);
        verify(breedInfoMapper, times(1)).insert(captor.capture());
        BreedInfo saved = captor.getValue();
        assertThat(saved.getBreedStrain()).isEqualTo(1);
        assertThat(saved.getBreedStrainCode()).isEqualTo("04");
        assertThat(saved.getBreedStrainName()).isEqualTo("杜洛克");
    }

    @Test
    @DisplayName("queryPageList: 走 mapper.selectVoPage 返回封装后的 TableDataInfo")
    void testQueryPageList() {
        BreedInfoQuery query = new BreedInfoQuery();
        query.setBreedStrain(1);
        PageQuery pageQuery = new PageQuery(1, 10);

        BreedInfoVo vo = new BreedInfoVo();
        vo.setId(30001L);
        vo.setBreedStrainCode("04");
        Page<BreedInfoVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(breedInfoMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        TableDataInfo<BreedInfoVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getBreedStrainCode()).isEqualTo("04");
    }

    @Test
    @DisplayName("queryById: 走 mapper.selectVoById")
    void testQueryById() {
        BreedInfoVo vo = new BreedInfoVo();
        vo.setId(30001L);
        vo.setBreedStrainCode("04");
        when(breedInfoMapper.selectVoById(30001L)).thenReturn(vo);

        BreedInfoVo got = service.queryById(30001L);
        assertThat(got).isNotNull();
        assertThat(got.getBreedStrainCode()).isEqualTo("04");
    }

    @Test
    @DisplayName("updateByBo: happy path → mapper.updateById 调一次")
    void testUpdateByBo_HappyPath() {
        BreedInfo existing = new BreedInfo();
        existing.setId(30001L);
        existing.setBreedStrainCode("04");
        when(breedInfoMapper.selectById(30001L)).thenReturn(existing);
        when(breedInfoMapper.updateById(any(BreedInfo.class))).thenReturn(1);

        BreedInfoBo bo = sampleBreedBo();
        bo.setId(30001L);
        bo.setBreedStrainName("杜洛克（改）");

        int rows = service.updateByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<BreedInfo> captor = ArgumentCaptor.forClass(BreedInfo.class);
        verify(breedInfoMapper).updateById(captor.capture());
        assertThat(captor.getValue().getBreedStrainName()).isEqualTo("杜洛克（改）");
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → 抛 ServiceException")
    void testUpdateByBo_NullId() {
        BreedInfoBo bo = sampleBreedBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ID 不能为空");
    }

    @Test
    @DisplayName("updateByBo: DB 查不到 → 抛 ServiceException")
    void testUpdateByBo_NotExist() {
        when(breedInfoMapper.selectById(99999L)).thenReturn(null);
        BreedInfoBo bo = sampleBreedBo();
        bo.setId(99999L);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("deleteWithValidByIds: happy path → 走基类 softDelete，wrapper.setSql del_flag='1'，entity 携 id+delUnique（delFlag 不进 entity）")
    void testDeleteWithValidByIds_HappyPath() {
        when(breedInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(30001L, 30002L));

        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<UpdateWrapper<BreedInfo>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(breedInfoMapper, times(2)).update(isNull(), wrapperCaptor.capture());

        List<UpdateWrapper<BreedInfo>> capturedWrappers = wrapperCaptor.getAllValues();
        assertThat(capturedWrappers).hasSize(2);
        assertThat(capturedWrappers).allSatisfy(w -> {
            assertThat(w.getSqlSet()).contains("del_flag", "del_unique", "update_by", "update_time");
            assertThat(w.getExpression().getNormal().getSqlSegment()).contains("id");
        });
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合 → 0，不打 DB")
    void testDeleteWithValidByIds_EmptyShortCircuit() {
        int rows = service.deleteWithValidByIds(Collections.emptyList());
        assertThat(rows).isZero();
        verify(breedInfoMapper, times(0)).update(any(BreedInfo.class), any(UpdateWrapper.class));
    }
}
