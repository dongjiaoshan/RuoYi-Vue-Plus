package org.dromara.djs.store.operation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.store.operation.domain.StoreProductRelation;
import org.dromara.djs.store.operation.domain.bo.StoreProductRelationSyncBo;
import org.dromara.djs.store.operation.domain.vo.StoreProductCandidateVo;
import org.dromara.djs.store.operation.domain.vo.StoreProductRelationVo;
import org.dromara.djs.store.operation.mapper.StoreProductRelationMapper;
import org.dromara.djs.store.operation.service.IStoreProductRelationService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 门店产品关联 Service 实现（STR-OP-001）。
 *
 * <h3>全量 diff 同步（{@link #syncRelations}）</h3>
 * <p>admin {@code el-transfer} 保存时提交门店当前右侧全部产品 ID。service 取该门店现有活跃关联，
 * 对比目标集合：目标有而现存无 → INSERT（{@code is_active=1}）；现存有而目标无 → softDelete。
 * 单 {@code @Transactional} 保证原子。</p>
 *
 * <p>{@code productId} 是产品雪花主键（= {@code t_warehouse_product_info.id}）。列表 VO 的产品名 /
 * 单位 / 规格由本类内存 JOIN {@code ProductInfoMapper} 回填（一次性批量查，避免 N+1）。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Slf4j
@Service
public class StoreProductRelationServiceImpl
    extends DjsBaseServiceImpl<StoreProductRelationMapper, StoreProductRelation>
    implements IStoreProductRelationService {

    /**
     * 启用状态（字典 {@code djs_common_status}）。
     */
    private static final int ACTIVE = 1;

    private final ProductInfoMapper productInfoMapper;

    public StoreProductRelationServiceImpl(StoreProductRelationMapper baseMapper,
                                           ProductInfoMapper productInfoMapper) {
        super(baseMapper);
        this.productInfoMapper = productInfoMapper;
    }

    @Override
    public List<StoreProductRelationVo> listByStore(Long storeId) {
        if (storeId == null) {
            return new ArrayList<>();
        }
        List<StoreProductRelation> rows = baseMapper.selectList(
            new LambdaQueryWrapper<StoreProductRelation>()
                .eq(StoreProductRelation::getStoreId, storeId)
                .orderByAsc(StoreProductRelation::getId));
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, ProductInfo> productMap = loadProductMap(
            rows.stream().map(StoreProductRelation::getProductId).collect(Collectors.toList()));
        List<StoreProductRelationVo> vos = new ArrayList<>(rows.size());
        for (StoreProductRelation r : rows) {
            StoreProductRelationVo vo = new StoreProductRelationVo();
            vo.setId(r.getId());
            vo.setStoreId(r.getStoreId());
            vo.setProductId(r.getProductId());
            vo.setIsActive(r.getIsActive());
            ProductInfo p = productMap.get(r.getProductId());
            if (p != null) {
                vo.setProductName(p.getProductName());
                vo.setProductUnit(p.getProductUnit());
                vo.setProductSpec(p.getProductSpec());
            }
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public List<StoreProductCandidateVo> listCandidates() {
        List<ProductInfo> products = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>().orderByAsc(ProductInfo::getId));
        return products.stream().map(p -> {
            StoreProductCandidateVo vo = new StoreProductCandidateVo();
            vo.setId(p.getId());
            vo.setProductCode(p.getProductId());
            vo.setProductName(p.getProductName());
            vo.setProductUnit(p.getProductUnit());
            vo.setProductSpec(p.getProductSpec());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncRelations(StoreProductRelationSyncBo bo) {
        Long storeId = bo.getStoreId();
        // 去重 + 保序目标集合
        Set<Long> targetIds = new LinkedHashSet<>(bo.getProductIds());

        // 现存活跃关联
        List<StoreProductRelation> existing = baseMapper.selectList(
            new LambdaQueryWrapper<StoreProductRelation>()
                .eq(StoreProductRelation::getStoreId, storeId));
        Set<Long> existingProductIds = existing.stream()
            .map(StoreProductRelation::getProductId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        int changed = 0;

        // 1. 新增：目标有而现存无（校验产品存在）
        List<Long> toAdd = targetIds.stream()
            .filter(pid -> !existingProductIds.contains(pid))
            .collect(Collectors.toList());
        if (!toAdd.isEmpty()) {
            Map<Long, ProductInfo> productMap = loadProductMap(toAdd);
            for (Long pid : toAdd) {
                if (!productMap.containsKey(pid)) {
                    throw new ServiceException("产品不存在或已删除：" + pid);
                }
                StoreProductRelation r = new StoreProductRelation();
                r.setStoreId(storeId);
                r.setProductId(pid);
                r.setIsActive(ACTIVE);
                changed += baseMapper.insert(r);
            }
        }

        // 2. 移除：现存有而目标无 → softDelete
        List<Long> toRemoveIds = existing.stream()
            .filter(r -> !targetIds.contains(r.getProductId()))
            .map(StoreProductRelation::getId)
            .collect(Collectors.toList());
        if (!toRemoveIds.isEmpty()) {
            changed += softDelete(toRemoveIds);
        }

        return changed;
    }

    /**
     * 批量按产品雪花 ID 取产品（活跃即查，含已软删走 MP 逻辑删除自动过滤）。
     */
    private Map<Long, ProductInfo> loadProductMap(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductInfo> products = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds));
        return products.stream().collect(Collectors.toMap(ProductInfo::getId, Function.identity(), (a, b) -> a));
    }

}
