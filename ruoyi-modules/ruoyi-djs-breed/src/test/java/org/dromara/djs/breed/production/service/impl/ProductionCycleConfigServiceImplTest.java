package org.dromara.djs.breed.production.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.production.domain.ProductionCycleConfig;
import org.dromara.djs.breed.production.domain.bo.ProductionCycleConfigBo;
import org.dromara.djs.breed.production.mapper.ProductionCycleConfigMapper;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProductionCycleConfigServiceImpl} 单测（BRD-MD-003 Tab1）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>insertByBo happy path</li>
 *   <li>updateByBo id 缺失 → ServiceException</li>
 *   <li>getValue 优先 customValue / 回退 defaultValue / key 不存在返 null</li>
 *   <li>软删走基类，wrapper.setSql del_flag='1'（D03 教训）</li>
 * </ul></p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductionCycleConfigServiceImpl 单元测试")
class ProductionCycleConfigServiceImplTest {

    @BeforeAll
    static void initMpEntityCache() {
        // 预热 MP 实体 lambda cache（getValuesByKeys 用 LambdaQueryWrapper<ProductionCycleConfig> 需 TableInfo）
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, ProductionCycleConfig.class);
    }

    @Mock
    private ProductionCycleConfigMapper cycleMapper;

    private TestableProductionCycleConfigServiceImpl service;

    static class TestableProductionCycleConfigServiceImpl extends ProductionCycleConfigServiceImpl {
        TestableProductionCycleConfigServiceImpl(ProductionCycleConfigMapper m) {
            super(m);
        }

        @Override
        protected ProductionCycleConfig toEntity(ProductionCycleConfigBo bo) {
            if (bo == null) {
                return null;
            }
            ProductionCycleConfig e = new ProductionCycleConfig();
            e.setId(bo.getId());
            e.setConfigKey(bo.getConfigKey());
            e.setDefaultValue(bo.getDefaultValue());
            e.setCustomValue(bo.getCustomValue());
            e.setUnit(bo.getUnit());
            e.setDescription(bo.getDescription());
            e.setRemark(bo.getRemark());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableProductionCycleConfigServiceImpl(cycleMapper);
    }

    private ProductionCycleConfigBo sampleBo() {
        ProductionCycleConfigBo bo = new ProductionCycleConfigBo();
        bo.setConfigKey("gestation_days");
        bo.setDefaultValue(114);
        bo.setCustomValue(null);
        bo.setUnit("天");
        bo.setDescription("妊娠天数");
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → mapper.insert 调一次")
    void testInsertByBo_HappyPath() {
        when(cycleMapper.insert(any(ProductionCycleConfig.class))).thenAnswer(inv -> {
            ProductionCycleConfig e = inv.getArgument(0);
            e.setId(50001L);
            return 1;
        });

        int rows = service.insertByBo(sampleBo());

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<ProductionCycleConfig> captor = ArgumentCaptor.forClass(ProductionCycleConfig.class);
        verify(cycleMapper, times(1)).insert(captor.capture());
        ProductionCycleConfig saved = captor.getValue();
        assertThat(saved.getConfigKey()).isEqualTo("gestation_days");
        assertThat(saved.getDefaultValue()).isEqualTo(114);
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → 抛 ServiceException")
    void testUpdateByBo_NullId() {
        ProductionCycleConfigBo bo = sampleBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ID 不能为空");
    }

    @Test
    @DisplayName("getValue: 优先 customValue / 无则 defaultValue / key 不存在 null")
    void testGetValue() {
        // case 1: customValue 优先
        ProductionCycleConfig hit = new ProductionCycleConfig();
        hit.setConfigKey("gestation_days");
        hit.setDefaultValue(114);
        hit.setCustomValue(116);
        when(cycleMapper.selectOne(any(Wrapper.class))).thenReturn(hit);
        assertThat(service.getValue("gestation_days")).isEqualTo(116);

        // case 2: customValue null → defaultValue
        ProductionCycleConfig hit2 = new ProductionCycleConfig();
        hit2.setConfigKey("lactation_days");
        hit2.setDefaultValue(28);
        hit2.setCustomValue(null);
        when(cycleMapper.selectOne(any(Wrapper.class))).thenReturn(hit2);
        assertThat(service.getValue("lactation_days")).isEqualTo(28);

        // case 3: key 不存在 null
        when(cycleMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThat(service.getValue("nonexistent")).isNull();

        // case 4: 空字符串短路
        assertThat(service.getValue("")).isNull();
        assertThat(service.getValue(null)).isNull();
    }

    @Test
    @DisplayName("deleteWithValidByIds: happy path → 走基类 softDelete，wrapper.setSql del_flag='1'")
    void testDeleteWithValidByIds_HappyPath() {
        when(cycleMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(50001L, 50002L));

        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<UpdateWrapper<ProductionCycleConfig>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(cycleMapper, times(2)).update(isNull(), wrapperCaptor.capture());

        List<UpdateWrapper<ProductionCycleConfig>> wrappers = wrapperCaptor.getAllValues();
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
        verify(cycleMapper, times(0)).update(any(ProductionCycleConfig.class), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("getValuesByKeys: 批量取生效值（custom 优先 / 缺 key 不入 map / 空入参短路）")
    void testGetValuesByKeys() {
        ProductionCycleConfig r1 = new ProductionCycleConfig();
        r1.setConfigKey("sow_wean_to_breed_days");
        r1.setDefaultValue(6);
        r1.setCustomValue(8);
        ProductionCycleConfig r2 = new ProductionCycleConfig();
        r2.setConfigKey("sow_breed_to_farrow_days");
        r2.setDefaultValue(141);
        r2.setCustomValue(null);
        when(cycleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(r1, r2));

        Map<String, Integer> m = service.getValuesByKeys(
            List.of("sow_wean_to_breed_days", "sow_breed_to_farrow_days", "sow_missing"));
        assertThat(m).containsEntry("sow_wean_to_breed_days", 8)   // custom 优先
            .containsEntry("sow_breed_to_farrow_days", 141)        // custom null → default
            .doesNotContainKey("sow_missing");                     // DB 无 → 不入 map

        // 空入参短路
        assertThat(service.getValuesByKeys(Collections.emptyList())).isEmpty();
        assertThat(service.getValuesByKeys(null)).isEmpty();
    }

    @Test
    @DisplayName("batchSaveValues: key 存在 → update custom_value；不存在 → insert 新行")
    void testBatchSaveValues_UpsertMix() {
        ProductionCycleConfig existing = new ProductionCycleConfig();
        existing.setId(101L);
        existing.setConfigKey("sow_wean_to_breed_days");
        existing.setDefaultValue(6);
        // key1 命中（update）/ key2 未命中（insert）
        when(cycleMapper.selectOne(any(Wrapper.class)))
            .thenReturn(existing)
            .thenReturn(null);
        when(cycleMapper.updateById(any(ProductionCycleConfig.class))).thenReturn(1);
        when(cycleMapper.insert(any(ProductionCycleConfig.class))).thenReturn(1);

        // LinkedHashMap 保证 key 顺序 → selectOne 返回顺序对齐
        Map<String, Integer> values = new java.util.LinkedHashMap<>();
        values.put("sow_wean_to_breed_days", 9);
        values.put("sow_return_to_breed_days", 7);
        Map<String, Integer> defaults = Map.of(
            "sow_wean_to_breed_days", 6,
            "sow_return_to_breed_days", 5);

        int rows = service.batchSaveValues(values, defaults, Collections.emptyMap());

        assertThat(rows).isEqualTo(2);
        // update 1 次（existing），insert 1 次（new）
        ArgumentCaptor<ProductionCycleConfig> upd = ArgumentCaptor.forClass(ProductionCycleConfig.class);
        verify(cycleMapper, times(1)).updateById(upd.capture());
        assertThat(upd.getValue().getCustomValue()).isEqualTo(9);
        ArgumentCaptor<ProductionCycleConfig> ins = ArgumentCaptor.forClass(ProductionCycleConfig.class);
        verify(cycleMapper, times(1)).insert(ins.capture());
        assertThat(ins.getValue().getConfigKey()).isEqualTo("sow_return_to_breed_days");
        assertThat(ins.getValue().getCustomValue()).isEqualTo(7);
        assertThat(ins.getValue().getDefaultValue()).isEqualTo(5);  // 首次落库取 defaults
    }

    @Test
    @DisplayName("batchSaveValues: 空入参 → 0，不打 DB")
    void testBatchSaveValues_EmptyShortCircuit() {
        assertThat(service.batchSaveValues(Collections.emptyMap(), null, null)).isZero();
        assertThat(service.batchSaveValues(null, null, null)).isZero();
        verify(cycleMapper, never()).updateById(any(ProductionCycleConfig.class));
        verify(cycleMapper, never()).insert(any(ProductionCycleConfig.class));
    }
}
