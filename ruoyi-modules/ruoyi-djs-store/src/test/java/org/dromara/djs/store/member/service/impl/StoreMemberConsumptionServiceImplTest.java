package org.dromara.djs.store.member.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.store.member.domain.StoreMember;
import org.dromara.djs.store.member.domain.StoreMemberConsumption;
import org.dromara.djs.store.member.domain.bo.StoreMemberConsumptionBo;
import org.dromara.djs.store.member.mapper.StoreMemberConsumptionMapper;
import org.dromara.djs.store.member.mapper.StoreMemberMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreMemberConsumptionServiceImpl} 单测（STR-MEMBER-001）。
 *
 * <p>覆盖：手录消费 happy（会员存在 → 插入，门店默认取会员门店）；会员不存在抛异常；
 * 软删只 set del_flag（本表无 del_unique 列，不复用基类）。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreMemberConsumptionServiceImpl 手录消费单测")
class StoreMemberConsumptionServiceImplTest {

    private final StoreMemberConsumptionMapper consumptionMapper = mock(StoreMemberConsumptionMapper.class);
    private final StoreMemberMapper memberMapper = mock(StoreMemberMapper.class);

    private final StoreMemberConsumptionServiceImpl service =
        new StoreMemberConsumptionServiceImpl(consumptionMapper, memberMapper);

    private StoreMemberConsumptionBo bo(long memberId) {
        StoreMemberConsumptionBo bo = new StoreMemberConsumptionBo();
        bo.setMemberId(memberId);
        bo.setConsumeDate(new Date());
        bo.setSku("有机白菜 5kg");
        bo.setQuantity(new BigDecimal("2"));
        bo.setAmountManual(new BigDecimal("66.50"));
        bo.setNotes("会员到店现金购");
        return bo;
    }

    @Test
    @DisplayName("add：会员存在 → 插入消费记录，门店默认取会员所属门店")
    void addHappy() {
        StoreMember member = new StoreMember();
        member.setId(5001L);
        member.setStoreId(7001L);
        when(memberMapper.selectById(5001L)).thenReturn(member);
        when(consumptionMapper.insert(any(StoreMemberConsumption.class))).thenReturn(1);

        service.add(bo(5001L));

        ArgumentCaptor<StoreMemberConsumption> captor = ArgumentCaptor.forClass(StoreMemberConsumption.class);
        verify(consumptionMapper, times(1)).insert(captor.capture());
        StoreMemberConsumption saved = captor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(5001L);
        assertThat(saved.getStoreId()).isEqualTo(7001L);
        assertThat(saved.getSku()).isEqualTo("有机白菜 5kg");
        assertThat(saved.getAmountManual()).isEqualByComparingTo("66.50");
    }

    @Test
    @DisplayName("add：会员不存在 → 抛 ServiceException，不插入")
    void addMemberNotFound() {
        when(memberMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> service.add(bo(9999L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("会员不存在");
        verify(consumptionMapper, times(0)).insert(any(StoreMemberConsumption.class));
    }

    @Test
    @DisplayName("deleteByIds：本表无 del_unique，wrapper update 只 set del_flag（逐 id）")
    void deleteByIdsSoftDelete() {
        when(consumptionMapper.update(any(), any())).thenReturn(1);
        int n = service.deleteByIds(List.of(1L, 2L, 3L));
        assertThat(n).isEqualTo(3);
        verify(consumptionMapper, times(3)).update(eq(null), any());
    }

    @Test
    @DisplayName("listByMember：按 memberId 查消费记录列表")
    void listByMember() {
        when(consumptionMapper.selectVoList(any())).thenReturn(List.of());
        service.listByMember(5001L);
        verify(consumptionMapper, times(1)).selectVoList(any());
    }

}
