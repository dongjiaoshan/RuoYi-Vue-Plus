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
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 供应商主数据 Service 实现（SYS-MD-003）。
 *
 * <p>编码策略：{@code supplier_code} 新增时由 {@link IBizCodeGenerator} 按
 * {@link BizCodeType#SUPPLIER_CODE} 规则生成（pattern {@code G{seq4}}，例 {@code G0001}），
 * 编辑端点不允许覆盖。</p>
 *
 * <p>软删走 {@link DjsBaseServiceImpl#softDelete}（D03 _open-issues #32 回填基类）：
 * UNIQUE(tenant_id, supplier_code, del_unique) 保证软删后重启用同编码不冲突。</p>
 *
 * @author djs
 * @since SYS-MD-003
 */
@Slf4j
@Service
public class SupplierServiceImpl extends DjsBaseServiceImpl<SupplierMapper, Supplier> implements ISupplierService {

    private final IBizCodeGenerator bizCodeGenerator;

    public SupplierServiceImpl(SupplierMapper baseMapper, IBizCodeGenerator bizCodeGenerator) {
        super(baseMapper);
        this.bizCodeGenerator = bizCodeGenerator;
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
        if (entity.getBusinessStatus() == null) {
            entity.setBusinessStatus(1);
        }
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
        return baseMapper.updateById(entity);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus（Spring 注入的 {@code Converter} 单例）。
     *
     * <p>抽成 protected 是为了便于纯 Mockito 单测覆盖（避免启 Spring 上下文），
     * 业务调用方<b>不应</b>子类化本服务来绕开转换逻辑。</p>
     */
    protected Supplier toEntity(SupplierBo bo) {
        return MapstructUtils.convert(bo, Supplier.class);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        // TODO SYS-MD-003 → D5 BRD-EVENT-001 触发：抽 BizReferenceChecker 后回填校验
        // （t_breed_*.supplier_id / t_warehouse_supplier_record.supplier_id 等引用反查）。
        // 详 D02 _open-issues #12。
        return softDelete(ids, Supplier::new);
    }

    /**
     * 构造查询条件：supplier_name like / supplier_type eq / contact_phone like
     * / business_status eq / supplier_code eq。
     */
    private LambdaQueryWrapper<Supplier> buildQueryWrapper(SupplierQuery query) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(Supplier::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getSupplierName()), Supplier::getSupplierName, query.getSupplierName())
            .eq(StringUtils.isNotBlank(query.getSupplierCode()), Supplier::getSupplierCode, query.getSupplierCode())
            .eq(StringUtils.isNotBlank(query.getSupplierType()), Supplier::getSupplierType, query.getSupplierType())
            .like(StringUtils.isNotBlank(query.getContactPhone()), Supplier::getContactPhone, query.getContactPhone())
            .eq(Objects.nonNull(query.getBusinessStatus()), Supplier::getBusinessStatus, query.getBusinessStatus())
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
