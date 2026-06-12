package org.dromara.djs.warehouse.pack.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.domain.bo.CeleryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.DryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.GiftPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.VegPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.WhiteBarOutBo;
import org.dromara.djs.warehouse.pack.domain.query.ProductProductionQuery;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionVo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.domain.GiftBox;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.GiftBoxMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.trace.domain.TraceContentConst;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 发货产品生产记录 Service 实现（WMS-PACK-001）。
 *
 * <h3>4 业态打包跨表事务</h3>
 * <p>每业态 service 方法 @Transactional 跨 4 表（参 D9 WMS-PIG-002 范式）：</p>
 * <ol>
 *   <li>校验来源 {@code product_inhouse} 存在 + 未消耗（V1 软删模型）</li>
 *   <li>INSERT {@code product_production}（status='packed' / produceNo 业务码 / 凭证图）</li>
 *   <li>INSERT {@code stock_flow}（flow_type='pack_in', inout_type='IN', 入冻品 / 鲜品 / 礼盒库等待发货）</li>
 *   <li>UPDATE {@code location_stock} += quantity（addByProductLocation 兜底）</li>
 *   <li>软删 inhouse row（V1 简化：一次打包消费整 row；分次打包需 mp 端多次提交）</li>
 *   <li>{@code traceService.genCode} 生成追溯码回填 {@code trace_code} + recordEvent(in_stock)（TRC-CORE-001）</li>
 * </ol>
 *
 * <h3>礼盒打包特殊事务（Kevin override 决策 b）</h3>
 * <p>礼盒走 D8 落表 {@code t_warehouse_gift_box}（box_product_id + component_product_id 关联），
 * service 端按 giftBoxProductId 查 gift_box 拿组件清单 N 行，按 packBoxCount 倍数扣减
 * 每个 component 的 location_stock，写主 production 1 行 + N+1 行 stock_flow。</p>
 *
 * <h3>produce_no 业务码生成</h3>
 * <p>{@code yyMMdd + 前缀 + 4 位序号}，前缀 Z=猪肉 / G=果蔬 / B=白条 / H=干货 / D=鸡蛋 / L=礼盒。
 * 走 {@link IBizCodeGenerator} 的 {@link BizCodeType#PRODUCE_NO} 规则（按业态前缀分桶递增 +
 * Redisson 锁 + 序号表 UNIQUE 双保护）。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Slf4j
@Service
public class ProductProductionServiceImpl
    extends DjsBaseServiceImpl<ProductProductionMapper, ProductProduction>
    implements IProductProductionService {

    /** stock_flow.inout_type CHAR(3) IN=入库 / OT=出库（沿 PigCutRecordServiceImpl 范式）。 */
    private static final String INOUT_IN = "IN";
    private static final String INOUT_OUT = "OT";

    /** stock_flow.flow_type 业务类型。 */
    private static final String FLOW_TYPE_PACK_IN = "pack_in";
    private static final String FLOW_TYPE_PACK_CONSUME = "pack_consume";

    /** djs_pack_status 字典码值。 */
    private static final String PACK_STATUS_PACKED = "packed";

    /**
     * 生产位置默认值（NULL — PACK 业态本身不指定具体 location_info，由后续打包入库流程填）。
     *
     * <p>D10 P0 hotfix：原为 Integer 字典 1=仓库，本 hotfix 因 SHIP 用法约束改为 Long FK；
     * PACK 端不写实际 location，统一 NULL。字典语义降级为 follow-up。</p>
     */
    private static final Long PRODUCE_LOCATION_WAREHOUSE = null;

    /** djs_yes_no 字典码值。 */
    private static final Integer YN_NO = 0;

    /** djs_product_type 字典码值（礼盒）。 */
    private static final Integer PRODUCT_TYPE_GIFT = 3;

    /** produce_no 前缀映射（按 product_info.belong_type）。 */
    private static final Map<String, String> BELONG_TYPE_TO_PRODUCE_PREFIX = Map.of(
        "pork",      "Z",
        "vegetable", "G",
        "white_bar", "B",
        "dry_good",  "H",
        "egg",       "D",
        "gift_box",  "L"
    );

    /** 业务码默认前缀（belong_type 缺失时兜底）。 */
    private static final String DEFAULT_PRODUCE_PREFIX = "G";

    /** 白条/猪肉业态（WMS-WHITEBAR-SHIP-001 出库发货来源校验）。 */
    private static final String BELONG_TYPE_WHITE_BAR = "white_bar";
    private static final String BELONG_TYPE_PORK = "pork";

    private final ProductInhouseMapper productInhouseMapper;
    private final ProductInfoMapper productInfoMapper;
    private final GiftBoxMapper giftBoxMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final LocationStockMapper locationStockMapper;
    private final StockFlowMapper stockFlowMapper;
    private final StoreMapper storeMapper;
    private final PlotInfoMapper plotInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ITraceService traceService;

    public ProductProductionServiceImpl(ProductProductionMapper baseMapper,
                                        ProductInhouseMapper productInhouseMapper,
                                        ProductInfoMapper productInfoMapper,
                                        GiftBoxMapper giftBoxMapper,
                                        LocationInfoMapper locationInfoMapper,
                                        LocationStockMapper locationStockMapper,
                                        StockFlowMapper stockFlowMapper,
                                        StoreMapper storeMapper,
                                        PlotInfoMapper plotInfoMapper,
                                        IBizCodeGenerator bizCodeGenerator,
                                        ITraceService traceService) {
        super(baseMapper);
        this.productInhouseMapper = productInhouseMapper;
        this.productInfoMapper = productInfoMapper;
        this.giftBoxMapper = giftBoxMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.locationStockMapper = locationStockMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.storeMapper = storeMapper;
        this.plotInfoMapper = plotInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.traceService = traceService;
    }

    /**
     * 蔬菜打包。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitVegPack(VegPackBo bo) {
        Long userId = currentUserIdSafe();
        Date now = new Date();

        // Step 1：校验来源 product_inhouse 存在 + 未消耗
        ProductInhouse src = requireActiveInhouse(bo.getSourceInhouseId());
        // Step 2：校验目标产品存在 + 是发货品
        ProductInfo product = requireDeliveryProduct(bo.getProductId());
        // Step 3：校验入库库位存在
        requireLocation(bo.getLocationId());

        // Step 4：INSERT product_production
        ProductProduction p = new ProductProduction();
        p.setProduceDate(java.sql.Date.valueOf(LocalDate.now()));
        p.setProduceNo(generateProduceNo(product.getBelongType()));
        p.setProductId(product.getId());
        p.setProductName(product.getProductName());
        p.setProductType(product.getProductType() != null ? product.getProductType() : 1);
        p.setProductUnit(product.getProductUnit());
        p.setProductSpec(StringUtils.isNotBlank(bo.getProductSpec())
            ? bo.getProductSpec() : product.getProductSpec());
        p.setPlotId(src.getPlotId());
        p.setProductSort(1);
        p.setProductWeight(bo.getProductWeight());
        p.setProduceQuantity(bo.getProductWeight()); // D10 hotfix: SHIP 流水 changeNum 用
        p.setStoreId(bo.getStoreId());
        p.setProduceTime(now);
        p.setIsDeliveryCheck(YN_NO);
        p.setIsArrivalConfirm(YN_NO);
        p.setMaterialId(bo.getMaterialId());
        p.setMaterialConsume(bo.getMaterialConsume());
        p.setProduceLocation(PRODUCE_LOCATION_WAREHOUSE);
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setProofOssIds(bo.getProofOssIds());
        p.setRemark(bo.getRemark());
        baseMapper.insert(p);

        // Step 5：INSERT stock_flow 入库
        insertPackInFlow(product.getId(), bo.getLocationId(), bo.getProductWeight(),
            src.getEarNo(), src.getPlotId(), p.getProduceNo(), userId, now);

        // Step 6：UPSERT location_stock += weight（不存在则建新行，WMS-PACK-UPSERT-001）
        upsertLocationStock(bo.getLocationId(), p, bo.getProductWeight(), userId);

        // Step 7：软删来源 inhouse（V1 整 row 消耗）
        consumeInhouse(src);

        // Step 8：生成追溯码回填 + 写 in_stock 事件（TRC-CORE-001）
        fillTraceCode(p, src.getEarNo(), src.getPlotId());

        log.info("[WMS-PACK-001] veg pack done id={} produceNo={} weight={} traceCode={}",
            p.getId(), p.getProduceNo(), bo.getProductWeight(), p.getTraceCode());
        return p.getId();
    }

    /**
     * 礼盒打包（按 D8 gift_box 关联表组件清单）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitGiftPack(GiftPackBo bo) {
        Long userId = currentUserIdSafe();
        Date now = new Date();

        // Step 1：校验礼盒 SKU 存在 + 是礼盒类型
        ProductInfo giftBoxProduct = requireDeliveryProduct(bo.getGiftBoxProductId());
        if (!PRODUCT_TYPE_GIFT.equals(giftBoxProduct.getProductType())) {
            throw new ServiceException("产品不是礼盒类型（product_type != 3）：" + bo.getGiftBoxProductId());
        }
        requireLocation(bo.getLocationId());

        // Step 2：查 D8 gift_box 拿组件清单
        List<GiftBox> components = giftBoxMapper.selectList(
            new LambdaQueryWrapper<GiftBox>()
                .eq(GiftBox::getBoxProductId, bo.getGiftBoxProductId())
                .orderByAsc(GiftBox::getComponentSort));
        if (components.isEmpty()) {
            throw new ServiceException(
                "礼盒未配置组件清单（t_warehouse_gift_box 无 box_product_id=" + bo.getGiftBoxProductId() + " 记录）"
                + "请先在 admin 产品库 → 礼盒详情维护组件");
        }

        // Step 3：for each component → UPDATE location_stock 扣减 + INSERT stock_flow OT
        BigDecimal packCount = new BigDecimal(bo.getPackBoxCount());
        for (GiftBox c : components) {
            BigDecimal needQty = c.getComponentCount().multiply(packCount);
            int affected = locationStockMapper.deductByProductLocation(
                bo.getLocationId(), c.getComponentProductId(), needQty, userId);
            if (affected == 0) {
                throw new ServiceException(
                    "组件库存不足 component_product_id=" + c.getComponentProductId()
                    + " 需要=" + needQty + c.getComponentUnit()
                    + "（库位=" + bo.getLocationId() + "）");
            }
            // 组件消耗流水
            insertPackConsumeFlow(c.getComponentProductId(), bo.getLocationId(),
                needQty, bo.getGiftBoxProductId(), userId, now);
        }

        // Step 4：INSERT product_production（礼盒主记录）
        BigDecimal giftWeight = new BigDecimal(bo.getPackBoxCount());
        ProductProduction p = new ProductProduction();
        p.setProduceDate(java.sql.Date.valueOf(LocalDate.now()));
        p.setProduceNo(generateProduceNo(giftBoxProduct.getBelongType()));
        p.setProductId(giftBoxProduct.getId());
        p.setProductName(giftBoxProduct.getProductName());
        p.setProductType(PRODUCT_TYPE_GIFT);
        p.setProductUnit(StringUtils.isNotBlank(giftBoxProduct.getProductUnit())
            ? giftBoxProduct.getProductUnit() : "盒");
        p.setProductSpec(giftBoxProduct.getProductSpec());
        p.setProductSort(1);
        p.setProductWeight(giftWeight);
        p.setProduceQuantity(giftWeight); // D10 hotfix: SHIP 流水 changeNum 用
        p.setStoreId(bo.getStoreId());
        p.setProduceTime(now);
        p.setIsDeliveryCheck(YN_NO);
        p.setIsArrivalConfirm(YN_NO);
        p.setProduceLocation(PRODUCE_LOCATION_WAREHOUSE);
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setProofOssIds(bo.getProofOssIds());
        p.setRemark(bo.getRemark());
        baseMapper.insert(p);

        // Step 5：INSERT stock_flow 礼盒入库 + UPSERT location_stock += packBoxCount（WMS-PACK-UPSERT-001）
        insertPackInFlow(giftBoxProduct.getId(), bo.getLocationId(), giftWeight,
            null, null, p.getProduceNo(), userId, now);
        upsertLocationStock(bo.getLocationId(), p, giftWeight, userId);

        // 生成追溯码回填 + 写 in_stock 事件（TRC-CORE-001）；礼盒无 earNo / plotId
        fillTraceCode(p, null, null);

        log.info("[WMS-PACK-001] gift pack done id={} produceNo={} packBoxCount={} components={} traceCode={}",
            p.getId(), p.getProduceNo(), bo.getPackBoxCount(), components.size(), p.getTraceCode());
        return p.getId();
    }

    /**
     * 干货打包。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitDryPack(DryPackBo bo) {
        Long userId = currentUserIdSafe();
        Date now = new Date();

        ProductInhouse src = requireActiveInhouse(bo.getSourceInhouseId());
        ProductInfo product = requireDeliveryProduct(bo.getProductId());
        requireLocation(bo.getLocationId());

        ProductProduction p = new ProductProduction();
        p.setProduceDate(java.sql.Date.valueOf(LocalDate.now()));
        p.setProduceNo(generateProduceNo(product.getBelongType()));
        p.setProductId(product.getId());
        p.setProductName(product.getProductName());
        p.setProductType(product.getProductType() != null ? product.getProductType() : 1);
        p.setProductUnit(bo.getProductUnit());
        p.setProductSpec(StringUtils.isNotBlank(bo.getProductSpec())
            ? bo.getProductSpec() : product.getProductSpec());
        p.setEarNo(src.getEarNo());
        p.setProductSort(1);
        p.setProductWeight(bo.getProductWeight());
        p.setProduceQuantity(bo.getProductWeight()); // D10 hotfix: SHIP 流水 changeNum 用
        p.setStoreId(bo.getStoreId());
        p.setProduceTime(now);
        p.setIsDeliveryCheck(YN_NO);
        p.setIsArrivalConfirm(YN_NO);
        p.setProduceLocation(PRODUCE_LOCATION_WAREHOUSE);
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setProofOssIds(bo.getProofOssIds());
        p.setRemark(bo.getRemark());
        baseMapper.insert(p);

        insertPackInFlow(product.getId(), bo.getLocationId(), bo.getProductWeight(),
            src.getEarNo(), src.getPlotId(), p.getProduceNo(), userId, now);
        upsertLocationStock(bo.getLocationId(), p, bo.getProductWeight(), userId);
        consumeInhouse(src);

        // 生成追溯码回填 + 写 in_stock 事件（TRC-CORE-001）
        fillTraceCode(p, src.getEarNo(), src.getPlotId());

        log.info("[WMS-PACK-001] dry pack done id={} produceNo={} weight={} unit={} traceCode={}",
            p.getId(), p.getProduceNo(), bo.getProductWeight(), bo.getProductUnit(), p.getTraceCode());
        return p.getId();
    }

    /**
     * 芹菜按重量打包（无规格）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitCeleryPack(CeleryPackBo bo) {
        Long userId = currentUserIdSafe();
        Date now = new Date();

        ProductInhouse src = requireActiveInhouse(bo.getSourceInhouseId());
        ProductInfo product = requireDeliveryProduct(bo.getProductId());
        requireLocation(bo.getLocationId());

        ProductProduction p = new ProductProduction();
        p.setProduceDate(java.sql.Date.valueOf(LocalDate.now()));
        p.setProduceNo(generateProduceNo(product.getBelongType()));
        p.setProductId(product.getId());
        p.setProductName(product.getProductName());
        p.setProductType(product.getProductType() != null ? product.getProductType() : 1);
        p.setProductUnit(StringUtils.isNotBlank(product.getProductUnit())
            ? product.getProductUnit() : "kg");
        p.setProductSpec("按重量");
        p.setPlotId(src.getPlotId());
        p.setProductSort(1);
        p.setProductWeight(bo.getProductWeight());
        p.setProduceQuantity(bo.getProductWeight()); // D10 hotfix: SHIP 流水 changeNum 用
        p.setStoreId(bo.getStoreId());
        p.setProduceTime(now);
        p.setIsDeliveryCheck(YN_NO);
        p.setIsArrivalConfirm(YN_NO);
        p.setProduceLocation(PRODUCE_LOCATION_WAREHOUSE);
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setProofOssIds(bo.getProofOssIds());
        p.setRemark(bo.getRemark());
        baseMapper.insert(p);

        insertPackInFlow(product.getId(), bo.getLocationId(), bo.getProductWeight(),
            null, src.getPlotId(), p.getProduceNo(), userId, now);
        upsertLocationStock(bo.getLocationId(), p, bo.getProductWeight(), userId);
        consumeInhouse(src);

        // 生成追溯码回填 + 写 in_stock 事件（TRC-CORE-001）；芹菜无 earNo
        fillTraceCode(p, null, src.getPlotId());

        log.info("[WMS-PACK-001] celery pack done id={} produceNo={} weight={} traceCode={}",
            p.getId(), p.getProduceNo(), bo.getProductWeight(), p.getTraceCode());
        return p.getId();
    }

    /**
     * 白条/猪肉出库(发货领用)（WMS-WHITEBAR-SHIP-001）。
     *
     * <p>把白条整只(燎毛产)/猪肉部位(分割产)的 inhouse 出库为 product_production（前缀 B=白条/Z=猪肉）。
     * 区别于 4 个打包口：<b>不选目标发货 SKU</b> —— 直接用来源 inhouse 自身 product_id（白条整只/半只
     * SKU is_delivery=0 是燎毛过程态，出库即发货，shipment 靠 product_info.belong_type 匹配非 is_delivery，
     * 故不走 requireDeliveryProduct）。store_id 工人指定（猪只指定门店），可空（清点时绑定）。
     * 复用 submitDryPack 收尾范式：insertPackInFlow + upsertLocationStock + consumeInhouse + fillTraceCode。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitWhiteBarOut(WhiteBarOutBo bo) {
        Long userId = currentUserIdSafe();
        Date now = new Date();

        ProductInhouse src = requireActiveInhouse(bo.getSourceInhouseId());
        ProductInfo product = productInfoMapper.selectById(src.getProductId());
        if (product == null) {
            throw new ServiceException("来源产品主数据不存在：" + src.getProductId());
        }
        String belongType = product.getBelongType();
        if (!BELONG_TYPE_WHITE_BAR.equals(belongType) && !BELONG_TYPE_PORK.equals(belongType)) {
            throw new ServiceException("出库发货仅限白条/猪肉，当前来源业态=" + belongType);
        }

        ProductProduction p = new ProductProduction();
        p.setProduceDate(java.sql.Date.valueOf(LocalDate.now()));
        p.setProduceNo(generateProduceNo(belongType));
        p.setProductId(product.getId());
        p.setProductName(product.getProductName());
        p.setProductType(product.getProductType() != null ? product.getProductType() : 1);
        p.setProductUnit(StringUtils.isNotBlank(product.getProductUnit()) ? product.getProductUnit() : "kg");
        p.setProductSpec(product.getProductSpec());
        p.setEarNo(src.getEarNo());
        p.setProductSort(1);
        p.setProductWeight(bo.getProductWeight());
        p.setProduceQuantity(bo.getProductWeight()); // D10 hotfix: SHIP 流水 changeNum 用
        p.setStoreId(bo.getStoreId());
        p.setProduceTime(now);
        p.setIsDeliveryCheck(YN_NO);
        p.setIsArrivalConfirm(YN_NO);
        p.setProduceLocation(PRODUCE_LOCATION_WAREHOUSE);
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setProofOssIds(bo.getProofOssIds());
        p.setRemark(bo.getRemark());
        baseMapper.insert(p);

        Long locationId = src.getLocationId();
        if (locationId != null) {
            insertPackInFlow(product.getId(), locationId, bo.getProductWeight(),
                src.getEarNo(), src.getPlotId(), p.getProduceNo(), userId, now);
            upsertLocationStock(locationId, p, bo.getProductWeight(), userId);
        }
        consumeInhouse(src);
        // 生成追溯码回填 + 写 in_stock 事件（TRC-CORE-001 pork 链首个 genCode 入口）
        fillTraceCode(p, src.getEarNo(), null);

        log.info("[WMS-WHITEBAR-SHIP-001] white_bar/pork out done id={} produceNo={} belongType={} weight={} store={} traceCode={}",
            p.getId(), p.getProduceNo(), belongType, bo.getProductWeight(), bo.getStoreId(), p.getTraceCode());
        return p.getId();
    }

    @Override
    public TableDataInfo<ProductProductionVo> queryPageList(ProductProductionQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<ProductProduction> wrapper = buildWrapper(query);
        Page<ProductProductionVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillJoinNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<ProductProductionVo> queryList(ProductProductionQuery query) {
        List<ProductProductionVo> list = baseMapper.selectVoList(buildWrapper(query));
        fillJoinNames(list);
        return list;
    }

    @Override
    public TableDataInfo<ProductProductionVo> queryItemPageList(ProductProductionQuery query, PageQuery pageQuery) {
        // 下钻必须锁定一个生产批次（生产日期 + 产品），缺则返回空（避免误拉全表逐件）
        if (query == null || query.getProductId() == null || query.getProduceDate() == null) {
            return TableDataInfo.build(List.of());
        }
        LambdaQueryWrapper<ProductProduction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductProduction::getProductId, query.getProductId())
            .apply("DATE(produce_date) = DATE({0})", query.getProduceDate())
            .eq(query.getProductSort() != null, ProductProduction::getProductSort, query.getProductSort())
            .eq(query.getStoreId() != null, ProductProduction::getStoreId, query.getStoreId())
            .orderByAsc(ProductProduction::getProductSort)
            .orderByAsc(ProductProduction::getId);
        Page<ProductProductionVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillJoinNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public ProductProductionVo queryById(Long id) {
        ProductProductionVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillJoinNames(List.of(vo));
        }
        return vo;
    }

    @Override
    public List<ProductInhouse> listSourceForVeg() {
        return productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .isNotNull(ProductInhouse::getPlotId)
                .orderByDesc(ProductInhouse::getId)
                .last("LIMIT 50"));
    }

    @Override
    public List<ProductInhouse> listSourceForDry() {
        // V1 简化：所有活动 inhouse（cutPart 非空 = 分割品；plot_id 非空 = 蔬菜采收）
        return productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .orderByDesc(ProductInhouse::getId)
                .last("LIMIT 50"));
    }

    @Override
    public List<ProductInhouse> listSourceForCelery() {
        return productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .isNotNull(ProductInhouse::getPlotId)
                .orderByDesc(ProductInhouse::getId)
                .last("LIMIT 50"));
    }

    @Override
    public List<ProductInhouse> listSourceForWhiteBar() {
        // 来源 = belong_type ∈ {white_bar, pork} 的活动 inhouse
        // （白条整只=燎毛产 cutPart 空+whiteBarId 非空；猪肉部位=分割产 cutPart 非空）。
        List<Long> productIds = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId)
                    .in(ProductInfo::getBelongType, List.of(BELONG_TYPE_WHITE_BAR, BELONG_TYPE_PORK)))
            .stream().map(ProductInfo::getId).toList();
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .in(ProductInhouse::getProductId, productIds)
                .gt(ProductInhouse::getProductWeight, BigDecimal.ZERO)
                .orderByDesc(ProductInhouse::getId)
                .last("LIMIT 50"));
    }

    // ============================================================
    // helpers
    // ============================================================

    /**
     * 生成追溯码并回填 {@code ProductProduction.traceCode}（同事务 UPDATE）+ 写 in_stock 事件流水（TRC-CORE-001）。
     *
     * <p>4 个打包入口（veg/gift/dry/celery）INSERT {@code product_production} 后统一调用。追溯写在主事务内，
     * 但 {@link ITraceService#recordEvent} 内部自带 try-catch 容错（追溯写失败不抛、不拖垮打包）。
     * protected 方便单测 stub。</p>
     *
     * @param p      已 INSERT 的生产记录（含 id / productId）
     * @param earNo  来源耳号（猪肉链；果蔬 / 礼盒传 null）
     * @param plotId 来源地块（果蔬链；猪肉 / 礼盒传 null）
     */
    protected void fillTraceCode(ProductProduction p, String earNo, Long plotId) {
        String produceCode = traceService.genCode(p.getProductId(), earNo, plotId);
        p.setTraceCode(produceCode);
        baseMapper.updateById(p);
        traceService.recordEvent(produceCode, TraceContentConst.IN_STOCK);
    }

    /**
     * 校验来源 inhouse 存在 + 未被消耗（V1 软删模型：del_flag='1' 视为已消耗）。
     */
    protected ProductInhouse requireActiveInhouse(Long inhouseId) {
        ProductInhouse src = productInhouseMapper.selectById(inhouseId);
        if (src == null) {
            throw new ServiceException("来源过程产品不存在或已消耗：" + inhouseId);
        }
        return src;
    }

    /**
     * 校验产品存在 + 是发货品。
     */
    protected ProductInfo requireDeliveryProduct(Long productId) {
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null) {
            throw new ServiceException("产品不存在：" + productId);
        }
        if (product.getIsDelivery() == null || product.getIsDelivery() != 1) {
            throw new ServiceException("产品不是发货品（is_delivery != 1）：" + productId);
        }
        return product;
    }

    /**
     * 校验库位存在。
     */
    protected void requireLocation(Long locationId) {
        LocationInfo location = locationInfoMapper.selectById(locationId);
        if (location == null) {
            throw new ServiceException("入库库位不存在：" + locationId);
        }
    }

    /**
     * INSERT 打包入库流水。
     */
    protected void insertPackInFlow(Long productId, Long locationId, BigDecimal qty,
                                    String earNo, Long plotId, String produceNo,
                                    Long userId, Date now) {
        StockFlow flow = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_IN);
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        flow.setFlowDate(now);
        flow.setProductId(productId);
        flow.setWarehouseId(locationId);
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_TYPE_PACK_IN);
        flow.setChangeNum(qty);
        flow.setChangeQuantity(qty);
        if (StringUtils.isNotBlank(earNo)) {
            flow.setEarNo(earNo);
        }
        if (plotId != null) {
            flow.setPlotId(plotId);
        }
        flow.setOperatorId(userId);
        flow.setRemark("打包入库 produce_no=" + produceNo);
        stockFlowMapper.insert(flow);
    }

    /**
     * INSERT 礼盒组件消耗流水。
     */
    protected void insertPackConsumeFlow(Long componentProductId, Long locationId, BigDecimal qty,
                                         Long giftBoxProductId, Long userId, Date now) {
        StockFlow flow = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_OUT);
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        flow.setFlowDate(now);
        flow.setProductId(componentProductId);
        flow.setWarehouseId(locationId);
        flow.setInoutType(INOUT_OUT);
        flow.setFlowType(FLOW_TYPE_PACK_CONSUME);
        flow.setChangeNum(qty);
        flow.setChangeQuantity(qty);
        flow.setOperatorId(userId);
        flow.setRemark("礼盒打包组件消耗 gift_box_product_id=" + giftBoxProductId);
        stockFlowMapper.insert(flow);
    }

    /**
     * 软删来源 inhouse（V1 整 row 消耗，分次打包需多次 mp 提交）。
     */
    protected void consumeInhouse(ProductInhouse src) {
        // 走 BaseMapperPlus 的 deleteById（会触发 @TableLogic 软删 + tenant 拦截器）。
        // del_unique 同步走 DjsBaseServiceImpl 模型——本场景简化，直接 deleteById 让 MP
        // 写 del_flag='1'，del_unique 保留 0（inhouse 表无 UNIQUE 约束依赖 del_unique，可安全）。
        productInhouseMapper.deleteById(src.getId());
    }

    /**
     * UPSERT location_stock += qty（打包入库）：先 UPDATE 增量，affected=0（该 product+location
     * 无既有行）→ INSERT 新行。复刻 WarehousePurchaseInServiceImpl 的 upsert 范式，修复"打包入库
     * 静默不建库存行 → 礼盒组件扣减找不到行报『组件库存不足』"（WMS-PACK-UPSERT-001）。
     */
    protected void upsertLocationStock(Long locationId, ProductProduction p, BigDecimal qty, Long userId) {
        int updated = locationStockMapper.addByProductLocation(locationId, p.getProductId(), qty, userId);
        if (updated == 0) {
            LocationStock fresh = new LocationStock();
            fresh.setLocationId(locationId);
            fresh.setProductId(p.getProductId());
            fresh.setProductName(p.getProductName());
            fresh.setProductUnit(p.getProductUnit());
            fresh.setProductStock(qty);
            fresh.setIsEnd(0);
            fresh.setOperatorId(userId);
            locationStockMapper.insert(fresh);
        }
    }

    /**
     * 生成 produce_no：{@code yyMMdd + 业态前缀 + 4 位序号}（doc/11 §2.6 R7）。
     *
     * <p>业态前缀按 product_info.belong_type 映射（缺省走默认 G），交给
     * {@link IBizCodeGenerator} 的 {@link BizCodeType#PRODUCE_NO} 规则生成：
     * 序号按 {@code yyMMdd + prefix} 复合键独立递增（每业态当日各自从 0001 起算），
     * Redisson 锁 + 序号表 UNIQUE 双保护。</p>
     */
    protected String generateProduceNo(String belongType) {
        String prefix = BELONG_TYPE_TO_PRODUCE_PREFIX.getOrDefault(
            belongType, DEFAULT_PRODUCE_PREFIX);
        return bizCodeGenerator.generate(BizCodeType.PRODUCE_NO, Map.of("prefix", prefix));
    }

    /**
     * admin 查询条件构造。
     */
    private LambdaQueryWrapper<ProductProduction> buildWrapper(ProductProductionQuery query) {
        LambdaQueryWrapper<ProductProduction> w = new LambdaQueryWrapper<>();
        if (query == null) {
            return w.orderByDesc(ProductProduction::getId);
        }
        w.eq(StringUtils.isNotBlank(query.getProduceNo()),  ProductProduction::getProduceNo, query.getProduceNo())
            .eq(query.getProductId() != null,    ProductProduction::getProductId, query.getProductId())
            .eq(query.getProductType() != null,  ProductProduction::getProductType, query.getProductType())
            .eq(query.getProductSort() != null,  ProductProduction::getProductSort, query.getProductSort())
            .eq(StringUtils.isNotBlank(query.getPackStatus()), ProductProduction::getPackStatus, query.getPackStatus())
            .eq(StringUtils.isNotBlank(query.getEarNo()), ProductProduction::getEarNo, query.getEarNo())
            .eq(query.getPlotId() != null, ProductProduction::getPlotId, query.getPlotId())
            .eq(query.getStoreId() != null, ProductProduction::getStoreId, query.getStoreId())
            .apply(query.getProduceDate() != null, "DATE(produce_date) = DATE({0})", query.getProduceDate())
            .ge(query.getProduceTimeFrom() != null, ProductProduction::getProduceTime, query.getProduceTimeFrom())
            .le(query.getProduceTimeTo() != null,   ProductProduction::getProduceTime, query.getProduceTimeTo())
            .orderByDesc(ProductProduction::getProduceTime)
            .orderByDesc(ProductProduction::getId);
        return w;
    }

    /**
     * 批量回填 plotName / storeName（按 store_id / plot_id 跨域 IN 查，无 N+1）。
     *
     * <p>所属门店 / 来源地块列把 ID 显示成名称：store_id → {@code t_md_store.store_name}
     * （StoreMapper 在 ruoyi-djs-common），plot_id → {@code t_plant_plot_info.plot_name}
     * （PlotInfoMapper 在 ruoyi-djs-plant，warehouse 模块已依赖）。</p>
     */
    private void fillJoinNames(List<ProductProductionVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> storeIds = rows.stream()
            .map(ProductProductionVo::getStoreId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<Long> plotIds = rows.stream()
            .map(ProductProductionVo::getPlotId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Map<Long, String> storeNameMap = storeIds.isEmpty() ? Map.of()
            : storeMapper.selectList(new LambdaQueryWrapper<Store>()
                    .select(Store::getId, Store::getStoreName)
                    .in(Store::getId, storeIds))
                .stream().collect(Collectors.toMap(Store::getId, Store::getStoreName, (a, b) -> a));
        Map<Long, String> plotNameMap = plotIds.isEmpty() ? Map.of()
            : plotInfoMapper.selectList(new LambdaQueryWrapper<PlotInfo>()
                    .select(PlotInfo::getId, PlotInfo::getPlotName)
                    .in(PlotInfo::getId, plotIds))
                .stream().collect(Collectors.toMap(PlotInfo::getId, PlotInfo::getPlotName, (a, b) -> a));

        for (ProductProductionVo vo : rows) {
            if (vo.getStoreId() != null) {
                vo.setStoreName(storeNameMap.get(vo.getStoreId()));
            }
            if (vo.getPlotId() != null) {
                vo.setPlotName(plotNameMap.get(vo.getPlotId()));
            }
        }
    }

}
