package org.dromara.djs.warehouse.product.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.IImageLibraryService;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.util.I18nMessages;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.domain.vo.LocationPickerVo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo;
import org.dromara.djs.warehouse.product.domain.bo.ProductInfoBo;
import org.dromara.djs.warehouse.product.domain.query.ProductInfoQuery;
import org.dromara.djs.warehouse.product.domain.vo.ProductFlowRecordVo;
import org.dromara.djs.warehouse.product.domain.vo.ProductInfoVo;
import org.dromara.djs.warehouse.product.domain.vo.ProductProductionRecordVo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.service.IProductInfoService;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 产品 / 商品 / 礼盒 Service 实现（WMS-MD-002）。
 *
 * <p>差异化处理（共表 3 形态）：</p>
 * <ul>
 *   <li>{@code productType=1 自产} → belongType 必填校验</li>
 *   <li>{@code productType=2 外购} → supplierId 必填校验（buyClass 客户字典空时可空）</li>
 *   <li>{@code productType=3 礼盒} → 独立成品，自动 set belongType=gift_box（无组件清单/BOM）</li>
 * </ul>
 *
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}。</p>
 *
 * <p>{@code productId} 业务码 DB UNIQUE(tenant_id, product_id, del_unique) 兜底；
 * 重复时捕获 {@link DuplicateKeyException} 转译为 {@code product.code_duplicate}。</p>
 *
 * @author djs
 * @since WMS-MD-002
 */
@Slf4j
@Service
public class ProductInfoServiceImpl extends DjsBaseServiceImpl<ProductInfoMapper, ProductInfo> implements IProductInfoService {

    /** 礼盒归属类型固定值。 */
    private static final String BELONG_TYPE_GIFT_BOX = "gift_box";
    /** productType 三态。 */
    private static final int PRODUCT_TYPE_SELF = 1;
    private static final int PRODUCT_TYPE_PURCHASE = 2;
    private static final int PRODUCT_TYPE_GIFT_BOX = 3;

    /** 出入库方向（stock_flow.inout_type CHAR(3)）。 */
    private static final String INOUT_IN = "IN";
    /** 产品入库 flow_type（djs_flow_type）。 */
    private static final String FLOW_TYPE_PURCHASE_IN = "purchase_in";

    private final IImageLibraryService imageLibraryService;
    private final ImageUrlResolver imageUrlResolver;
    private final LocationInfoMapper locationInfoMapper;
    private final StockFlowMapper stockFlowMapper;
    private final LocationStockMapper locationStockMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IStockCheckService stockCheckService;

    public ProductInfoServiceImpl(ProductInfoMapper baseMapper,
                                  IImageLibraryService imageLibraryService,
                                  ImageUrlResolver imageUrlResolver,
                                  LocationInfoMapper locationInfoMapper,
                                  StockFlowMapper stockFlowMapper,
                                  LocationStockMapper locationStockMapper,
                                  IBizCodeGenerator bizCodeGenerator,
                                  IStockCheckService stockCheckService) {
        super(baseMapper);
        this.imageLibraryService = imageLibraryService;
        this.imageUrlResolver = imageUrlResolver;
        this.locationInfoMapper = locationInfoMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.locationStockMapper = locationStockMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.stockCheckService = stockCheckService;
    }

    @Override
    public TableDataInfo<ProductInfoVo> queryPageList(ProductInfoQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<ProductInfo> wrapper = buildQueryWrapper(query);
        Page<ProductInfoVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillImageUrls(page.getRecords());
        fillStoreLocationNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<ProductInfoVo> queryList(ProductInfoQuery query) {
        List<ProductInfoVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillImageUrls(list);
        fillStoreLocationNames(list);
        return list;
    }

    /**
     * 批量回填商品 imageUrl（IMG-LIB-001 4 层 resolver，禁 N+1）。L2 兜底用各自 belong_type。
     */
    private void fillImageUrls(List<ProductInfoVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<ImageUrlResolver.Item> items = new ArrayList<>(records.size());
        for (ProductInfoVo vo : records) {
            items.add(new ImageUrlResolver.Item(vo.getImageOssId(), vo.getBelongType()));
        }
        List<String> urls = imageUrlResolver.resolveList(items);
        if (urls.size() != records.size()) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setImageUrl(urls.get(i));
        }
    }

