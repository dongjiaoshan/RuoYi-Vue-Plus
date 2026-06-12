package org.dromara.djs.breed.production.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.production.domain.FattenAgeStage;
import org.dromara.djs.breed.production.domain.bo.FattenAgeStageBo;
import org.dromara.djs.breed.production.mapper.FattenAgeStageMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FattenAgeStageServiceImpl} 单测（A1 Tab2 育肥日龄阶段）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>batchSave happy path → 先软删旧行（softDelete wrapper），再逐行 insert</li>
 *   <li>batchSave 空入参（仅清空旧行）→ 软删后不 insert，返 0</li>
 *   <li>batchSave 校验 endAge < startAge → ServiceException</li>
 *   <li>removeById → 走基类 softDelete</li>
 * </ul></p>
 *
 * @author djs
 * @since A1
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FattenAgeStageServiceImpl 单元测试")
class FattenAgeStageServiceImplTest {

    @BeforeAll
    static void initMpEntityCache() {
        // 预热 MP 实体 lambda cache（无 Spring/DB 上下文，queryList 用 LambdaQueryWrapper<FattenAgeStage> 需 TableInfo）
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, FattenAgeStage.class);
    }

    @Mock
    private FattenAgeStageMapper stageMapper;

    private FattenAgeStageServiceImpl service;

    @BeforeEach
    void setup() {
        service = new FattenAgeStageServiceImpl(stageMapper);
    }

    private FattenAgeStageBo bo(int start, int end, Integer recordGrowth) {
        FattenAgeStageBo b = new FattenAgeStageBo();
        b.setStartAge(start);
        b.setEndAge(end);
        b.setRecordGrowth(recordGrowth);
        return b;
    }

    @Test
    @DisplayName("batchSave: happy path → 软删旧行后逐行 insert")
    void testBatchSave_HappyPath() {
        FattenAgeStage old1 = new FattenAgeStage();
        old1.setId(201L);
        when(stageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(old1));
        // 软删走基类 softDelete → mapper.update(null, wrapper)
        when(stageMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(stageMapper.insert(any(FattenAgeStage.class))).thenReturn(1);

        int rows = service.batchSave(List.of(bo(26, 42, 1), bo(43, 70, 0)));

        assertThat(rows).isEqualTo(2);
        // 软删 1 旧行
        verify(stageMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
        // 灌 2 新行
        ArgumentCaptor<FattenAgeStage> captor = ArgumentCaptor.forClass(FattenAgeStage.class);
        verify(stageMapper, times(2)).insert(captor.capture());
        List<FattenAgeStage> saved = captor.getAllValues();
        assertThat(saved.get(0).getStartAge()).isEqualTo(26);
        assertThat(saved.get(0).getRecordGrowth()).isEqualTo(1);
        // recordGrowth 缺省时兜底 0
        assertThat(saved.get(1).getRecordGrowth()).isEqualTo(0);
    }

    @Test
    @DisplayName("batchSave: 空入参 → 软删旧行后不 insert，返 0")
    void testBatchSave_EmptyOnlyClears() {
        FattenAgeStage old1 = new FattenAgeStage();
        old1.setId(201L);
        when(stageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(old1));
        when(stageMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.batchSave(Collections.emptyList());

        assertThat(rows).isZero();
        verify(stageMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
        verify(stageMapper, never()).insert(any(FattenAgeStage.class));
    }

    @Test
    @DisplayName("batchSave: endAge < startAge → ServiceException")
    void testBatchSave_InvalidRange() {
        when(stageMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> service.batchSave(List.of(bo(70, 43, 1))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("截止日龄不能小于起始日龄");
    }

    @Test
    @DisplayName("queryList: 走 selectVoList（按 start_age 升序）")
    void testQueryList() {
        when(stageMapper.selectVoList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        assertThat(service.queryList()).isEmpty();
        verify(stageMapper, times(1)).selectVoList(any(Wrapper.class));
    }

    @Test
    @DisplayName("removeById: 走基类 softDelete，wrapper.setSql del_flag='1'")
    void testRemoveById() {
        when(stageMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.removeById(201L);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<UpdateWrapper<FattenAgeStage>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(stageMapper, times(1)).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("del_flag", "del_unique");
    }

    @Test
    @DisplayName("removeById: null id → 0，不打 DB")
    void testRemoveById_NullShortCircuit() {
        assertThat(service.removeById(null)).isZero();
        verify(stageMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }
}
