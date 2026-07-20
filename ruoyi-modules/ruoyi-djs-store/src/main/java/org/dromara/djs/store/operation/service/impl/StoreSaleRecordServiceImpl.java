package org.dromara.djs.store.operation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.common.store.service.IStoreService;
import org.dromara.djs.store.operation.domain.StoreProductRelation;
import org.dromara.djs.store.operation.domain.StoreSaleRecord;
import org.dromara.djs.store.operation.domain.bo.StoreSaleRecordBo;
import org.dromara.djs.store.operation.domain.query.StoreSaleRecordQuery;
import org.dromara.djs.store.operation.domain.vo.StoreSaleImportVo;
import org.dromara.djs.store.operation.domain.vo.StoreSaleRecordVo;
import org.dromara.djs.store.operation.mapper.StoreProductRelationMapper;
import org.dromara.djs.store.operation.mapper.StoreSaleRecordMapper;
import org.dromara.djs.store.operation.service.IStoreSaleRecordService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 门店销售流水 Service 实现（STR-OP-001）。
 *
 * <h3>手录（{@link #addManual}）</h3>
 * <p>产品名 / 单位按 {@code productId} 反查 {@code t_warehouse_product_info} 冗余落库，
 * 不信前端传值；{@code source='manual'}，{@code operatorId} 取当前登录用户。</p>
 *
 * <h3>Excel 导入（{@link #importRows}）</h3>
 * <p>逐行按「产品名 + 该门店关联表」反查 {@code productId}：产品须存在于该门店的产品关联表
 * （{@code t_store_product_relation}），否则该行抛 {@code ServiceException} 由 listener 计入失败统计。
 * 校验通过批量 {@code insertBatch}，{@code source='excel_import'}。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Slf4j
@Service
public class StoreSaleRecordServiceImpl
    extends DjsBaseServiceImpl<StoreSaleRecordMapper, StoreSaleRecord>
    implements IStoreSaleRecordService {

    /**
     * 数据来源（字典 {@code djs_sale_source}）。
     */
    private static final String SOURCE_MANUAL = "manual";
    private static final String SOURCE_EXCEL = "excel_import";

    private final ProductInfoMapper productInfoMapper;
    private final StoreMapper storeMapper;
    private final StoreProductRelationMapper relationMapper;
    private final IStoreService storeService;

    public StoreSaleRecordServiceImpl(StoreSaleRecordMapper baseMapper,
                                      ProductInfoMapper productInfoMapper,
                                      StoreMapper storeMapper,
                                      StoreProductRelationMapper relationMapper,
                                      IStoreService storeService) {
        super(baseMapper);
        this.productInfoMapper = productInfoMapper;
        this.storeMapper = storeMapper;
        this.relationMapper = relationMapper;
        this.storeService = storeService;
    }

    @Override
    public TableDataInfo<StoreSaleRecordVo> queryPageList(StoreSaleRecordQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<StoreSaleRecord> w = new LambdaQueryWrapper<>();
        if (query != null) {
            w.eq(query.getStoreId() != null, StoreSaleRecord::getStoreId, query.getStoreId())
                .eq(query.getProductId() != null, StoreSaleRecord::getProductId, query.getProductId())
                .eq(query.getSource() != null && !query.getSource().isBlank(),
                    StoreSaleRecord::getSource, query.getSource())
                .ge(query.getSaleDateFrom() != null, StoreSaleRecord::getSaleDate, query.getSaleDateFrom())
                .le(query.getSaleDateTo() != null, StoreSaleRecord::getSaleDate, query.getSaleDateTo());
        }
        w.orderByDesc(StoreSaleRecord::getSaleDate).orderByDesc(StoreSaleRecord::getId);

        Page<StoreSaleRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), w);
        fillStoreNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public StoreSaleRecordVo queryById(Long id) {
        StoreSaleRecordVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillStoreNames(List.of(vo));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addManual(StoreSaleRecordBo bo) {
        // 已终止合作门店禁止销售录入
        storeService.assertStoreActive(bo.getStoreId());
        ProductInfo product = productInfoMapper.selectById(bo.getProductId());
        if (product == null) {
            throw new ServiceException("产品不存在或已删除：" + bo.getProductId());
        }
        if (storeMapper.selectById(bo.getStoreId()) == null) {
            throw new ServiceException("门店不存在或已删除：" + bo.getStoreId());
        }
        StoreSaleRecord record = new StoreSaleRecord();
        record.setStoreId(bo.getStoreId());
        record.setProductId(bo.getProductId());
        record.setProductName(product.getProductName());
        record.setSaleUnit(product.getProductUnit());
        record.setSaleDate(bo.getSaleDate());
        record.setSaleQty(bo.getSaleQty());
        record.setSaleAmount(bo.getSaleAmount());
        record.setOperatorId(currentUserIdSafe());
        record.setSource(SOURCE_MANUAL);
        record.setRemark(bo.getRemark());
        baseMapper.insert(record);
        return record.getId();
    }

    @Override
    public int deleteByIds(Collection<Long> ids) {
        return softDelete(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importRows(Long storeId, List<StoreSaleImportVo> rows, Long operatorId) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 已终止合作门店禁止导入销售流水（口径同 addManual；自含门店存在性检查）
        storeService.assertStoreActive(storeId);
        // 该门店已关联产品 → 按产品名建索引（导入只允许录入已关联 SKU）
        Map<String, ProductInfo> nameToProduct = loadStoreProductsByName(storeId);

        List<StoreSaleRecord> batch = new ArrayList<>(rows.size());
        int line = 0;
        for (StoreSaleImportVo row : rows) {
            line++;
            String name = row.getProductName() == null ? "" : row.getProductName().trim();
            if (name.isEmpty()) {
                throw new ServiceException("第 " + line + " 行产品名称为空");
            }
            ProductInfo product = nameToProduct.get(name);
            if (product == null) {
                throw new ServiceException("第 " + line + " 行产品「" + name + "」未在该门店产品关联中，请先配置产品关联");
            }
            if (row.getSaleDate() == null) {
                throw new ServiceException("第 " + line + " 行销售日期为空");
            }
            if (row.getSaleQty() == null || row.getSaleQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ServiceException("第 " + line + " 行销售数量须大于 0");
            }
            if (row.getSaleAmount() == null || row.getSaleAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ServiceException("第 " + line + " 行销售总额须大于 0");
            }
            StoreSaleRecord record = new StoreSaleRecord();
            record.setStoreId(storeId);
            record.setProductId(product.getId());
            record.setProductName(product.getProductName());
            record.setSaleUnit(product.getProductUnit());
            record.setSaleDate(row.getSaleDate());
            record.setSaleQty(row.getSaleQty());
            record.setSaleAmount(row.getSaleAmount());
            record.setOperatorId(operatorId);
            record.setSource(SOURCE_EXCEL);
            batch.add(record);
        }
        baseMapper.insertBatch(batch);
        return batch.size();
    }

    /**
     * 取该门店已关联（活跃）产品，按产品名建索引（同名取第一条）。
     */
    private Map<String, ProductInfo> loadStoreProductsByName(Long storeId) {
        List<StoreProductRelation> relations = relationMapper.selectList(
            new LambdaQueryWrapper<StoreProductRelation>()
                .eq(StoreProductRelation::getStoreId, storeId));
        if (relations.isEmpty()) {
            return Map.of();
        }
        List<Long> productIds = relations.stream()
            .map(StoreProductRelation::getProductId).collect(Collectors.toList());
        List<ProductInfo> products = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds));
        return products.stream().collect(Collectors.toMap(
            ProductInfo::getProductName, Function.identity(), (a, b) -> a));
    }

    /**
     * 批量 JOIN 回填门店名（避免 N+1）。
     */
    private void fillStoreNames(List<StoreSaleRecordVo> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        Set<Long> storeIds = vos.stream()
            .map(StoreSaleRecordVo::getStoreId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        if (storeIds.isEmpty()) {
            return;
        }
        Map<Long, String> storeNameMap = storeMapper.selectList(
                new LambdaQueryWrapper<Store>().in(Store::getId, storeIds))
            .stream().collect(Collectors.toMap(Store::getId, Store::getStoreName, (a, b) -> a));
        for (StoreSaleRecordVo vo : vos) {
            vo.setStoreName(storeNameMap.get(vo.getStoreId()));
        }
    }

}
