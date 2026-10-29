package org.dromara.djs.warehouse.tare.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.djs.warehouse.tare.domain.TareSpec;
import org.dromara.djs.warehouse.tare.domain.bo.TareSpecBo;
import org.dromara.djs.warehouse.tare.domain.vo.TareOptionVo;
import org.dromara.djs.warehouse.tare.mapper.TareSpecMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TareSpecServiceImplTest {

    @Mock
    private TareSpecMapper mapper;
    @Mock
    private DictService dictService;
    private TareSpecServiceImpl service;

    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, TareSpec.class);
    }

    @BeforeEach
    void setUp() {
        service = new TareSpecServiceImpl(mapper, dictService);
        Map<String, String> positions = new LinkedHashMap<>();
        positions.put("pig_in", "猪只入库管理");
        positions.put("cut_out", "白条分割出库管理");
        when(dictService.getAllDictByDictType("djs_tare_position")).thenReturn(positions);
    }

    private static TareSpec spec(long id, String name, String weight, int isDefault, long updatedAt) {
        TareSpec s = new TareSpec();
        s.setId(id);
        s.setSpecName(name);
        s.setTareWeight(new BigDecimal(weight));
        s.setIsDefault(isDefault);
        s.setPositions("pig_in");
        s.setUpdateTime(new Date(updatedAt));
        return s;
    }

    @Test
    void optionsPutTheNewestDefaultFirstAndSelectOnlyIt() {
        when(mapper.selectList(any())).thenReturn(List.of(
            spec(1, "旧默认筐", "1.000", 1, 1_000),
            spec(2, "普通筐", "0.800", 0, 3_000),
            spec(3, "新默认筐", "1.200", 1, 2_000)));

        List<TareOptionVo> options = service.listOptions("pig_in");

        assertEquals(List.of(3L, 2L, 1L), options.stream().map(TareOptionVo::getId).toList());
        assertEquals(List.of(true, false, false), options.stream().map(TareOptionVo::getSelected).toList());
        assertEquals(new BigDecimal("1.200"), options.get(0).getTareWeight());
    }

    @Test
    void optionsWithoutDefaultSelectNothing() {
        when(mapper.selectList(any())).thenReturn(List.of(spec(2, "普通筐", "0.800", 0, 3_000)));

        List<TareOptionVo> options = service.listOptions("pig_in");

        assertEquals(1, options.size());
        assertEquals(false, options.get(0).getSelected());
    }

    @Test
    void optionsRejectUnknownPosition() {
        assertThrows(ServiceException.class, () -> service.listOptions("nowhere"));
        verify(mapper, never()).selectList(any());
    }

    @Test
    void insertStoresDedupedPositionsAsCsv() {
        when(mapper.exists(any())).thenReturn(false);
        when(mapper.insert(any(TareSpec.class))).thenReturn(1);
        TareSpecBo bo = new TareSpecBo();
        bo.setSpecName(" 塑料筐 ");
        bo.setTareWeight(new BigDecimal("1.250"));
        bo.setIsDefault(1);
        bo.setPositions(List.of("pig_in", "cut_out", "pig_in"));

        assertEquals(1, service.insertByBo(bo));

        ArgumentCaptor<TareSpec> saved = ArgumentCaptor.forClass(TareSpec.class);
        verify(mapper).insert(saved.capture());
        assertEquals("塑料筐", saved.getValue().getSpecName());
        assertEquals("pig_in,cut_out", saved.getValue().getPositions());
        assertEquals(1, saved.getValue().getIsDefault());
    }

    @Test
    void insertRejectsUnknownPositionAndDuplicateName() {
        TareSpecBo bo = new TareSpecBo();
        bo.setSpecName("塑料筐");
        bo.setTareWeight(new BigDecimal("1"));
        bo.setIsDefault(0);
        bo.setPositions(List.of("store"));
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));

        bo.setPositions(List.of("pig_in"));
        when(mapper.exists(any())).thenReturn(true);
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        verify(mapper, never()).insert(any(TareSpec.class));
    }
}
