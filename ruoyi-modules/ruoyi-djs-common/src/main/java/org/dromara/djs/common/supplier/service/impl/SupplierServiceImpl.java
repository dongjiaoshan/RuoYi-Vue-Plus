package org.dromara.djs.common.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.domain.bo.SupplierBo;
import org.dromara.djs.common.supplier.domain.query.SupplierQuery;
import org.dromara.djs.common.supplier.domain.vo.SupplierVo;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.common.supplier.service.ISupplierService;
import org.dromara.djs.common.validate.BizReferenceChecker;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 供应商主数据 Service 实现（SYS-MD-003 + SYS-MD-FIX-002）。
 *
 * <p>编码策略：{@code supplier_code} 新增时由 {@link IBizCodeGenerator} 生成，编辑端点不允许覆盖。</p>
 *
 * <p>软删走 {@link DjsBaseServiceImpl#softDelete}（单参，wrapper-only update）。</p>
 *
 * <p>{@code dealCount} / {@code purchaseQty}（FIX-002 新增聚合冗余字段）：V1 stub 始终 0；
 * insert 显式 reset，编辑端点也不允许通过 BO 覆盖（防止前端误传）。下游 BRD-MED / WMS-PURCHASE 落地后
 * 走独立内部接口（非 Web）回填。</p>
 *
 * @author djs
 * @since SYS-MD-003
 */
@Slf4j
@Service
public class SupplierServiceImpl extends DjsBaseServiceImpl<SupplierMapper, Supplier> implements ISupplierService {

    private final IBizCodeGenerator bizCodeGenerator;
    private final BizReferenceChecker bizReferenceChecker;

    public SupplierServiceImpl(SupplierMapper baseMapper,
                               IBizCodeGenerator bizCodeGenerator,
                               BizReferenceChecker bizReferenceChecker) {
        super(baseMapper);
        this.bizCodeGenerator = bizCodeGenerator;
        this.bizReferenceChecker = bizReferenceChecker;
    }

    @Override
    public TableDataInfo<SupplierVo> queryPageList(SupplierQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Supplier> wrapper = buildQueryWrapper(query);
        Page<SupplierVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SupplierVo> queryList(SupplierQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public SupplierVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(SupplierBo bo) {
        Supplier entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("供应商入参转换失败");
        }
        entity.setSupplierCode(generateSupplierCode());
        if (StringUtils.isBlank(entity.getBusinessStatus())) {
            entity.setBusinessStatus("0"); // 合作中
        }
        if (StringUtils.isBlank(entity.getSettleType())) {
            entity.setSettleType("cash"); // 默认现款现货
        }
        // 聚合冗余字段始终从 0 起步（不接受外部传入）
        entity.setDealCount(0);
        entity.setPurchaseQty(BigDecimal.ZERO);
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(SupplierBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("供应商 ID 不能为空");
        }
        Supplier exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("供应商不存在或已删除：" + bo.getId());
        }
        Supplier entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("供应商入参转换失败");
        }
        // supplier_code 不允许通过编辑端点修改
        entity.setSupplierCode(exists.getSupplierCode());
        // 聚合冗余字段不允许通过编辑端点修改（保留 DB 当前值）
        entity.setDealCount(exists.getDealCount());
        entity.setPurchaseQty(exists.getPurchaseQty());
        return baseMapper.updateById(entity);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus（Spring 注入的 {@code Converter} 单例）。
     */
    protected Supplier toEntity(SupplierBo bo) {
        return MapstructUtils.convert(bo, Supplier.class);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        for (Long id : ids) {
            if (bizReferenceChecker.isReferenced("t_md_supplier", id)) {
                throw new ServiceException("供应商被业务记录引用，无法删除：id=" + id);
            }
        }
        return softDelete(ids);
    }

    /**
     * 构造查询条件：supplier_name like / supplier_type eq / liaison_name like
     * / liaison_phone like / business_status eq / settle_type eq / supplier_code like。
     */
    private LambdaQueryWrapper<Supplier> buildQueryWrapper(SupplierQuery query) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(Supplier::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getSupplierName()), Supplier::getSupplierName, query.getSupplierName())
            .like(StringUtils.isNotBlank(query.getSupplierCode()), Supplier::getSupplierCode, query.getSupplierCode())
            .eq(StringUtils.isNotBlank(query.getSupplierType()), Supplier::getSupplierType, query.getSupplierType())
            .like(StringUtils.isNotBlank(query.getLiaisonName()), Supplier::getLiaisonName, query.getLiaisonName())
            .like(StringUtils.isNotBlank(query.getLiaisonPhone()), Supplier::getLiaisonPhone, query.getLiaisonPhone())
            .eq(StringUtils.isNotBlank(query.getBusinessStatus()), Supplier::getBusinessStatus, query.getBusinessStatus())
            .eq(StringUtils.isNotBlank(query.getSettleType()), Supplier::getSettleType, query.getSettleType())
            .ge(Objects.nonNull(query.getUpdateTimeBegin()), Supplier::getUpdateTime, query.getUpdateTimeBegin())
            .le(Objects.nonNull(query.getUpdateTimeEnd()), Supplier::getUpdateTime, query.getUpdateTimeEnd())
            .eq(Objects.nonNull(query.getUpdateBy()), Supplier::getUpdateBy, query.getUpdateBy())
            .orderByDesc(Supplier::getId);
        return wrapper;
    }

    /**
     * 生成供应商编码：走 {@link IBizCodeGenerator}，类型 {@link BizCodeType#SUPPLIER_CODE}。
     */
    private String generateSupplierCode() {
        return bizCodeGenerator.generate(BizCodeType.SUPPLIER_CODE, Map.of());
    }

}