    @Override
    public ProductInfoVo queryById(Long id) {
        ProductInfoVo vo = baseMapper.selectVoById(id);
        if (vo == null) {
            return null;
        }
        // row31：详情「原材料产品」展示关联原材料的产品名称（productMaterial 自引用 FK → product_info.id）
        if (vo.getProductMaterial() != null) {
            ProductInfo material = baseMapper.selectById(vo.getProductMaterial());
            if (material != null) {
                vo.setProductMaterialName(material.getProductName());
            }
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(ProductInfoBo bo) {
        validateBoBeforeWrite(bo);
        // 礼盒自动设置 belongType（礼盒为独立成品，无组件清单）
        if (bo.getProductType() != null && bo.getProductType() == PRODUCT_TYPE_GIFT_BOX) {
            bo.setBelongType(BELONG_TYPE_GIFT_BOX);
        }
        ProductInfo entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("产品入参转换失败");
        }
        applyDefaultsBeforeInsert(entity);

        // IMG-LIB-001：image_oss_id 空且非手动 → 按 productName 自动匹配图库（匹配不上留 null，走 resolver 兜底）
        boolean manual = entity.getImageSource() != null && entity.getImageSource() == 1;
        if (StrUtil.isBlank(entity.getImageOssId()) && !manual) {
            String matched = imageLibraryService.match(entity.getProductName());
            entity.setImageOssId(matched);
            entity.setImageSource(0);
        } else if (StrUtil.isNotBlank(entity.getImageOssId())) {
            entity.setImageSource(1);
        }

        int rows;
        try {
            rows = baseMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new ServiceException(I18nMessages.t("product.code_duplicate", bo.getProductId()));
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(ProductInfoBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("产品 ID 不能为空");
        }
        ProductInfo exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("产品不存在或已删除：" + bo.getId());
        }
        // 编辑路径下 productType + productId 双锁（拉回旧值）
        bo.setProductType(exists.getProductType());
        validateBoBeforeWrite(bo);
        if (exists.getProductType() != null && exists.getProductType() == PRODUCT_TYPE_GIFT_BOX) {
            bo.setBelongType(BELONG_TYPE_GIFT_BOX);
        }

        ProductInfo entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("产品入参转换失败");
        }
        // 编辑端点不允许改 productId / productType
        entity.setProductId(exists.getProductId());
        entity.setProductType(exists.getProductType());
        // IMG-LIB-001：用户在编辑里改了图 → 标记手动（image_source=1），后续 rematch 不覆盖
        if (StrUtil.isNotBlank(entity.getImageOssId())
            && !entity.getImageOssId().equals(exists.getImageOssId())) {
            entity.setImageSource(1);
        }
        return baseMapper.updateById(entity);
    }

    @Override
    public int updateStatus(Long id, Integer status) {
        if (id == null) {
            throw new ServiceException("产品 ID 不能为空");
        }
        if (status == null) {
            throw new ServiceException("产品状态不能为空");
        }
        // 仅更新 product_status 单字段（updateById 默认非 null 才更新，updateBy/updateTime 由 MetaObjectHandler 自动填）
        ProductInfo entity = new ProductInfo();
        entity.setId(id);
        entity.setProductStatus(status);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        // 删除前校验：每个产品都不能有非零库存 / 被作为原材料引用
        for (Long id : ids) {
            long activeStock = baseMapper.countActiveStockByProduct(id);
            if (activeStock > 0) {
                String name = lookupProductName(id);
                throw new ServiceException(I18nMessages.t("product.has_stock", name));
            }
            long referenced = baseMapper.countReferencedAsMaterial(id);
            if (referenced > 0) {
                String name = lookupProductName(id);
                throw new ServiceException(I18nMessages.t("product.referenced_as_material", name));
            }
        }
        return softDelete(ids);
    }

    // ---------- 内部辅助 ----------

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus，protected 便于单测覆盖。
     */
    protected ProductInfo toEntity(ProductInfoBo bo) {
        return MapstructUtils.convert(bo, ProductInfo.class);
    }

