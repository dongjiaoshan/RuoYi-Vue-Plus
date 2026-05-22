package org.dromara.djs.breed.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.production.domain.BoarConfig;
import org.dromara.djs.breed.production.domain.bo.BoarConfigBo;
import org.dromara.djs.breed.production.mapper.BoarConfigMapper;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BoarConfigServiceImpl} 单测（BRD-MD-003 Tab2）。
 *
 * <p>覆盖：insertByBo happy / updateByBo id 缺失 → ServiceException / 软删走基类。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BoarConfigServiceImpl 单元测试")
class BoarConfigServiceImplTest {

    @Mock
    private BoarConfigMapper boarMapper;

    private TestableBoarConfigServiceImpl service;

    static class TestableBoarConfigServiceImpl extends BoarConfigServiceImpl {
        TestableBoarConfigServiceImpl(BoarConfigMapper m) {
            super(m);
        }

        @Override
        protected BoarConfig toEntity(BoarConfigBo bo) {
            if (bo == null) {
                return null;
            }
            BoarConfig e = new BoarConfig();
            e.setId(bo.getId());
            e.setBoarId(bo.getBoarId());
            e.setSpermQualityThreshold(bo.getSpermQualityThreshold());
            e.setBreedingIntervalDays(bo.getBreedingIntervalDays());
            e.setRemark(bo.getRemark());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableBoarConfigServiceImpl(boarMapper);
    }

    private BoarConfigBo sampleBo() {
        BoarConfigBo bo = new BoarConfigBo();
        bo.setBoarId(null); // V1 通用配置
        bo.setSpermQualityThreshold(new BigDecimal("2.50"));
        bo.setBreedingIntervalDays(2);
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → mapper.insert 调一次")
    void testInsertByBo_HappyPath() {
        when(boarMapper.insert(any(BoarConfig.class))).thenAnswer(inv -> {
            BoarConfig e = inv.getArgument(0);
            e.setId(60001L);
            return 1;
        });

        int rows = service.insertByBo(sampleBo());

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<BoarConfig> captor = ArgumentCaptor.forClass(BoarConfig.class);
        verify(boarMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getBreedingIntervalDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → ServiceException")
    void testUpdateByBo_NullId() {
        BoarConfigBo bo = sampleBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ID 不能为空");
    }

    @Test
    @DisplayName("deleteWithValidByIds: happy path → wrapper.setSql del_flag='1'")
    void testDeleteWithValidByIds_HappyPath() {
        when(boarMapper.update(any(BoarConfig.class), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(60001L));

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<BoarConfig> entityCaptor = ArgumentCaptor.forClass(BoarConfig.class);
        ArgumentCaptor<UpdateWrapper<BoarConfig>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(boarMapper, times(1)).update(entityCaptor.capture(), wrapperCaptor.capture());

        BoarConfig entity = entityCaptor.getValue();
        assertThat(entity.getId()).isEqualTo(60001L);
        assertThat(entity.getDelUnique()).isEqualTo(60001L);

        UpdateWrapper<BoarConfig> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSet()).contains("del_flag = '1'");
        assertThat(wrapper.getExpression().getNormal().getSqlSegment()).contains("id");
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合短路")
    void testDeleteWithValidByIds_EmptyShortCircuit() {
        int rows = service.deleteWithValidByIds(Collections.emptyList());
        assertThat(rows).isZero();
        verify(boarMapper, times(0)).update(any(BoarConfig.class), any(UpdateWrapper.class));
    }
}
