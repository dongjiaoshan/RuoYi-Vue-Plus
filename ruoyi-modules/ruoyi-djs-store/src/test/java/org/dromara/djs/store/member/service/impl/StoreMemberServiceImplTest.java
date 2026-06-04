package org.dromara.djs.store.member.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.store.member.domain.StoreMember;
import org.dromara.djs.store.member.domain.bo.StoreMemberBo;
import org.dromara.djs.store.member.domain.vo.StoreMemberStatsVo;
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

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreMemberServiceImpl} 单测（STR-MEMBER-001）。
 *
 * <p>覆盖：新增会员 happy（generate MEMBER_NO 落库 + 状态默认正常）；手机号查重抛异常；
 * 软删委托基类 wrapper update；本月统计两个 count。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreMemberServiceImpl 会员档案单测")
class StoreMemberServiceImplTest {

    private final StoreMemberMapper memberMapper = mock(StoreMemberMapper.class);
    private final StoreMapper storeMapper = mock(StoreMapper.class);
    private final StoreMemberConsumptionMapper consumptionMapper = mock(StoreMemberConsumptionMapper.class);
    private final IBizCodeGenerator bizCodeGenerator = mock(IBizCodeGenerator.class);

    private final StoreMemberServiceImpl service =
        new StoreMemberServiceImpl(memberMapper, storeMapper, consumptionMapper, bizCodeGenerator);

    private StoreMemberBo bo(String name, String phone) {
        StoreMemberBo bo = new StoreMemberBo();
        bo.setMemberName(name);
        bo.setPhone(phone);
        bo.setMemberLevel("normal");
        bo.setJoinDate(new Date());
        return bo;
    }

    @Test
    @DisplayName("add：手机号不重复 → 生成 member_no 落库，状态默认正常(1)，返回新 ID")
    void addHappy() {
        StoreMemberBo bo = bo("张三", "13800000001");
        when(memberMapper.selectCount(any())).thenReturn(0L);
        when(bizCodeGenerator.generate(eq(BizCodeType.MEMBER_NO), any())).thenReturn("10001");
        when(memberMapper.insert(any(StoreMember.class))).thenReturn(1);

        service.add(bo);

        ArgumentCaptor<StoreMember> captor = ArgumentCaptor.forClass(StoreMember.class);
        verify(memberMapper, times(1)).insert(captor.capture());
        StoreMember saved = captor.getValue();
        assertThat(saved.getMemberNo()).isEqualTo("10001");
        assertThat(saved.getMemberName()).isEqualTo("张三");
        assertThat(saved.getPhone()).isEqualTo("13800000001");
        assertThat(saved.getMemberStatus()).isEqualTo(1);
        verify(bizCodeGenerator, times(1)).generate(eq(BizCodeType.MEMBER_NO), any());
    }

    @Test
    @DisplayName("add：门店校验——传了不存在的 storeId 抛 ServiceException")
    void addStoreNotFound() {
        StoreMemberBo bo = bo("李四", "13800000002");
        bo.setStoreId(9999L);
        when(memberMapper.selectCount(any())).thenReturn(0L);
        when(storeMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> service.add(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("门店不存在");
        verify(memberMapper, times(0)).insert(any(StoreMember.class));
    }

    @Test
    @DisplayName("add：手机号重复 → 抛 ServiceException（业务前置查重，不撞 DuplicateKey）")
    void addPhoneDuplicate() {
        StoreMemberBo bo = bo("王五", "13800000001");
        when(memberMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.add(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("手机号已被其他会员使用");
        verify(memberMapper, times(0)).insert(any(StoreMember.class));
    }

    @Test
    @DisplayName("update：会员不存在 → 抛 ServiceException")
    void updateNotFound() {
        StoreMemberBo bo = bo("赵六", "13800000003");
        bo.setId(123L);
        when(memberMapper.selectById(123L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("会员不存在");
    }

    @Test
    @DisplayName("update：改手机号查重排除自身（自身命中不算重复）")
    void updateExcludeSelf() {
        StoreMember exist = new StoreMember();
        exist.setId(123L);
        StoreMemberBo bo = bo("赵六改", "13800000003");
        bo.setId(123L);
        when(memberMapper.selectById(123L)).thenReturn(exist);
        when(memberMapper.selectCount(any())).thenReturn(0L);
        when(memberMapper.updateById(any(StoreMember.class))).thenReturn(1);

        service.update(bo);

        verify(memberMapper, times(1)).updateById(any(StoreMember.class));
    }

    @Test
    @DisplayName("deleteByIds：委托基类 softDelete（wrapper update，member 表含 del_unique）")
    void deleteByIdsSoftDelete() {
        when(memberMapper.update(any(), any())).thenReturn(1);
        int n = service.deleteByIds(java.util.List.of(1L, 2L));
        assertThat(n).isEqualTo(2);
        verify(memberMapper, times(2)).update(eq(null), any());
    }

    @Test
    @DisplayName("getMonthlyStats：聚合两个 count（本月会员数 + 本月消费记录数）")
    void monthlyStats() {
        when(memberMapper.countMonthlyMembers(any())).thenReturn(7L);
        when(consumptionMapper.countMonthlyConsumptions(any())).thenReturn(15L);

        StoreMemberStatsVo vo = service.getMonthlyStats();

        assertThat(vo.getMonthlyMemberCount()).isEqualTo(7L);
        assertThat(vo.getMonthlyConsumptionCount()).isEqualTo(15L);
    }

}
