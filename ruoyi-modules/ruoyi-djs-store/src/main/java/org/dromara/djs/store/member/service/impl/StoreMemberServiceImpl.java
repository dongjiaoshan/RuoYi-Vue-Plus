package org.dromara.djs.store.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.store.member.domain.StoreMember;
import org.dromara.djs.store.member.domain.bo.StoreMemberBo;
import org.dromara.djs.store.member.domain.query.StoreMemberQuery;
import org.dromara.djs.store.member.domain.vo.StoreMemberStatsVo;
import org.dromara.djs.store.member.domain.vo.StoreMemberVo;
import org.dromara.djs.store.member.mapper.StoreMemberConsumptionMapper;
import org.dromara.djs.store.member.mapper.StoreMemberMapper;
import org.dromara.djs.store.member.service.IStoreMemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 会员档案 Service 实现（STR-MEMBER-001）。
 *
 * <h3>新增 / 编辑（{@link #add} / {@link #update}）</h3>
 * <p>手机号查重（{@code uk_phone} 业务前置友好报错，不直接撞 {@code DuplicateKeyException}）；新增时
 * {@code memberNo} 走 {@code generate(MEMBER_NO, Map.of())}（10001 段，终生递增）。门店可空，传了则校验存在。</p>
 *
 * <h3>软删（{@link #deleteByIds}）</h3>
 * <p>走基类 {@link DjsBaseServiceImpl#softDelete}（{@code del_flag='1' + del_unique=id}）——
 * member 表有 {@code del_unique} 列。</p>
 *
 * <h3>统计（{@link #getMonthlyStats}）</h3>
 * <p>V1 仅两个 count：本月会员数 + 本月录入消费记录数。不做 RFM / 客单价 / 复购（迁 V2）。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Slf4j
@Service
public class StoreMemberServiceImpl
    extends DjsBaseServiceImpl<StoreMemberMapper, StoreMember>
    implements IStoreMemberService {

    /**
     * 默认租户（V1 单租户；无登录上下文兜底）。
     */
    private static final String DEFAULT_TENANT = "1001";

    /**
     * 会员状态：1=正常 / 0=停用。
     */
    private static final int STATUS_NORMAL = 1;

    private final StoreMapper storeMapper;
    private final StoreMemberConsumptionMapper consumptionMapper;
    private final IBizCodeGenerator bizCodeGenerator;

    public StoreMemberServiceImpl(StoreMemberMapper baseMapper,
                                  StoreMapper storeMapper,
                                  StoreMemberConsumptionMapper consumptionMapper,
                                  IBizCodeGenerator bizCodeGenerator) {
        super(baseMapper);
        this.storeMapper = storeMapper;
        this.consumptionMapper = consumptionMapper;
        this.bizCodeGenerator = bizCodeGenerator;
    }

    @Override
    public TableDataInfo<StoreMemberVo> queryPageList(StoreMemberQuery query, PageQuery pageQuery) {
        Page<StoreMemberVo> page = baseMapper.selectVoPage(pageQuery.build(), buildWrapper(query));
        fillStoreNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<StoreMemberVo> queryList(StoreMemberQuery query) {
        List<StoreMemberVo> list = baseMapper.selectVoList(buildWrapper(query));
        fillStoreNames(list);
        return list;
    }

    @Override
    public StoreMemberVo queryById(Long id) {
        StoreMemberVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillStoreNames(List.of(vo));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(StoreMemberBo bo) {
        // 手机号查重（业务前置友好报错）
        assertPhoneNotDuplicate(bo.getPhone(), null);
        // 门店可空，传了校验存在
        if (bo.getStoreId() != null && storeMapper.selectById(bo.getStoreId()) == null) {
            throw new ServiceException("门店不存在或已删除：" + bo.getStoreId());
        }
        StoreMember member = new StoreMember();
        member.setMemberNo(bizCodeGenerator.generate(BizCodeType.MEMBER_NO, Map.of()));
        member.setMemberName(bo.getMemberName());
        member.setPhone(bo.getPhone());
        member.setMemberLevel(bo.getMemberLevel());
        member.setJoinDate(bo.getJoinDate());
        member.setStoreId(bo.getStoreId());
        member.setMemberTags(bo.getMemberTags());
        member.setMemberStatus(bo.getMemberStatus() != null ? bo.getMemberStatus() : STATUS_NORMAL);
        member.setRemark(bo.getRemark());
        baseMapper.insert(member);
        return member.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(StoreMemberBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("会员 ID 不能为空");
        }
        StoreMember exist = baseMapper.selectById(bo.getId());
        if (exist == null) {
            throw new ServiceException("会员不存在或已删除：" + bo.getId());
        }
        // 改手机号时查重排除自身
        assertPhoneNotDuplicate(bo.getPhone(), bo.getId());
        if (bo.getStoreId() != null && storeMapper.selectById(bo.getStoreId()) == null) {
            throw new ServiceException("门店不存在或已删除：" + bo.getStoreId());
        }
        exist.setMemberName(bo.getMemberName());
        exist.setPhone(bo.getPhone());
        exist.setMemberLevel(bo.getMemberLevel());
        exist.setJoinDate(bo.getJoinDate());
        exist.setStoreId(bo.getStoreId());
        exist.setMemberTags(bo.getMemberTags());
        if (bo.getMemberStatus() != null) {
            exist.setMemberStatus(bo.getMemberStatus());
        }
        exist.setRemark(bo.getRemark());
        baseMapper.updateById(exist);
    }

    @Override
    public int deleteByIds(Collection<Long> ids) {
        return softDelete(ids);
    }

    @Override
    public StoreMemberStatsVo getMonthlyStats() {
        String tenantId = currentTenantId();
        StoreMemberStatsVo vo = new StoreMemberStatsVo();
        vo.setMonthlyMemberCount(baseMapper.countMonthlyMembers(tenantId));
        vo.setMonthlyConsumptionCount(consumptionMapper.countMonthlyConsumptions(tenantId));
        return vo;
    }

    // ============================ 内部辅助 ============================

    private LambdaQueryWrapper<StoreMember> buildWrapper(StoreMemberQuery query) {
        LambdaQueryWrapper<StoreMember> w = new LambdaQueryWrapper<>();
        if (query != null) {
            w.like(query.getPhone() != null && !query.getPhone().isBlank(),
                    StoreMember::getPhone, query.getPhone())
                .like(query.getMemberName() != null && !query.getMemberName().isBlank(),
                    StoreMember::getMemberName, query.getMemberName())
                .eq(query.getMemberLevel() != null && !query.getMemberLevel().isBlank(),
                    StoreMember::getMemberLevel, query.getMemberLevel())
                .eq(query.getStoreId() != null, StoreMember::getStoreId, query.getStoreId());
        }
        w.orderByDesc(StoreMember::getId);
        return w;
    }

    /**
     * 手机号查重（同租户内未删会员，排除自身 ID）。
     *
     * @param phone     手机号
     * @param excludeId 编辑场景排除的自身会员 ID（新增传 null）
     */
    private void assertPhoneNotDuplicate(String phone, Long excludeId) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<StoreMember>()
            .eq(StoreMember::getPhone, phone)
            .ne(excludeId != null, StoreMember::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("手机号已被其他会员使用：" + phone);
        }
    }

    /**
     * 批量回填门店名称（一次查全部相关门店，避免 N+1）。
     */
    private void fillStoreNames(List<StoreMemberVo> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        List<Long> storeIds = members.stream()
            .map(StoreMemberVo::getStoreId).filter(Objects::nonNull).distinct().toList();
        if (storeIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = storeMapper.selectList(
                new LambdaQueryWrapper<Store>().in(Store::getId, storeIds))
            .stream()
            .collect(Collectors.toMap(Store::getId, Store::getStoreName, (a, b) -> a));
        for (StoreMemberVo m : members) {
            if (m.getStoreId() != null) {
                m.setStoreName(nameMap.get(m.getStoreId()));
            }
        }
    }

    /**
     * 当前租户 ID（无登录上下文兜底默认租户）。
     */
    private String currentTenantId() {
        String t = TenantHelper.getTenantId();
        return (t == null || t.isBlank()) ? DEFAULT_TENANT : t;
    }

}
