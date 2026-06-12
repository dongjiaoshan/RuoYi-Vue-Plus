package org.dromara.djs.breed.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.bo.BarnBo;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.FarmStatMapper;
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
 * {@link BarnServiceImpl} 单元测试（BRD-MD-002）。
 *
 * <p>Happy path：insert（默认 barnStatus / currentCount）→ updateByBo（barnCode 不可改）→
 * deleteWithValidByIds（无引用 → 走基类 softDelete，wrapper.setSql del_flag='1'，绕过 @TableLogic）。
 * 拒绝路径：删除时若 t_farm_pig_info 存在引用 → 抛 ServiceException。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BarnServiceImpl 单元测试")
class BarnServiceImplTest {

    @Mock
    private BarnMapper barnMapper;

    @Mock
    private PigReferenceCheckMapper pigReferenceCheckMapper;

    @Mock
    private PenMapper penMapper;

    @Mock
    private FarmStatMapper farmStatMapper;

    private TestableBarnServiceImpl service;

    static class TestableBarnServiceImpl extends BarnServiceImpl {
        TestableBarnServiceImpl(BarnMapper m, PigReferenceCheckMapper p, PenMapper pen, FarmStatMapper stat) {
            super(m, p, pen, stat);
        }

        @Override
        protected Barn toEntity(BarnBo bo) {
            if (bo == null) return null;
            Barn b = new Barn();
            b.setId(bo.getId());
            b.setBarnCode(bo.getBarnCode());
            b.setBarnName(bo.getBarnName());
            b.setBarnType(bo.getBarnType());
            b.setCapacity(bo.getCapacity());
            b.setBarnStatus(bo.getBarnStatus());
            b.setRemark(bo.getRemark());
            return b;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableBarnServiceImpl(barnMapper, pigReferenceCheckMapper, penMapper, farmStatMapper);
    }

    private BarnBo sampleBo() {
        BarnBo bo = new BarnBo();
        bo.setBarnCode("B01-A");
        bo.setBarnName("一号妊娠舍A单元");
        bo.setBarnType("pregnant");
        bo.setCapacity(120);
        bo.setBarnStatus(1);
        bo.setRemark("BRD-MD-002 单测样本");
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy → mapper.insert 调一次，默认 currentCount=0")
    void testInsertByBo_HappyPath() {
        when(barnMapper.insert(any(Barn.class))).thenReturn(1);
        BarnBo bo = sampleBo();
        bo.setBarnStatus(null);  // 不传

        int rows = service.insertByBo(bo);
        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<Barn> captor = ArgumentCaptor.forClass(Barn.class);
        verify(barnMapper).insert(captor.capture());
        Barn saved = captor.getValue();
        assertThat(saved.getBarnCode()).isEqualTo("B01-A");
        assertThat(saved.getBarnType()).isEqualTo("pregnant");
        assertThat(saved.getBarnStatus()).isEqualTo(1);    // 默认 1=启用
        assertThat(saved.getCurrentCount()).isEqualTo(0);  // 默认 0
    }

    @Test
    @DisplayName("updateByBo: 编辑不允许覆盖 barnCode / currentCount（保留 DB 原值）")
    void testUpdateByBo_PreservesBarnCodeAndCurrentCount() {
        Barn existing = new Barn();
        existing.setId(80001L);
        existing.setBarnCode("B01-A");
        existing.setBarnName("旧名");
        existing.setCurrentCount(42);
        when(barnMapper.selectById(80001L)).thenReturn(existing);
        when(barnMapper.updateById(any(Barn.class))).thenReturn(1);

        BarnBo bo = sampleBo();
        bo.setId(80001L);
        bo.setBarnCode("X99-Z");  // 想偷偷改编码
        bo.setBarnName("一号妊娠舍A单元（改）");

        int rows = service.updateByBo(bo);
        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<Barn> captor = ArgumentCaptor.forClass(Barn.class);
        verify(barnMapper).updateById(captor.capture());
        Barn saved = captor.getValue();
        assertThat(saved.getBarnName()).isEqualTo("一号妊娠舍A单元（改）");
        assertThat(saved.getBarnCode()).isEqualTo("B01-A");  // 守住
        assertThat(saved.getCurrentCount()).isEqualTo(42);   // 守住
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → 抛 ServiceException")
    void testUpdateByBo_NullId() {
        BarnBo bo = sampleBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("栋舍 ID 不能为空");
    }

    @Test
    @DisplayName("deleteWithValidByIds: 无猪只引用 → 走基类 softDelete，wrapper.setSql del_flag='1'")
    void testDeleteWithValidByIds_HappyPath() {
        when(pigReferenceCheckMapper.countActiveByBarnId(80001L)).thenReturn(0L);
        when(pigReferenceCheckMapper.countActiveByBarnId(80002L)).thenReturn(0L);
        when(barnMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(80001L, 80002L));
        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<UpdateWrapper<Barn>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(barnMapper, times(2)).update(isNull(), wrapperCaptor.capture());

        // wrapper.setSql 强写 del_flag='1' —— 这是 D03 教训的硬约束
        List<UpdateWrapper<Barn>> wrappers = wrapperCaptor.getAllValues();
        assertThat(wrappers).allSatisfy(w -> {
            assertThat(w.getSqlSet()).contains("del_flag", "del_unique", "update_by", "update_time");
            assertThat(w.getExpression().getNormal().getSqlSegment()).contains("id");
        });
    }

    @Test
    @DisplayName("deleteWithValidByIds: 栋舍下有未删猪只 → 抛 ServiceException，不打 update")
    void testDeleteWithValidByIds_RejectWhenPigsExist() {
        Barn existing = new Barn();
        existing.setId(80001L);
        existing.setBarnName("一号妊娠舍A单元");
        when(barnMapper.selectById(80001L)).thenReturn(existing);
        when(pigReferenceCheckMapper.countActiveByBarnId(80001L)).thenReturn(3L);

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(80001L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("一号妊娠舍A单元")
            .hasMessageContaining("3 头");
        verify(barnMapper, times(0)).update(any(Barn.class), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合 → 直接返 0，不查 pig")
    void testDeleteWithValidByIds_EmptyShortCircuit() {
        int rows = service.deleteWithValidByIds(List.of());
        assertThat(rows).isZero();
        verify(pigReferenceCheckMapper, times(0)).countActiveByBarnId(any());
    }
}
