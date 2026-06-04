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
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.domain.bo.CeleryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.DryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.GiftPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.VegPackBo;
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

    private final ProductInhouseMapper productInhouseMapper;
    private final ProductInfoMapper productInfoMapper;
    private final GiftBoxMapper giftBoxMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final LocationStockMapper locationStockMapper;
    private final StockFlowMapper stockFlowMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ITraceService traceService;

    public ProductProductionServiceImpl(ProductProductionMapper baseMapper,
                                        ProductInhouseMapper productInhouseMapper,
                                        ProductInfoMapper productInfoMapper,
                                        GiftBoxMapper giftBoxMapper,
                                        LocationInfoMapper locationInfoMapper,
                                        LocationStockMapper locationStockMapper,
                                        StockFlowMapper stockFlowMapper,
                                        IBizCodeGenerator bizCodeGenerator,
                                        ITraceService traceService) {
        super(baseMapper);
        this.productInhouseMapper = productInhouseMapper;
        this.productInfoMapper = productInfoMapper;
        this.giftBoxMapper = giftBoxMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.locationStockMapper = locationStockMapper;
        this.stockFlowMapper = stockFlowMapper;
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

        // Step 6：UPDATE location_stock += weight（不存在则 V1 不凭空创建，跟 D9 同语义）
        locationStockMapper.addByProductLocation(
            bo.getLocationId(), product.getId(), bo.getProductWeight(), userId);

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

        // Step 5：INSERT stock_flow 礼盒入库 + UPDATE location_stock += packBoxCount
        insertPackInFlow(giftBoxProduct.getId(), bo.getLocationId(), giftWeight,
            null, null, p.getProduceNo(), userId, now);
        locationStockMapper.addByProductLocation(
            bo.getLocationId(), giftBoxProduct.getId(), giftWeight, userId);

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
        locationStockMapper.addByProductLocation(
            bo.getLocationId(), product.getId(), bo.getProductWeight(), userId);
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
        locationStockMapper.addByProductLocation(
            bo.getLocationId(), product.getId(), bo.getProductWeight(), userId);
        consumeInhouse(src);

        // 生成追溯码回填 + 写 in_stock 事件（TRC-CORE-001）；芹菜无 earNo
        fillTraceCode(p, null, src.getPlotId());

        log.info("[WMS-PACK-001] celery pack done id={} produceNo={} weight={} traceCode={}",
            p.getId(), p.getProduceNo(), bo.getProductWeight(), p.getTraceCode());
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
            .eq(StringUtils.isNotBlank(query.getPackStatus()), ProductProduction::getPackStatus, query.getPackStatus())
            .eq(StringUtils.isNotBlank(query.getEarNo()), ProductProduction::getEarNo, query.getEarNo())
            .eq(query.getPlotId() != null, ProductProduction::getPlotId, query.getPlotId())
            .eq(query.getStoreId() != null, ProductProduction::getStoreId, query.getStoreId())
            .ge(query.getProduceTimeFrom() != null, ProductProduction::getProduceTime, query.getProduceTimeFrom())
            .le(query.getProduceTimeTo() != null,   ProductProduction::getProduceTime, query.getProduceTimeTo())
            .orderByDesc(ProductProduction::getProduceTime)
            .orderByDesc(ProductProduction::getId);
        return w;
    }

    /**
     * 批量回填 plotName / storeName（V1 简化：仅按 ID 占位 log，admin 列表 plot/store 字段
     * 由前端字典 / 翻译注解处理；N+1 优化推 D11 集中治理）。
     */
    private void fillJoinNames(List<ProductProductionVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream()
            .map(ProductProductionVo::getId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        log.debug("[WMS-PACK-001] fillJoinNames rows={} ids={}", rows.size(), ids);
    }

}
