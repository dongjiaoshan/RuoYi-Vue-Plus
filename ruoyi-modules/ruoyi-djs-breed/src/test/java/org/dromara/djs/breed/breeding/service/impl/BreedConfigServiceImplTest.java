package org.dromara.djs.breed.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.breeding.domain.BreedConfig;
import org.dromara.djs.breed.breeding.domain.BreedInfo;
import org.dromara.djs.breed.breeding.domain.bo.BreedConfigBo;
import org.dromara.djs.breed.breeding.domain.query.BreedConfigQuery;
import org.dromara.djs.breed.breeding.domain.vo.BreedConfigVo;
import org.dromara.djs.breed.breeding.mapper.BreedConfigMapper;
import org.dromara.djs.breed.breeding.mapper.BreedInfoMapper;
import org.junit.jupiter.api.BeforeAll;
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
 * {@link BreedConfigServiceImpl} 单测（BRD-MD-001）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>insertByBo happy path（三个 code 引用校验通过）</li>
 *   <li>insertByBo 仔代 cubCode 未在 breed_info 登记 → ServiceException</li>
 *   <li>列表 / 详情 / 编辑</li>
 *   <li>软删走基类，wrapper.setSql del_flag='1'（D03 教训）</li>
 * </ul></p>
 *
 * @author djs
 * @since BRD-MD-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BreedConfigServiceImpl 单元测试")
class BreedConfigServiceImplTest {