    /**
     * 共表 3 形态差异化校验（写入路径前置）。
     */
    private void validateBoBeforeWrite(ProductInfoBo bo) {
        Integer type = bo.getProductType();
        if (type == null) {
            throw new ServiceException(I18nMessages.t("product.type.required"));
        }
        switch (type) {
            case PRODUCT_TYPE_SELF -> {
                if (StrUtil.isBlank(bo.getBelongType())) {
                    throw new ServiceException(I18nMessages.t("product.belong_type.required"));
                }
                // 生产产品(product_attr=1) 必填规格（doc/14 §4；打包卡按规格展示，「按重量/散装」非空即合规）
                if (bo.getProductAttr() != null && bo.getProductAttr() == 1
                    && StrUtil.isBlank(bo.getProductSpec())) {
                    throw new ServiceException(I18nMessages.t("product.spec.required"));
                }
            }
            case PRODUCT_TYPE_PURCHASE -> {
                if (bo.getSupplierId() == null) {
                    throw new ServiceException(I18nMessages.t("product.supplier.required"));
                }
            }
            case PRODUCT_TYPE_GIFT_BOX -> {
                // 礼盒为独立成品，无额外必填（belongType 由 service 自动 set=gift_box）
            }
            default -> throw new ServiceException("不支持的产品类型：" + type);
        }
    }

    /**
     * 插入前默认值兜底：productStatus 默认 0（正常）；isDelivery 默认 1；isBuyOut 默认 0。
     */
    private void applyDefaultsBeforeInsert(ProductInfo entity) {
        if (entity.getProductStatus() == null) {
            entity.setProductStatus(0);
        }
        if (entity.getIsDelivery() == null) {
            entity.setIsDelivery(1);
        }
        if (entity.getIsBuyOut() == null) {
            entity.setIsBuyOut(0);
        }
    }

    /**
     * 拉产品名称用于错误提示；查不到回退到 id 字符串。
     */
    private String lookupProductName(Long id) {
        ProductInfo p = baseMapper.selectById(id);
        return p != null && p.getProductName() != null ? p.getProductName() : String.valueOf(id);
    }

