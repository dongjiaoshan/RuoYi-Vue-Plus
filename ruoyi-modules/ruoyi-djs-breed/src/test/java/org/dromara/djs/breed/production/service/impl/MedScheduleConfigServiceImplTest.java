package org.dromara.djs.breed.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.production.domain.MedScheduleConfig;
import org.dromara.djs.breed.production.domain.bo.MedScheduleConfigBo;
import org.dromara.djs.breed.production.mapper.MedScheduleConfigMapper;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MedScheduleConfigServiceImpl} 单测（BRD-MD-003 Tab3）。
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
@DisplayName("MedScheduleConfigServiceImpl 单元测试")
class MedScheduleConfigServiceImplTest {

    @Mock
    private MedScheduleConfigMapper medMapper;

    private TestableMedScheduleConfigServiceImpl service;

    static class TestableMedScheduleConfigServiceImpl extends MedScheduleConfigServiceImpl {
        TestableMedScheduleConfigServiceImpl(MedScheduleConfigMapper m) {
            super(m);
        }

        @Override
        protected MedScheduleConfig toEntity(MedScheduleConfigBo bo) {
            if (bo == null) {
                return null;
            }
            MedScheduleConfig e = new MedScheduleConfig();
            e.setId(bo.getId());
            e.setMedType(bo.getMedType());
            e.setEventTrigger(bo.getEventTrigger());
            e.setDaysOffset(bo.getDaysOffset());
            e.setDescription(bo.getDescription());
            e.setRemark(bo.getRemark());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableMedScheduleConfigServiceImpl(medMapper);
    }

    private MedScheduleConfigBo sampleBo() {
        MedScheduleConfigBo bo = new MedScheduleConfigBo();
        bo.setMedType("vaccine");
        bo.setEventTrigger("birth");
        bo.setDaysOffset(7);
        bo.setDescription("仔猪出生后 7 天打三联苗");
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → mapper.insert 调一次")
    void testInsertByBo_HappyPath() {
        when(medMapper.insert(any(MedScheduleConfig.class))).thenAnswer(inv -> {
            MedScheduleConfig e = inv.getArgument(0);
            e.setId(70001L);
            return 1;
        });

        int rows = service.insertByBo(sampleBo());

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<MedScheduleConfig> captor = ArgumentCaptor.forClass(MedScheduleConfig.class);
        verify(medMapper, times(1)).insert(captor.capture());
        MedScheduleConfig saved = captor.getValue();
        assertThat(saved.getMedType()).isEqualTo("vaccine");
        assertThat(saved.getEventTrigger()).isEqualTo("birth");
        assertThat(saved.getDaysOffset()).isEqualTo(7);
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → ServiceException")
    void testUpdateByBo_NullId() {
        MedScheduleConfigBo bo = sampleBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ID 不能为空");
    }

    @Test
    @DisplayName("deleteWithValidByIds: happy path → wrapper.setSql del_flag='1'")
    void testDeleteWithValidByIds_HappyPath() {
        when(medMapper.update(any(MedScheduleConfig.class), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(70001L, 70002L));

        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<MedScheduleConfig> entityCaptor = ArgumentCaptor.forClass(MedScheduleConfig.class);
        ArgumentCaptor<UpdateWrapper<MedScheduleConfig>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(medMapper, times(2)).update(entityCaptor.capture(), wrapperCaptor.capture());

        List<MedScheduleConfig> entities = entityCaptor.getAllValues();
        assertThat(entities).extracting(MedScheduleConfig::getId).containsExactly(70001L, 70002L);
        assertThat(entities).extracting(MedScheduleConfig::getDelUnique).containsExactly(70001L, 70002L);

        List<UpdateWrapper<MedScheduleConfig>> wrappers = wrapperCaptor.getAllValues();
        assertThat(wrappers).allSatisfy(w -> {
            assertThat(w.getSqlSet()).contains("del_flag = '1'");
            assertThat(w.getExpression().getNormal().getSqlSegment()).contains("id");
        });
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合短路")
    void testDeleteWithValidByIds_EmptyShortCircuit() {
        int rows = service.deleteWithValidByIds(Collections.emptyList());
        assertThat(rows).isZero();
        verify(medMapper, times(0)).update(any(MedScheduleConfig.class), any(UpdateWrapper.class));
    }
}