    @BeforeAll
    static void initMpEntityCache() {
        // 预热 MyBatis-Plus 实体 lambda cache（纯 Mockito 无 Spring/DB 上下文，enrichNames 用 LambdaQueryWrapper&lt;BreedInfo&gt; 需 TableInfo）
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, BreedInfo.class);
        TableInfoHelper.initTableInfo(assistant, BreedConfig.class);
    }

    @Mock
    private BreedConfigMapper breedConfigMapper;

    @Mock
    private BreedInfoMapper breedInfoMapper;

    private TestableBreedConfigServiceImpl service;

    static class TestableBreedConfigServiceImpl extends BreedConfigServiceImpl {
        TestableBreedConfigServiceImpl(BreedConfigMapper m, BreedInfoMapper i) {
            super(m, i);
        }

        @Override
        protected BreedConfig toEntity(BreedConfigBo bo) {
            if (bo == null) {
                return null;
            }
            BreedConfig e = new BreedConfig();
            e.setId(bo.getId());
            e.setBreedStrain(bo.getBreedStrain());
            e.setMotherCode(bo.getMotherCode());
            e.setFatherCode(bo.getFatherCode());
            e.setCubCode(bo.getCubCode());
            e.setRemark(bo.getRemark());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableBreedConfigServiceImpl(breedConfigMapper, breedInfoMapper);
    }

    private BreedConfigBo sampleBo() {
        BreedConfigBo bo = new BreedConfigBo();
        bo.setBreedStrain(1);
        bo.setMotherCode("landrace");
        bo.setFatherCode("yorkshire");
        bo.setCubCode("binary");
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → 三个 code 校验通过 / mapper.insert 调一次")
    void testInsertByBo_HappyPath() {
        // 校验三次 selectCount 都返 1（每个 code 都存在）
        when(breedInfoMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(breedConfigMapper.insert(any(BreedConfig.class))).thenAnswer(inv -> {
            BreedConfig e = inv.getArgument(0);
            e.setId(40001L);
            return 1;
        });

        int rows = service.insertByBo(sampleBo());

        assertThat(rows).isEqualTo(1);
        verify(breedInfoMapper, times(3)).selectCount(any(Wrapper.class));
        ArgumentCaptor<BreedConfig> captor = ArgumentCaptor.forClass(BreedConfig.class);
        verify(breedConfigMapper, times(1)).insert(captor.capture());
        BreedConfig saved = captor.getValue();
        assertThat(saved.getMotherCode()).isEqualTo("landrace");
        assertThat(saved.getFatherCode()).isEqualTo("yorkshire");
        assertThat(saved.getCubCode()).isEqualTo("binary");
    }

    @Test
    @DisplayName("insertByBo: cubCode 未在 breed_info 登记 → ServiceException")
    void testInsertByBo_CubCodeNotExists() {
        when(breedInfoMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        assertThatThrownBy(() -> service.insertByBo(sampleBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("queryPageList: 走 mapper.selectVoPage")
    void testQueryPageList() {
        BreedConfigQuery query = new BreedConfigQuery();
        query.setBreedStrain(1);
        PageQuery pageQuery = new PageQuery(1, 10);

        BreedConfigVo vo = new BreedConfigVo();
        vo.setId(40001L);
        vo.setMotherCode("landrace");
        Page<BreedConfigVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(breedConfigMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        TableDataInfo<BreedConfigVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows().get(0).getMotherCode()).isEqualTo("landrace");
    }

    @Test
    @DisplayName("queryPageList: 富集 motherName/fatherName/offspringName（批量 JOIN breed_info，避免 N+1）")
    void testQueryPageList_EnrichNames() {
        BreedConfigQuery query = new BreedConfigQuery();
        query.setBreedStrain(1);
        PageQuery pageQuery = new PageQuery(1, 10);

        BreedConfigVo vo = new BreedConfigVo();
        vo.setId(40001L);
        vo.setBreedStrain(1);
        vo.setMotherCode("landrace");
        vo.setFatherCode("yorkshire");
        vo.setCubCode("binary");
        Page<BreedConfigVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(breedConfigMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        // breed_info 批量查名：一次 selectList 返三条 code→name
        when(breedInfoMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            breedInfo("landrace", "长白"),
            breedInfo("yorkshire", "大约克"),
            breedInfo("binary", "长大二元")
        ));

        TableDataInfo<BreedConfigVo> result = service.queryPageList(query, pageQuery);

        BreedConfigVo row = result.getRows().get(0);
        assertThat(row.getMotherName()).isEqualTo("长白");
        assertThat(row.getFatherName()).isEqualTo("大约克");
        assertThat(row.getOffspringName()).isEqualTo("长大二元");
        // 避免 N+1：3 行 code 仅 1 次批量查（同一 breedStrain 分组）
        verify(breedInfoMapper, times(1)).selectList(any(Wrapper.class));
    }

    private BreedInfo breedInfo(String code, String name) {
        BreedInfo info = new BreedInfo();
        info.setBreedStrainCode(code);
        info.setBreedStrainName(name);
        return info;
    }

    @Test
    @DisplayName("updateByBo: happy path → mapper.updateById 调一次")
    void testUpdateByBo_HappyPath() {
        BreedConfig existing = new BreedConfig();
        existing.setId(40001L);
        when(breedConfigMapper.selectById(40001L)).thenReturn(existing);
        when(breedInfoMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(breedConfigMapper.updateById(any(BreedConfig.class))).thenReturn(1);

        BreedConfigBo bo = sampleBo();
        bo.setId(40001L);

        int rows = service.updateByBo(bo);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → 抛 ServiceException")
    void testUpdateByBo_NullId() {
        BreedConfigBo bo = sampleBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ID 不能为空");
    }

    @Test
    @DisplayName("deleteWithValidByIds: happy path → 走基类 softDelete，wrapper.setSql del_flag='1'，entity 携 id+delUnique")
    void testDeleteWithValidByIds_HappyPath() {
        when(breedConfigMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(40001L, 40002L));

        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<UpdateWrapper<BreedConfig>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(breedConfigMapper, times(2)).update(isNull(), wrapperCaptor.capture());

        List<UpdateWrapper<BreedConfig>> wrappers = wrapperCaptor.getAllValues();
        assertThat(wrappers).allSatisfy(w -> {
            assertThat(w.getSqlSet()).contains("del_flag", "del_unique", "update_by", "update_time");
            assertThat(w.getExpression().getNormal().getSqlSegment()).contains("id");
        });
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合 → 0，不打 DB")
    void testDeleteWithValidByIds_EmptyShortCircuit() {
        int rows = service.deleteWithValidByIds(Collections.emptyList());
        assertThat(rows).isZero();
        verify(breedConfigMapper, times(0)).update(any(BreedConfig.class), any(UpdateWrapper.class));
    }
}
