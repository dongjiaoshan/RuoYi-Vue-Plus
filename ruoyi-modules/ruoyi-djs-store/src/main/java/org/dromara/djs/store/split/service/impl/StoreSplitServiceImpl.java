package org.dromara.djs.store.split.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.store.context.StoreContext;
import org.dromara.djs.store.split.domain.bo.StoreSplitBo;
import org.dromara.djs.store.split.domain.query.StoreSplitQuery;
import org.dromara.djs.store.split.domain.vo.StoreSplitVo;
import org.dromara.djs.store.split.service.IStoreSplitService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 门店白条分割 Service 实现（STR-SPLIT-001）。
 *
 * <h3>跨域共表 source 区分</h3>
 * <p>{@code extends DjsBaseServiceImpl<ProductInhouseMapper, ProductInhouse>} —— 直接复用 warehouse 域
 * 过程产品表的 mapper + entity，不在 store 模块重建。列表 / 导出固定 {@code .eq(source, "store")}，
 * 录入写 {@code source='store'}，与仓库分割（{@code source='warehouse'}）行隔离。</p>
 *
 * <h3>SKU 反查</h3>
 * <p>{@link #resolveSkuByCutPart} 按部位字典 key 反查标准分割 SKU（{@code product_id='PROD-PIG-<PART>-01'}），
 * 取 surrogate {@code id}（Long）赋 {@code inhouse.productId}（FK → product_info.id），取 {@code productName}
 * 冗余。查不到抛 {@link ServiceException}（不吞）。</p>
 *
 * <h3>V1 最薄版本边界</h3>
 * <p>纯独立录入新行：不扣减源部位重量、不做重量勾稽、不写库存流水、不进库存视图聚合
 * （业务理由 P1 #6 待客户澄清后 V2 补，见 _open-issues）。</p>
 *
 * @author djs
 * @since STR-SPLIT-001
 */
@Slf4j
@Service
public class StoreSplitServiceImpl
    extends DjsBaseServiceImpl<ProductInhouseMapper, ProductInhouse>
    implements IStoreSplitService {

    /**
     * 来源标记：门店再分。
     */
    private static final String SOURCE_STORE = "store";

    /**
     * 标准分割 SKU 业务码前缀 / 后缀（{@code PROD-PIG-LEAN-01} 等，WMS-PIG-002 seed）。
     */
    private static final String SKU_PREFIX = "PROD-PIG-";
    private static final String SKU_SUFFIX = "-01";

    /**
     * 默认产品类型 {@code djs_product_type}：1=自产（门店再分品）。
     */
    private static final int PRODUCT_TYPE_SELF = 1;

    /**
     * 默认计量单位。
     */
    private static final String UNIT_KG = "kg";

    private final ProductInfoMapper productInfoMapper;

    public StoreSplitServiceImpl(ProductInhouseMapper baseMapper,
                                 ProductInfoMapper productInfoMapper) {
        super(baseMapper);
        this.productInfoMapper = productInfoMapper;
    }

    @Override
    public TableDataInfo<StoreSplitVo> queryPage(StoreSplitQuery query, PageQuery pageQuery) {
        Page<ProductInhouse> page = baseMapper.selectPage(pageQuery.build(), buildWrapper(query));
        List<StoreSplitVo> vos = page.getRecords().stream().map(this::toVo).toList();
        Page<StoreSplitVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(vos);
        return TableDataInfo.build(voPage);
    }

    @Override
    public List<StoreSplitVo> queryList(StoreSplitQuery query) {
        return baseMapper.selectList(buildWrapper(query)).stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addSplit(StoreSplitBo bo) {
        // 门店拆单必须在已选门店上下文下进行（写绑当前门店，防跨门店写）
        Long storeId = currentStoreIdOrThrow();
        ProductInfo sku = resolveSkuByCutPart(bo.getCutPart());

        ProductInhouse e = new ProductInhouse();
        e.setSource(SOURCE_STORE);
        e.setStoreId(storeId);
        e.setCutPart(bo.getCutPart());
        e.setProductId(sku.getId());
        e.setProductName(sku.getProductName());
        e.setProductType(PRODUCT_TYPE_SELF);
        e.setProductUnit(UNIT_KG);
        e.setProductWeight(bo.getProductWeight());
        e.setLocationId(bo.getLocationId());
        if (StringUtils.isNotBlank(bo.getWhiteBarId())) {
            // snowflake 走 string 入参，service 转回实体 Long 列（白条溯源可空）
            e.setWhiteBarId(Long.valueOf(bo.getWhiteBarId().trim()));
        }
        Date now = new Date();
        e.setProduceDate(now);
        e.setProduceTime(now);
        e.setRemark(bo.getRemark());
        // tenant_id + 6 审计字段走 InjectionMetaObjectHandler 自动 fill
        baseMapper.insert(e);
        return e.getId();
    }

    // ============================ 内部辅助 ============================

    /**
     * 取当前门店上下文 ID，未选门店则抛业务异常（门店拆单必须在已选门店下进行）。
     * 超管 / 租管走 {@link StoreContext#isIgnore()} 也无 storeId 时，要求显式选店再操作。
     */
    private Long currentStoreIdOrThrow() {
        String storeId = StoreContext.getStoreId();
        if (StringUtils.isBlank(storeId)) {
            throw new ServiceException("未选择门店，无法门店拆单（请先在顶部选择门店）");
        }
        try {
            return Long.valueOf(storeId);
        } catch (NumberFormatException ex) {
            throw new ServiceException("门店上下文非法：" + storeId);
        }
    }

    /**
     * 构造列表 / 导出共用 wrapper：固定 {@code source='store'} + 按 query 条件筛选，按生产时间倒序。
     */
    private LambdaQueryWrapper<ProductInhouse> buildWrapper(StoreSplitQuery query) {
        LambdaQueryWrapper<ProductInhouse> w = new LambdaQueryWrapper<ProductInhouse>()
            .eq(ProductInhouse::getSource, SOURCE_STORE);
        if (query != null) {
            w.eq(StringUtils.isNotBlank(query.getCutPart()), ProductInhouse::getCutPart, query.getCutPart())
                .ge(query.getProduceDateStart() != null, ProductInhouse::getProduceDate, query.getProduceDateStart())
                .le(query.getProduceDateEnd() != null, ProductInhouse::getProduceDate, query.getProduceDateEnd());
        }
        w.orderByDesc(ProductInhouse::getProduceTime).orderByDesc(ProductInhouse::getId);
        return w;
    }

    /**
     * 按部位字典 key 反查标准分割 SKU（{@code product_id='PROD-PIG-<PART>-01'}）。
     *
     * <p>{@code t_warehouse_product_info} 业务码列叫 {@code product_id}（VARCHAR），surrogate 主键叫
     * {@code id}（BIGINT）；inhouse.product_id(BIGINT) FK → product_info.id。查不到抛业务异常（不吞）。</p>
     *
     * <p>protected 便于单测 stub。</p>
     */
    protected ProductInfo resolveSkuByCutPart(String cutPart) {
        String bizCode = SKU_PREFIX + cutPart.trim().toUpperCase() + SKU_SUFFIX;
        ProductInfo sku = productInfoMapper.selectOne(
            new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getProductId, bizCode)
                .last("LIMIT 1"));
        if (sku == null) {
            throw new ServiceException("未找到分割部位对应的标准 SKU：" + bizCode);
        }
        return sku;
    }

    /**
     * inhouse 行 → 门店分割 VO（手动映射，避免跨模块 MapStruct 生成）。
     */
    private StoreSplitVo toVo(ProductInhouse e) {
        StoreSplitVo vo = new StoreSplitVo();
        vo.setId(e.getId());
        vo.setCutPart(e.getCutPart());
        vo.setProductName(e.getProductName());
        vo.setProductWeight(e.getProductWeight());
        vo.setLocationId(e.getLocationId());
        vo.setWhiteBarId(e.getWhiteBarId());
        vo.setSource(e.getSource());
        vo.setProduceDate(e.getProduceDate());
        vo.setRemark(e.getRemark());
        vo.setCreateBy(e.getCreateBy());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }

}
