package org.dromara.djs.common.store.service.impl;

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
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.domain.bo.StoreBo;
import org.dromara.djs.common.store.domain.query.StoreQuery;
import org.dromara.djs.common.store.domain.vo.StoreVo;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.common.store.service.IStoreService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 门店主数据 Service 实现（SYS-MD-002）。
 *
 * <p>编码策略：{@code storeCode} 新增时由 {@link IBizCodeGenerator} 按
 * {@link BizCodeType#STORE_CODE} 规则生成（pattern {@code ST{seq4}}，例 {@code ST0001}），
 * 编辑端点不允许覆盖。</p>
 *
 * <p>软删通过基类 {@link DjsBaseServiceImpl#softDelete(Collection, java.util.function.Supplier)}
 * 显式循环 {@code updateById}（参基类注释）。下游业务表 wire 后需在 {@link #deleteWithValidByIds(Collection)}
 * 前置校验"是否被 {@code t_store_product_relation} / {@code t_store_sale_record} 等引用"。</p>
 *
 * @author djs
 * @since SYS-MD-002
 */
@Slf4j
@Service
public class StoreServiceImpl extends DjsBaseServiceImpl<StoreMapper, Store> implements IStoreService {

    private final IBizCodeGenerator bizCodeGenerator;

    public StoreServiceImpl(StoreMapper baseMapper, IBizCodeGenerator bizCodeGenerator) {
        super(baseMapper);
        this.bizCodeGenerator = bizCodeGenerator;
    }

    @Override
    public TableDataInfo<StoreVo> queryPageList(StoreQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Store> wrapper = buildQueryWrapper(query);
        Page<StoreVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    @Override
    public List<StoreVo> queryList(StoreQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public StoreVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(StoreBo bo) {
        Store entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("门店入参转换失败");
        }
        entity.setStoreCode(generateStoreCode());
        if (entity.getBusinessStatus() == null) {
            entity.setBusinessStatus(1);
        }
        if (StringUtils.isBlank(entity.getStoreType())) {
            entity.setStoreType("direct");
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(StoreBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("门店 ID 不能为空");
        }
        Store exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("门店不存在或已删除：" + bo.getId());
        }
        Store entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("门店入参转换失败");
        }
        // store_code 不允许通过编辑端点修改
        entity.setStoreCode(exists.getStoreCode());
        return baseMapper.updateById(entity);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus（Spring 注入的 {@code Converter} 单例）。
     *
     * <p>抽成 protected 是为了便于纯 Mockito 单测覆盖（避免启 Spring 上下文），
     * 业务调用方<b>不应</b>子类化本服务来绕开转换逻辑。</p>
     */
    protected Store toEntity(StoreBo bo) {
        return MapstructUtils.convert(bo, Store.class);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        // TODO SYS-MD-002 → D03+：业务表 wire 后，删除前需校验"是否被 t_store_product_relation /
        // t_store_sale_record / t_store_member 等引用"，引用存在则提示先解绑。
        // D05 BRD-EVENT-001 抽 BizReferenceChecker 后，本处统一改为声明式注册。
        return softDelete(ids, Store::new);
    }

    /**
     * 构造查询条件：storeName like / storeCode eq / storeType eq / businessStatus eq / contactPhone like。
     */
    private LambdaQueryWrapper<Store> buildQueryWrapper(StoreQuery query) {
        LambdaQueryWrapper<Store> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(Store::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getStoreName()), Store::getStoreName, query.getStoreName())
            .eq(StringUtils.isNotBlank(query.getStoreCode()), Store::getStoreCode, query.getStoreCode())
            .eq(StringUtils.isNotBlank(query.getStoreType()), Store::getStoreType, query.getStoreType())
            .eq(Objects.nonNull(query.getBusinessStatus()), Store::getBusinessStatus, query.getBusinessStatus())
            .like(StringUtils.isNotBlank(query.getContactPhone()), Store::getContactPhone, query.getContactPhone())
            .orderByDesc(Store::getId);
        return wrapper;
    }

    /**
     * 生成门店编码：走 {@link IBizCodeGenerator}，类型 {@link BizCodeType#STORE_CODE}。
     */
    private String generateStoreCode() {
        return bizCodeGenerator.generate(BizCodeType.STORE_CODE, Map.of());
    }

}