    private LambdaQueryWrapper<ProductInfo> buildQueryWrapper(ProductInfoQuery query) {
        LambdaQueryWrapper<ProductInfo> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(ProductInfo::getId);
        }
        boolean hasTypeSet = query.getProductTypes() != null && !query.getProductTypes().isEmpty();
        wrapper.eq(StringUtils.isNotBlank(query.getProductId()), ProductInfo::getProductId, query.getProductId())
            .like(StringUtils.isNotBlank(query.getProductName()), ProductInfo::getProductName, query.getProductName())
            // productTypes 集合优先（产品配置入口 {1,3}）；否则退回单值 productType
            .in(hasTypeSet, ProductInfo::getProductType, query.getProductTypes())
            .eq(!hasTypeSet && query.getProductType() != null, ProductInfo::getProductType, query.getProductType())
            .eq(StringUtils.isNotBlank(query.getBelongType()), ProductInfo::getBelongType, query.getBelongType())
            // belongTypes 集合（其他产品打包入口 {egg, dry_good, other}）落 belong_type IN (...)
            .in(CollUtil.isNotEmpty(query.getBelongTypes()), ProductInfo::getBelongType, query.getBelongTypes())
            .eq(StringUtils.isNotBlank(query.getBuyClass()), ProductInfo::getBuyClass, query.getBuyClass())
            // 是否支持外购（原型「是否支持外购」筛选项）
            .eq(query.getIsBuyOut() != null, ProductInfo::getIsBuyOut, query.getIsBuyOut())
            .eq(query.getProductWorkshop() != null, ProductInfo::getProductWorkshop, query.getProductWorkshop())
            // 产品属性（打包目标成品 product_attr=1 / 原材料=2，取数逻辑 doc#13）
            .eq(query.getProductAttr() != null, ProductInfo::getProductAttr, query.getProductAttr())
            // 关联原材料（自引用 FK → product_info.id 雪花 id）
            .eq(query.getProductMaterial() != null, ProductInfo::getProductMaterial, query.getProductMaterial())
            // 关联原材料集合（findings 步12：果蔬打包按「本次领用原料产品 ID」反查命中成品）
            .in(CollUtil.isNotEmpty(query.getProductMaterials()), ProductInfo::getProductMaterial, query.getProductMaterials())
            .eq(StringUtils.isNotBlank(query.getStoreLocationId()), ProductInfo::getStoreLocationId, query.getStoreLocationId())
            .eq(query.getProductStatus() != null, ProductInfo::getProductStatus, query.getProductStatus())
            .eq(query.getUpdateBy() != null, ProductInfo::getUpdateBy, query.getUpdateBy())
            .ge(query.getUpdateBeginTime() != null, ProductInfo::getUpdateTime, query.getUpdateBeginTime())
            .le(query.getUpdateEndTime() != null, ProductInfo::getUpdateTime, query.getUpdateEndTime())
            .orderByDesc(ProductInfo::getId);
        return wrapper;
    }

    /**
     * 批量回填 {@code storeLocationName}（store_location_id 逗号分隔 → location_info.location_name 逗号拼接，禁 N+1）。
     */
    private void fillStoreLocationNames(List<ProductInfoVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 收集全部库位 ID（逗号分隔展开 + 去重）
        List<Long> allLocIds = records.stream()
            .map(ProductInfoVo::getStoreLocationId)
            .filter(StringUtils::isNotBlank)
            .flatMap(s -> java.util.Arrays.stream(s.split(",")))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .map(s -> {
                try {
                    return Long.valueOf(s);
                } catch (NumberFormatException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (allLocIds.isEmpty()) {
            return;
        }
        List<LocationInfo> locations = locationInfoMapper.selectList(
            new LambdaQueryWrapper<LocationInfo>().in(LocationInfo::getId, allLocIds));
        Map<Long, String> nameMap = locations.stream()
            .collect(Collectors.toMap(LocationInfo::getId, LocationInfo::getLocationName, (a, b) -> a));
        for (ProductInfoVo vo : records) {
            if (StringUtils.isBlank(vo.getStoreLocationId())) {
                continue;
            }
            String names = java.util.Arrays.stream(vo.getStoreLocationId().split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(s -> {
                    try {
                        return nameMap.get(Long.valueOf(s));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
            vo.setStoreLocationName(StringUtils.isBlank(names) ? null : names);
        }
    }

    @Override
    public List<ProductProductionRecordVo> queryProductionRecords(Long productId, Date produceDate, String produceType) {
        if (productId == null) {
            throw new ServiceException("产品 ID 不能为空");
        }
        String type = StringUtils.isBlank(produceType) ? null : produceType.trim();
        List<ProductProductionRecordVo> records =
            baseMapper.selectProductionRecords(productId, produceDate, type);
        // 标准计重 / 差异源表无独立列，留 NULL；若后续 schema 补 standard_weight，此处计算 diff = 生产重量 - 标准计重
        for (ProductProductionRecordVo r : records) {
            BigDecimal std = r.getStandardWeight();
            if (std != null && r.getProduceWeight() != null) {
                r.setDiffWeight(r.getProduceWeight().subtract(std));
            }
        }
        return records;
    }

    @Override
    public List<ProductFlowRecordVo> queryFlowRecords(Long productId, Date bizDate) {
        if (productId == null) {
            throw new ServiceException("产品 ID 不能为空");
        }
        return baseMapper.selectFlowRecords(productId, bizDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long inbound(ProductStockInBo bo) {
        if (bo == null || bo.getProductId() == null || bo.getLocationId() == null
            || bo.getQuantity() == null || bo.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("入库参数不合法");
        }
        ProductInfo product = baseMapper.selectById(bo.getProductId());
        if (product == null) {
            throw new ServiceException("产品不存在或已删除：" + bo.getProductId());
        }
        // 配置库位强制：商品/产品配了 store_location_id（逗号分隔多库位）时，
        // 只能入配置库位之一；前端已锁库位下拉，此处后端兜底防绕过（118.2 / 119.2）。
        enforceConfiguredLocation(product, bo.getLocationId());
        // 盘点锁：库位被盘点锁定时禁止入库（与其他写入路径一致）
        stockCheckService.assertLocationUnlocked(bo.getLocationId());
        Long userId = LoginHelper.getUserId();

        // 1. INSERT 入库流水（IN / purchase_in）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_IN));
        flow.setFlowDate(new Date());
        flow.setProductId(bo.getProductId());
        flow.setWarehouseId(bo.getLocationId());
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_TYPE_PURCHASE_IN);
        flow.setChangeNum(bo.getQuantity());
        flow.setChangeQuantity(bo.getQuantity());
        flow.setOperatorId(userId);
        flow.setRemark(bo.getRemark());
        stockFlowMapper.insert(flow);

        // 2. UPDATE location_stock 加库存；无对应行 → 兜底 INSERT 新库存行
        int affected = locationStockMapper.addByProductLocation(bo.getLocationId(), bo.getProductId(), bo.getQuantity(), userId);
        if (affected == 0) {
            LocationStock stock = new LocationStock();
            stock.setLocationId(bo.getLocationId());
            stock.setProductId(bo.getProductId());
            stock.setProductName(product.getProductName());
            stock.setProductStock(bo.getQuantity());
            stock.setProductUnit(product.getProductUnit());
            stock.setIsEnd(0);
            stock.setOperatorId(userId);
            locationStockMapper.insert(stock);
        }

        // 路B 首次采购入库自配置：采购入库上下文 + 商品未配存储库位 → 回写本次库位为该商品存储库位
        if (Boolean.TRUE.equals(bo.getAutoConfigLocation()) && StringUtils.isBlank(product.getStoreLocationId())) {
            ProductInfo upd = new ProductInfo();
            upd.setId(product.getId());
            upd.setStoreLocationId(String.valueOf(bo.getLocationId()));
            baseMapper.updateById(upd);
        }
        return flow.getId();
    }

    /**
     * 配置库位约束校验：商品/产品在配置里设了 {@code store_location_id}
     * （逗号分隔，可多库位）时，入库 {@code locationId} 必须命中其中之一；
     * 否则抛 {@link ServiceException}。未配置库位的产品则不限制（任意库位可入）。
     *
     * @param product    入库产品
     * @param locationId 前端传入的入库库位 ID
     */
    private void enforceConfiguredLocation(ProductInfo product, Long locationId) {
        String configured = product.getStoreLocationId();
        if (StringUtils.isBlank(configured)) {
            return;
        }
        boolean matched = java.util.Arrays.stream(configured.split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .map(s -> {
                try {
                    return Long.valueOf(s);
                } catch (NumberFormatException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .anyMatch(id -> id.equals(locationId));
        if (!matched) {
            throw new ServiceException("该产品已配置专属存储库位，只能入库到配置库位");
        }
    }

    @Override
    public List<LocationPickerVo> queryStoreLocations(Long productId, String locationType) {
        if (productId == null) {
            return Collections.emptyList();
        }
        ProductInfo product = baseMapper.selectById(productId);
        if (product == null || StringUtils.isBlank(product.getStoreLocationId())) {
            // 该产品未配置存储仓库 → 返回空，前端回落自由选库位
            return Collections.emptyList();
        }
        // store_location_id 逗号分隔库位 ID 列表（doc/11 §2.5；V2 改关联表）
        List<Long> locationIds = new ArrayList<>();
        for (String token : product.getStoreLocationId().split(",")) {
            String trimmed = token.trim();
            if (StringUtils.isNotBlank(trimmed)) {
                try {
                    locationIds.add(Long.valueOf(trimmed));
                } catch (NumberFormatException ignore) {
                    // 脏数据（非数字 ID）跳过，不拖垮整体取数
                }
            }
        }
        if (locationIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 仅返启用库位（location_status=1），可选 locationType 过滤，保持配置顺序
        List<LocationInfo> rows = locationInfoMapper.selectList(
            new LambdaQueryWrapper<LocationInfo>()
                .in(LocationInfo::getId, locationIds)
                .eq(LocationInfo::getLocationStatus, 1)
                .eq(StringUtils.isNotBlank(locationType), LocationInfo::getLocationType, locationType));
        Map<Long, LocationInfo> rowMap = rows.stream()
            .collect(Collectors.toMap(LocationInfo::getId, l -> l, (a, b) -> a));
        List<LocationPickerVo> result = new ArrayList<>(locationIds.size());
        for (Long id : locationIds) {
            LocationInfo l = rowMap.get(id);
            if (l == null) {
                continue;
            }
            LocationPickerVo vo = new LocationPickerVo();
            vo.setId(l.getId());
            vo.setLocationCode(l.getLocationCode());
            vo.setLocationName(l.getLocationName());
            vo.setLocationType(l.getLocationType());
            result.add(vo);
        }
        return result;
    }

    /**
     * 生成流水号（复用 SYS-INFRA-004 BizCodeService；ioCode = IN/OT）。
     */
    private String generateFlowNo(String ioCode) {
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", ioCode);
        return bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx);
    }

}
