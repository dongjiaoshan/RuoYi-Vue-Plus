package org.dromara.djs.store.member.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.store.member.domain.StoreMember;
import org.dromara.djs.store.member.domain.StoreMemberConsumption;
import org.dromara.djs.store.member.domain.bo.StoreMemberConsumptionBo;
import org.dromara.djs.store.member.domain.vo.StoreMemberConsumptionVo;
import org.dromara.djs.store.member.mapper.StoreMemberConsumptionMapper;
import org.dromara.djs.store.member.mapper.StoreMemberMapper;
import org.dromara.djs.store.member.service.IStoreMemberConsumptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 会员手录消费记录 Service 实现（STR-MEMBER-001）。
 *
 * <h3>为什么不 extends {@link org.dromara.djs.common.base.DjsBaseServiceImpl}</h3>
 * <p>基类 {@code softDelete} 会 {@code .set("del_unique", id)}，但
 * {@code t_store_member_consumption} <b>无 del_unique 列</b>（实际 DDL 与 member 表不同），
 * 复用基类会报 {@code Unknown column 'del_unique'}。本类自写 {@link #deleteByIds} 软删，
 * 只 set {@code del_flag='1'} + 审计字段。</p>
 *
 * <h3>手录（{@link #add}）</h3>
 * <p>校验会员存在 → INSERT；{@code create_by} 由 ruoyi MetaObjectHandler 自动 fill 当前登录 user_id
 * （承载录入人 operator 语义，ADR-0007）。V1 不强校验金额。门店默认取会员所属门店。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreMemberConsumptionServiceImpl implements IStoreMemberConsumptionService {

    private final StoreMemberConsumptionMapper baseMapper;
    private final StoreMemberMapper memberMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(StoreMemberConsumptionBo bo) {
        StoreMember member = memberMapper.selectById(bo.getMemberId());
        if (member == null) {
            throw new ServiceException("会员不存在或已删除：" + bo.getMemberId());
        }
        StoreMemberConsumption record = new StoreMemberConsumption();
        record.setMemberId(bo.getMemberId());
        record.setConsumeDate(bo.getConsumeDate());
        // 门店默认取会员所属门店（前端可显式传覆盖）
        record.setStoreId(bo.getStoreId() != null ? bo.getStoreId() : member.getStoreId());
        record.setSku(bo.getSku());
        record.setQuantity(bo.getQuantity());
        record.setAmountManual(bo.getAmountManual());
        record.setNotes(bo.getNotes());
        baseMapper.insert(record);
        return record.getId();
    }

    @Override
    public List<StoreMemberConsumptionVo> listByMember(Long memberId) {
        return baseMapper.selectVoList(new LambdaQueryWrapper<StoreMemberConsumption>()
            .eq(StoreMemberConsumption::getMemberId, memberId)
            .orderByDesc(StoreMemberConsumption::getConsumeDate)
            .orderByDesc(StoreMemberConsumption::getId));
    }

    /**
     * 软删消费记录：本表无 {@code del_unique} 列，wrapper update 只 set {@code del_flag='1'} + 审计。
     */
    @Override
    public int deleteByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        Long updateBy = currentUserIdSafe();
        Date now = new Date();
        int count = 0;
        for (Long id : ids) {
            UpdateWrapper<StoreMemberConsumption> wrapper = Wrappers.<StoreMemberConsumption>update()
                .eq("id", id)
                .eq("del_flag", "0")
                .set("del_flag", "1")
                .set("update_by", updateBy)
                .set("update_time", now);
            count += baseMapper.update(null, wrapper);
        }
        return count;
    }

    /**
     * 安全获取当前登录 user_id（无登录上下文兜底 0，与 ruoyi MetaObjectHandler 一致）。
     */
    private Long currentUserIdSafe() {
        try {
            Long id = LoginHelper.getUserId();
            return id != null ? id : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

}
