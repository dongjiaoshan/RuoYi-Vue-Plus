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
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.image.service.IImageLibraryService;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.util.I18nMessages;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.bo.GiftBoxBo;
import org.dromara.djs.warehouse.product.domain.bo.ProductInfoBo;
import org.dromara.djs.warehouse.product.domain.query.ProductInfoQuery;
import org.dromara.djs.warehouse.product.domain.vo.GiftBoxVo;
import org.dromara.djs.warehouse.product.domain.vo.ProductInfoVo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.service.IGiftBoxService;
import org.dromara.djs.warehouse.product.service.IProductInfoService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 产品 / 商品 / 礼盒 Service 实现（WMS-MD-002）。
 *
 * <p>差异化处理（共表 3 形态）：</p>
 * <ul>
 *   <li>{@code productType=1 自产} → belongType 必填校验</li>
 *   <li>{@code productType=2 外购} → supplierId 必填校验（buyClass 客户字典空时可空）</li>
 *   <li>{@code productType=3 礼盒} → giftComponents.size ≥ 1；自动 set belongType=gift_box；
 *       组件 SKU 校验"不是另一个礼盒"（不嵌套）；编辑时覆盖式 replace 组件</li>
 * </ul>
 *
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}；同步级联软删该礼盒所有组件。</p>
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

    private final IGiftBoxService giftBoxService;
    private final IImageLibraryService imageLibraryService;
    private final ImageUrlResolver imageUrlResolver;

    public ProductInfoServiceImpl(ProductInfoMapper baseMapper,
                                  IGiftBoxService giftBoxService,
                                  IImageLibraryService imageLibraryService,
                                  ImageUrlResolver imageUrlResolver) {
        super(baseMapper);
        this.giftBoxService = giftBoxService;
        this.imageLibraryService = imageLibraryService;
        this.imageUrlResolver = imageUrlResolver;
    }

    @Override
    public TableDataInfo<ProductInfoVo> queryPageList(ProductInfoQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<ProductInfo> wrapper = buildQueryWrapper(query);
        Page<ProductInfoVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillImageUrls(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<ProductInfoVo> queryList(ProductInfoQuery query) {
        List<ProductInfoVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillImageUrls(list);
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
        // 礼盒详情附带组件清单
        if (vo.getProductType() != null && vo.getProductType() == PRODUCT_TYPE_GIFT_BOX) {
            List<GiftBoxVo> components = giftBoxService.queryByBoxId(id);
            vo.setGiftComponents(components);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(ProductInfoBo bo) {
        validateBoBeforeWrite(bo);
        // 礼盒自动设置 belongType
        if (bo.getProductType() != null && bo.getProductType() == PRODUCT_TYPE_GIFT_BOX) {
            bo.setBelongType(BELONG_TYPE_GIFT_BOX);
        }
        // 礼盒组件不允许嵌套礼盒
        if (bo.getProductType() != null && bo.getProductType() == PRODUCT_TYPE_GIFT_BOX) {
            validateGiftComponentsNotNested(bo.getGiftComponents());
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

        // 礼盒组件落库
        if (bo.getProductType() != null && bo.getProductType() == PRODUCT_TYPE_GIFT_BOX) {
            giftBoxService.insertBatch(entity.getId(), bo.getGiftComponents());
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
            validateGiftComponentsNotNested(bo.getGiftComponents());
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
        int rows = baseMapper.updateById(entity);

        // 礼盒组件"覆盖式 replace"
        if (exists.getProductType() != null && exists.getProductType() == PRODUCT_TYPE_GIFT_BOX) {
            giftBoxService.softDeleteByBoxId(entity.getId());
            giftBoxService.insertBatch(entity.getId(), bo.getGiftComponents());
        }
        return rows;
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
        int rows = softDelete(ids);
        // 同步级联软删礼盒组件（仅 productType=3 命中行，其他无副作用）
        for (Long id : ids) {
            giftBoxService.softDeleteByBoxId(id);
        }
        return rows;
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
            }
            case PRODUCT_TYPE_PURCHASE -> {
                if (bo.getSupplierId() == null) {
                    throw new ServiceException(I18nMessages.t("product.supplier.required"));
                }
            }
            case PRODUCT_TYPE_GIFT_BOX -> {
                if (CollUtil.isEmpty(bo.getGiftComponents())) {
                    throw new ServiceException(I18nMessages.t("product.gift_components.required"));
                }
            }
            default -> throw new ServiceException("不支持的产品类型：" + type);
        }
    }

    /**
     * 礼盒组件不允许嵌套礼盒（productType=3 禁止指向另一个 productType=3）。
     */
    private void validateGiftComponentsNotNested(List<GiftBoxBo> components) {
        if (CollUtil.isEmpty(components)) {
            return;
        }
        List<Long> ids = components.stream()
            .map(GiftBoxBo::getComponentProductId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        List<ProductInfo> products = baseMapper.selectBatchIds(ids);
        for (ProductInfo p : products) {
            if (p.getProductType() != null && p.getProductType() == PRODUCT_TYPE_GIFT_BOX) {
                throw new ServiceException(I18nMessages.t("product.gift_component.nested", p.getProductName()));
            }
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
        wrapper.eq(StringUtils.isNotBlank(query.getProductId()), ProductInfo::getProductId, query.getProductId())
            .like(StringUtils.isNotBlank(query.getProductName()), ProductInfo::getProductName, query.getProductName())
            .eq(query.getProductType() != null, ProductInfo::getProductType, query.getProductType())
            .eq(StringUtils.isNotBlank(query.getBelongType()), ProductInfo::getBelongType, query.getBelongType())
            .eq(StringUtils.isNotBlank(query.getBuyClass()), ProductInfo::getBuyClass, query.getBuyClass())
            .eq(query.getProductStatus() != null, ProductInfo::getProductStatus, query.getProductStatus())
            .orderByDesc(ProductInfo::getId);
        return wrapper;
    }

}
