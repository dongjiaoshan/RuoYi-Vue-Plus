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
import org.dromara.djs.common.supplier.api.SupplierDealProvider;
import org.dromara.djs.common.supplier.api.SupplierDealStat;
import org.dromara.djs.common.supplier.api.SupplierDealVo;
import org.dromara.djs.common.supplier.domain.vo.SupplierVo;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.common.supplier.service.ISupplierService;
import org.dromara.djs.common.validate.BizReferenceChecker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
    /** 跨模块交易明细提供方（药品入库 + 物资采购入库），compute-on-read 算交易次数/购入量。 */
    private final ObjectProvider<SupplierDealProvider> dealProviders;

    public SupplierServiceImpl(SupplierMapper baseMapper,
                               IBizCodeGenerator bizCodeGenerator,
                               BizReferenceChecker bizReferenceChecker,
                               ObjectProvider<SupplierDealProvider> dealProviders) {
        super(baseMapper);
        this.bizCodeGenerator = bizCodeGenerator;
        this.bizReferenceChecker = bizReferenceChecker;
        this.dealProviders = dealProviders;
    }

    @Override
    public TableDataInfo<SupplierVo> queryPageList(SupplierQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Supplier> wrapper = buildQueryWrapper(query);
        Page<SupplierVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        // 交易次数 = 该供应商采购入库记录数；购入量 = 采购入库数量合计
        // （口径同详情「交易明细」tab：药品入库 + 物资采购入库，read-only compute-on-read 不持久化）
        // 批量聚合避免 N+1：取本页非空 id 列表 → 每个 provider 一条 GROUP BY → 合并回填。
        List<Long> ids = page.getRecords().stream()
            .map(SupplierVo::getId)
            .filter(Objects::nonNull)
            .toList();
        if (!ids.isEmpty()) {
            Map<Long, SupplierDealStat> statMap = aggregateStats(ids);
            page.getRecords().forEach(vo -> {
                if (vo.getId() == null) {
                    return;
                }
                SupplierDealStat stat = statMap.getOrDefault(vo.getId(), SupplierDealStat.ZERO);
                vo.setDealCount(stat.dealCount());
                vo.setPurchaseQty(stat.purchaseQty());
            });
        }
        return TableDataInfo.build(page);
    }

    /**
     * 跨所有 provider 批量聚合本页供应商的交易统计（每 provider 一条查询），合并成 {@code id → stat}。
     *
     * @param ids 本页非空供应商 ID（调用方保证非空）
     * @return 合并后的统计 Map（无交易的 id 不在结果中）
     */
    private Map<Long, SupplierDealStat> aggregateStats(Collection<Long> ids) {
        Map<Long, SupplierDealStat> merged = new HashMap<>();
        dealProviders.forEach(p ->
            p.aggregateStatsBySupplierIds(ids).forEach((id, stat) ->
                merged.merge(id, stat, SupplierDealStat::plus)));
        return merged;
    }

    /** compute-on-read 回填交易次数/购入量（详情单条用；不持久化，避免冗余字段不一致）。 */
    private void fillDealStat(SupplierVo vo) {
        if (vo == null || vo.getId() == null) {
            return;
        }
        List<SupplierDealVo> deals = new ArrayList<>();
        dealProviders.forEach(p -> deals.addAll(p.aggregateBySupplier(vo.getId())));
        vo.setDealCount(deals.size());
        BigDecimal sum = deals.stream()
            .map(SupplierDealVo::getDealQuantity)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setPurchaseQty(sum);
    }

    @Override
    public List<SupplierVo> queryList(SupplierQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public SupplierVo queryById(Long id) {
        SupplierVo vo = baseMapper.selectVoById(id);
        fillDealStat(vo);
        return vo;
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
