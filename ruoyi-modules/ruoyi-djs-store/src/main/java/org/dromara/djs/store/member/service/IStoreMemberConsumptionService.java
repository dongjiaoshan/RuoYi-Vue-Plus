package org.dromara.djs.store.member.service;

import org.dromara.djs.store.member.domain.bo.StoreMemberConsumptionBo;
import org.dromara.djs.store.member.domain.vo.StoreMemberConsumptionVo;

import java.util.Collection;
import java.util.List;

/**
 * 会员手录消费记录 Service（STR-MEMBER-001）。
 *
 * @author djs
 * @since STR-MEMBER-001
 */
public interface IStoreMemberConsumptionService {

    /**
     * 手录一条消费记录（{@code create_by} 自动 fill 录入人，不强校验金额），返回新记录 ID。
     */
    Long add(StoreMemberConsumptionBo bo);

    /**
     * 按会员 ID 查该会员的全部消费记录（按消费日期倒序）。
     */
    List<StoreMemberConsumptionVo> listByMember(Long memberId);

    /**
     * 软删消费记录（本表无 {@code del_unique}，只 set {@code del_flag='1'}）。
     */
    int deleteByIds(Collection<Long> ids);

}
