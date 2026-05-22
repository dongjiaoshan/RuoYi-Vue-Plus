package org.dromara.djs.breed.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.domain.bo.PenBo;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.dromara.djs.breed.farm.mapper.PigReferenceCheckMapper;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PenServiceImpl} 单元测试（BRD-MD-002）。
 *
 * <p>关键断言：</p>
 * <ul>
 *   <li>updateByBo 不允许变更 barnId / penCode（栏位编辑时锁死）</li>
 *   <li>deleteWithValidByIds 栏位级单独校验 t_farm_pig_info.pen_id 引用</li>
 *   <li>软删走基类 softDelete，wrapper.setSql del_flag='1'（D03 教训）</li>
 * </ul>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PenServiceImpl 单元测试")
class PenServiceImplTest {

    @Mock
    private PenMapper penMapper;

    @Mock
    private PigReferenceCheckMapper pigReferenceCheckMapper;

    private TestablePenServiceImpl service;

    static class TestablePenServiceImpl extends PenServiceImpl {
        TestablePenServiceImpl(PenMapper m, PigReferenceCheckMapper p) {
            super(m, p);
        }

        @Override
        protected Pen toEntity(PenBo bo) {
            if (bo == null) return null;
            Pen p = new Pen();
            p.setId(bo.getId());
            p.setBarnId(bo.getBarnId());
            p.setPenCode(bo.getPenCode());
            p.setPenName(bo.getPenName());
            p.setPenType(bo.getPenType());
            p.setCapacity(bo.getCapacity());
            p.setPenStatus(bo.getPenStatus());
            p.setRemark(bo.getRemark());
            return p;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestablePenServiceImpl(penMapper, pigReferenceCheckMapper);
    }

    private PenBo sampleBo() {
        PenBo bo = new PenBo();
        bo.setBarnId(80001L);
        bo.setPenCode("P-01");
        bo.setPenName("01号限位栏");
        bo.setPenType("stall");
        bo.setCapacity(8);
        bo.setPenStatus(1);
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy → 默认 currentCount=0 / penStatus=1")
    void testInsertByBo_HappyPath() {
        when(penMapper.insert(any(Pen.class))).thenReturn(1);
        PenBo bo = sampleBo();
        bo.setPenStatus(null);

        int rows = service.insertByBo(bo);
        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<Pen> captor = ArgumentCaptor.forClass(Pen.class);
        verify(penMapper).insert(captor.capture());
        Pen saved = captor.getValue();
        assertThat(saved.getBarnId()).isEqualTo(80001L);
        assertThat(saved.getPenType()).isEqualTo("stall");
        assertThat(saved.getPenStatus()).isEqualTo(1);
        assertThat(saved.getCurrentCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("updateByBo: 编辑不允许变更 barnId / penCode / currentCount（保留 DB 原值）")
    void testUpdateByBo_LocksBarnIdAndPenCode() {
        Pen existing = new Pen();
        existing.setId(90001L);
        existing.setBarnId(80001L);
        existing.setPenCode("P-01");
        existing.setCurrentCount(5);
        when(penMapper.selectById(90001L)).thenReturn(existing);
        when(penMapper.updateById(any(Pen.class))).thenReturn(1);

        PenBo bo = sampleBo();
        bo.setId(90001L);
        bo.setBarnId(99999L);     // 想偷偷迁栋
        bo.setPenCode("P-NEW");   // 想偷偷改编码
        bo.setPenName("01号限位栏（改）");

        service.updateByBo(bo);
        ArgumentCaptor<Pen> captor = ArgumentCaptor.forClass(Pen.class);
        verify(penMapper).updateById(captor.capture());
        Pen saved = captor.getValue();
        assertThat(saved.getPenName()).isEqualTo("01号限位栏（改）");
        assertThat(saved.getBarnId()).isEqualTo(80001L);   // 守住
        assertThat(saved.getPenCode()).isEqualTo("P-01");  // 守住
        assertThat(saved.getCurrentCount()).isEqualTo(5);  // 守住
    }

    @Test
    @DisplayName("deleteWithValidByIds: 无猪只占栏 → 走基类 softDelete，wrapper.setSql del_flag='1'")
    void testDeleteWithValidByIds_HappyPath() {
        when(pigReferenceCheckMapper.countActiveByPenId(90001L)).thenReturn(0L);
        when(penMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(90001L));
        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<UpdateWrapper<Pen>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(penMapper).update(isNull(), wrapperCaptor.capture());

        UpdateWrapper<Pen> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSet()).contains("del_flag", "del_unique", "update_by", "update_time");
        assertThat(wrapper.getExpression().getNormal().getSqlSegment()).contains("id");
    }

    @Test
    @DisplayName("deleteWithValidByIds: 栏位有未删猪只 → 抛 ServiceException，不打 update")
    void testDeleteWithValidByIds_RejectWhenPigsExist() {
        Pen existing = new Pen();
        existing.setId(90001L);
        existing.setPenName("01号限位栏");
        when(penMapper.selectById(90001L)).thenReturn(existing);
        when(pigReferenceCheckMapper.countActiveByPenId(90001L)).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(90001L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("01号限位栏")
            .hasMessageContaining("2 头");
        verify(penMapper, times(0)).update(any(Pen.class), any(UpdateWrapper.class));
    }
}
