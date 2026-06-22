package org.dromara.djs.warehouse.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.purchase.domain.bo.PurchaseInBo;
import org.dromara.djs.warehouse.purchase.domain.query.PurchaseInProductQuery;
import org.dromara.djs.warehouse.purchase.domain.query.PurchaseInQuery;
import org.dromara.djs.warehouse.purchase.domain.vo.PurchaseInProductVo;
import org.dromara.djs.warehouse.purchase.domain.vo.PurchaseInRecordVo;
import org.dromara.djs.warehouse.purchase.service.IWarehousePurchaseInService;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购入库简化版 Service 实现（D9 hotfix）。
 *
 * <h3>跨表事务一致性</h3>
 * <p>{@link #submit(PurchaseInBo)} 单 {@code @Transactional} 跨 3 步：</p>
 * <ol>
 *   <li>校验产品 + 计算并发安全的 UPSERT location_stock：先 UPDATE 增量（带 del_flag='0' 行锁）；
 *       affected=0 → INSERT 新行（含 product_name / product_unit / operatorId / isEnd=0）</li>
 *   <li>INSERT stock_flow（flow_type='purchase_in', inout_type='IN'）</li>
 * </ol>
 *
 * <h3>并发说明</h3>
 * <p>{@code t_warehouse_location_stock} DDL 无 {@code UNIQUE(tenant_id, product_id, location_id)}（V1 默认结构允许
 * 同 product+location 多行；总库存按 SUM 聚合）。本 service 顺序"UPDATE 失败 → INSERT"在极端并发下可能
 * 产生 2 行 product_stock；为 hotfix admin 单操作员场景可接受，正式 D11 采购模块会加 UNIQUE + ON DUPLICATE KEY。</p>
 *
 * @author djs
 * @since D9 hotfix purchase-in
 */
@Slf4j
@Service
public class WarehousePurchaseInServiceImpl implements IWarehousePurchaseInService {

    /** flow_type 字典 value（djs_flow_type 新增）。 */
    private static final String FLOW_PURCHASE_IN = "purchase_in";

    /** 出入库方向（DDL CHAR(3)）。 */
    private static final String INOUT_IN = "IN";

    private final StockFlowMapper stockFlowMapper;
    private final LocationStockMapper locationStockMapper;
    private final ProductInfoMapper productInfoMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ImageUrlResolver imageUrlResolver;

    public WarehousePurchaseInServiceImpl(StockFlowMapper stockFlowMapper,
                                          LocationStockMapper locationStockMapper,
                                          ProductInfoMapper productInfoMapper,
                                          LocationInfoMapper locationInfoMapper,
                                          IBizCodeGenerator bizCodeGenerator,
                                          ImageUrlResolver imageUrlResolver) {
        this.stockFlowMapper = stockFlowMapper;
        this.locationStockMapper = locationStockMapper;
        this.productInfoMapper = productInfoMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.imageUrlResolver = imageUrlResolver;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(PurchaseInBo bo) {
        return inbound(bo.getProductId(), bo.getLocationId(), bo.getQuantity(), FLOW_PURCHASE_IN, bo.getRemark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long inbound(Long productId, Long locationId, BigDecimal quantity, String flowType, String remark) {
        ProductInfo product = requireProduct(productId);
        requireLocation(locationId);
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("入库数量必须大于 0");
        }
        Long userId = LoginHelper.getUserId();

        // 1. UPSERT location_stock
        int updated = locationStockMapper.addByProductLocation(locationId, productId, quantity, userId);
        if (updated == 0) {
            // 不存在 → INSERT 新库存行（同事务，failure → 整体回滚）
            LocationStock fresh = new LocationStock();
            fresh.setLocationId(locationId);
            fresh.setProductId(productId);
            fresh.setProductName(product.getProductName());
            fresh.setProductUnit(product.getProductUnit());
            fresh.setProductStock(quantity);
            fresh.setIsEnd(0);
            fresh.setOperatorId(userId);
            // tenant_id / create_by / create_time / create_dept / update_by / update_time / del_flag
            // 均走 ruoyi DjsMetaObjectHandler.insertFill 自动填
            locationStockMapper.insert(fresh);
        }

        // 2. INSERT stock_flow
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_IN));
        flow.setFlowDate(new Date());
        flow.setProductId(productId);
        flow.setWarehouseId(locationId);
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(flowType);
        flow.setChangeNum(quantity);
        flow.setChangeQuantity(quantity);
        flow.setOperatorId(userId);
        flow.setRemark(remark);
        stockFlowMapper.insert(flow);

        return flow.getId();
    }

    @Override
    public TableDataInfo<PurchaseInRecordVo> queryPageList(PurchaseInQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<StockFlow> wrapper = buildWrapper(query);
        Page<StockFlowVo> page = stockFlowMapper.selectVoPage(pageQuery.build(), wrapper);
        List<PurchaseInRecordVo> rows = page.getRecords().stream().map(this::toRecordVo).toList();
        fillJoinNames(rows);
        Page<PurchaseInRecordVo> outPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        outPage.setRecords(rows);
        return TableDataInfo.build(outPage);
    }

    @Override
    public List<PurchaseInRecordVo> queryList(PurchaseInQuery query) {
        List<StockFlowVo> list = stockFlowMapper.selectVoList(buildWrapper(query));
        List<PurchaseInRecordVo> rows = list.stream().map(this::toRecordVo).toList();
        fillJoinNames(rows);
        return rows;
    }

    @Override
    public TableDataInfo<PurchaseInProductVo> queryProductPageList(PurchaseInProductQuery query, PageQuery pageQuery) {
        PurchaseInProductQuery q = query == null ? new PurchaseInProductQuery() : query;
        Page<PurchaseInProductVo> page = pageQuery.build();
        IPage<PurchaseInProductVo> result = productInfoMapper.selectPurchaseInProductPage(page, q);
        List<PurchaseInProductVo> rows = result.getRecords();
        fillProductExtras(rows);
        return TableDataInfo.build(result);
    }

    /**
     * 批量回填 imageUrl（resolver 4 层，禁 N+1）+ storeLocationName（多库位「、」拼接，单次 IN 查）。
     *
     * <p>外购商品 belongType 恒 null（外购无业态），resolver L2 兜底走 null → 全局默认图。</p>
     */
    private void fillProductExtras(List<PurchaseInProductVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // 1. imageUrl 批量回填（resolver L1 取 imageOssId；外购无 belongType → null）
        List<ImageUrlResolver.Item> resolveItems = rows.stream()
            .map(v -> new ImageUrlResolver.Item(v.getImageOssId(), null))
            .toList();
        List<String> urls = imageUrlResolver.resolveList(resolveItems);
        if (urls.size() == rows.size()) {
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).setImageUrl(urls.get(i));
            }
        }

        // 2. storeLocationName 批量回填（store_location_id 为 CSV 多库位，收集全部 id 单次 IN 查）
        Set<Long> locIds = new LinkedHashSet<>();
        for (PurchaseInProductVo vo : rows) {
            locIds.addAll(parseCsvIds(vo.getStoreLocationId()));
        }
        if (locIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = locationInfoMapper
            .selectList(new LambdaQueryWrapper<LocationInfo>().in(LocationInfo::getId, locIds))
            .stream()
            .collect(Collectors.toMap(LocationInfo::getId, LocationInfo::getLocationName, (a, b) -> a));
        for (PurchaseInProductVo vo : rows) {
            List<Long> ids = parseCsvIds(vo.getStoreLocationId());
            if (ids.isEmpty()) {
                continue;
            }
            String joined = ids.stream()
                .map(nameMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("、"));
            vo.setStoreLocationName(joined.isEmpty() ? null : joined);
        }
    }

    /**
     * CSV 库位 id 串 → Long 列表（去空白 / 去空项 / 跳过非法值）。
     */
    private static List<Long> parseCsvIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()) {
            try {
                ids.add(Long.valueOf(part));
            }
            catch (NumberFormatException ignore) {
                // 非法库位 id 串跳过，不阻塞列表回填
            }
        }
        return ids;
    }

    /**
     * 查产品，找不到抛异常。
     *
     * <p>protected 方便单测覆盖。</p>
     */
    protected ProductInfo requireProduct(Long productId) {
        if (productId == null) {
            throw new ServiceException("产品 ID 不能为空");
        }
        ProductInfo p = productInfoMapper.selectOne(
            new LambdaQueryWrapper<ProductInfo>().eq(ProductInfo::getId, productId).last("LIMIT 1"));
        if (p == null) {
            throw new ServiceException("产品不存在或已删除：" + productId);
        }
        return p;
    }

    /**
     * 查库位，找不到抛异常。
     *
     * <p>protected 方便单测覆盖。</p>
     */
    protected void requireLocation(Long locationId) {
        if (locationId == null) {
            throw new ServiceException("库位 ID 不能为空");
        }
        LocationInfo l = locationInfoMapper.selectOne(
            new LambdaQueryWrapper<LocationInfo>().eq(LocationInfo::getId, locationId).last("LIMIT 1"));
        if (l == null) {
            throw new ServiceException("库位不存在或已删除：" + locationId);
        }
    }

    /**
     * 生成流水号（复用 SYS-INFRA-004 BizCodeService）。
     *
     * <p>protected 方便单测固定返值。</p>
     */
    protected String generateFlowNo(String ioCode) {
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", ioCode);
        return bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx);
    }

    private LambdaQueryWrapper<StockFlow> buildWrapper(PurchaseInQuery query) {
        LambdaQueryWrapper<StockFlow> w = new LambdaQueryWrapper<StockFlow>()
            .eq(StockFlow::getFlowType, FLOW_PURCHASE_IN);
        if (query == null) {
            return w.orderByDesc(StockFlow::getFlowDate).orderByDesc(StockFlow::getId);
        }
        w.eq(query.getProductId() != null, StockFlow::getProductId, query.getProductId())
            .eq(query.getLocationId() != null, StockFlow::getWarehouseId, query.getLocationId())
            .ge(query.getDateFrom() != null, StockFlow::getFlowDate, query.getDateFrom())
            .le(query.getDateTo() != null, StockFlow::getFlowDate, query.getDateTo())
            .orderByDesc(StockFlow::getFlowDate)
            .orderByDesc(StockFlow::getId);
        return w;
    }

    /**
     * StockFlowVo → PurchaseInRecordVo（手工拷贝，避免引一套 MapStruct 仅服务 hotfix）。
     */
    private PurchaseInRecordVo toRecordVo(StockFlowVo src) {
        PurchaseInRecordVo dst = new PurchaseInRecordVo();
        dst.setId(src.getId());
        dst.setFlowNo(src.getFlowNo());
        dst.setFlowDate(src.getFlowDate());
        dst.setProductId(src.getProductId());
        dst.setLocationId(src.getWarehouseId());
        dst.setChangeQuantity(src.getChangeQuantity() == null ? BigDecimal.ZERO : src.getChangeQuantity());
        dst.setOperatorId(src.getOperatorId());
        dst.setRemark(src.getRemark());
        dst.setCreateTime(src.getCreateTime());
        return dst;
    }

    /**
     * 批量回填 productName / productUnit / locationName（单次 IN 查，避免 N+1）。
     */
    private void fillJoinNames(List<PurchaseInRecordVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> productIds = rows.stream()
            .map(PurchaseInRecordVo::getProductId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, ProductInfo> pm = productIds.isEmpty() ? Map.of() :
            productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds))
                .stream()
                .collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
        for (PurchaseInRecordVo vo : rows) {
            ProductInfo p = pm.get(vo.getProductId());
            if (p != null) {
                vo.setProductName(p.getProductName());
                vo.setProductUnit(p.getProductUnit());
            }
        }
        List<Long> locationIds = rows.stream()
            .map(PurchaseInRecordVo::getLocationId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, String> lm = locationIds.isEmpty() ? Map.of() :
            locationInfoMapper.selectList(new LambdaQueryWrapper<LocationInfo>().in(LocationInfo::getId, locationIds))
                .stream()
                .collect(Collectors.toMap(LocationInfo::getId, LocationInfo::getLocationName, (a, b) -> a));
        for (PurchaseInRecordVo vo : rows) {
            if (vo.getLocationId() != null) {
                vo.setLocationName(lm.get(vo.getLocationId()));
            }
        }
    }

}
