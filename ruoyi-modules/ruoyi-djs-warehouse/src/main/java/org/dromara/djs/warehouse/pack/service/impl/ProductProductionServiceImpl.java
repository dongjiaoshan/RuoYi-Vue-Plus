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
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
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
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionGroupVo;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionVo;
import org.dromara.djs.warehouse.pack.domain.vo.StoreDemandCopiesVo;
import org.dromara.djs.warehouse.pack.domain.vo.VegDailyLossVo;
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
    /** 出库去向：生产领用（FIX-WMS-FLOWDICT-001，礼盒组件消耗回填）。 */
    private static final String STOCK_OUT_DEST_PROD_PICK = "prod_pick";

    /** djs_pack_status 字典码值。 */
    private static final String PACK_STATUS_PACKED = "packed";

    /**
     * product_inhouse.source 值：warehouse=仓库分割产（WMS-PIG-002 / WMS-VEG-001）。
     *
     * <p>5 个打包/出库来源 picker 与 {@link #requireActiveInhouse} 只认 warehouse 来源 inhouse，
     * 排除门店现场分割（source='store'，STR-SPLIT-001），实现跨域库存隔离。</p>
     */
    private static final String SOURCE_WAREHOUSE = "warehouse";

    /** djs_yes_no 字典码值。 */
    private static final Integer YN_NO = 0;

    /** djs_product_type 字典码值（礼盒）。 */
    private static final Integer PRODUCT_TYPE_GIFT = 3;

    /** 白条/猪肉业态（WMS-WHITEBAR-SHIP-001 出库发货来源校验）。 */
    private static final String BELONG_TYPE_WHITE_BAR = "white_bar";
    private static final String BELONG_TYPE_PORK = "pork";

    /** 果蔬业态（V4 果蔬打包 product_material 校验 + 日损耗聚合）。 */
    private static final String BELONG_TYPE_VEGETABLE = "vegetable";

    /**
     * 其他产品（egg/dry_good/other）打包来源业态白名单（统一目标模型 G5）。
     *
     * <p>这三类「其他业态食品」走与果蔬/肉品同构的「领用产 inhouse → dry 打包」路径：
     * 物资领用其原料（{@code product_attr=2}）经 {@code MatFlowServiceImpl.bridgeMaterialInhouse}
     * 产 inhouse（{@code produce_date=今天}），{@code listSourceForDry} 只读这三类今天领用的原料
     * inhouse 当来源（不再裸捞全表，避免跨业态污染）。pork/white_bar/vegetable 走各自来源接口。</p>
     */
    private static final List<String> BELONG_TYPES_OTHER_PACK = List.of("egg", "dry_good", "other");

    /** djs_product_attr 字典码值：2=原材料（打包来源只认原料 inhouse，不混成品）。 */
    private static final Integer PRODUCT_ATTR_MATERIAL = 2;

    /**
     * V4 果蔬日损耗聚合 flow_type（领用 / 打包 / 退回 / 饲喂）。
     *
     * <p>口径校正（findings 步14 决策 a）：日损耗 = 领用 − 打包 − 退回 − 饲喂。</p>
     * <ul>
     *   <li>领用 = {@code pick_out}（物资领用模块 {@code MatFlowServiceImpl.pick} 写的领用出库流水）；
     *       原 {@code veg_stock_in}（毛菜处理间入库）口径错误，已弃用。</li>
     *   <li>打包 = {@code pack_in}（果蔬打包 {@code submitVegPack} 写 pack_in 入库；不写 pack_consume，
     *       原取 {@code pack_consume} 恒 0）。</li>
     *   <li>退回 = {@code return_in}（物资领用退回，口径不变）。</li>
     *   <li>饲喂：物资领用 V1 无果蔬饲喂操作，该项恒 0（见 {@link #queryVegDailyLoss}）。</li>
     * </ul>
     */
    private static final String FLOW_TYPE_PICK_OUT = "pick_out";
    /** 领用退回入库（统计口径，与 MatFlowServiceImpl.returnBack 写入对齐：FIX-WMS-FLOWDICT-001 起 pick_return_in）。 */
    private static final String FLOW_TYPE_RETURN_IN = "pick_return_in";

    private final ProductInhouseMapper productInhouseMapper;
    private final ProductInfoMapper productInfoMapper;
    private final GiftBoxMapper giftBoxMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final LocationStockMapper locationStockMapper;
    private final StockFlowMapper stockFlowMapper;
    private final StoreMapper storeMapper;
    private final PlotInfoMapper plotInfoMapper;
    private final DemandManageMapper demandManageMapper;
    private final BarInfoMapper barInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ITraceService traceService;
    private final IStockCheckService stockCheckService;

    public ProductProductionServiceImpl(ProductProductionMapper baseMapper,
                                        ProductInhouseMapper productInhouseMapper,
                                        ProductInfoMapper productInfoMapper,
                                        GiftBoxMapper giftBoxMapper,
                                        LocationInfoMapper locationInfoMapper,
                                        LocationStockMapper locationStockMapper,
                                        StockFlowMapper stockFlowMapper,
                                        StoreMapper storeMapper,
                                        PlotInfoMapper plotInfoMapper,
                                        DemandManageMapper demandManageMapper,
                                        BarInfoMapper barInfoMapper,
                                        IBizCodeGenerator bizCodeGenerator,
                                        ITraceService traceService,
                                        IStockCheckService stockCheckService) {
        super(baseMapper);
        this.productInhouseMapper = productInhouseMapper;
        this.productInfoMapper = productInfoMapper;
        this.giftBoxMapper = giftBoxMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.locationStockMapper = locationStockMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.storeMapper = storeMapper;
        this.plotInfoMapper = plotInfoMapper;
        this.demandManageMapper = demandManageMapper;
        this.barInfoMapper = barInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.traceService = traceService;
        this.stockCheckService = stockCheckService;
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
        // Step 2.5：果蔬打包来源原材料库存校验（V4，果疏产品全流程处理.docx）：
        // 目标果蔬成品若配了 product_material（关联来源原材料果蔬产品），则按 doc 规则
        // "领用果蔬重量（来源原材料库存）< 打包成品重量 → 拦截禁止"。
        // fail-fast 放在任何写操作之前；未配 product_material → 降级跳过校验 + warn（不阻塞）。
        // 注意：果蔬不在此扣减原材料库存（来源消耗 = consumeInhouse 按实重部分扣减）。
        requireInhouseEnough(src, bo.getProductWeight());
        checkVegMaterialIfConfigured(product, bo.getProductWeight());
        // Step 3：入库库位（前端收银台不采集，可空 → 默认取产品配置库位/首个可用库位兜底）
        Long locationId = resolveLocationId(bo.getLocationId(), product);
        // D-2：目标库位盘点锁定中 → 拒绝入库（与 burn/mat-flow 同范式后端双保险）
        stockCheckService.assertLocationUnlocked(locationId);

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
        p.setProduceLocation(locationId);
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setDeliverDest(bo.getDeliverDest());
        p.setProofOssIds(bo.getProofOssIds());
        p.setRemark(bo.getRemark());
        baseMapper.insert(p);

        // Step 5：INSERT stock_flow 入库
        insertPackInFlow(product.getId(), locationId, bo.getProductWeight(),
            src.getEarNo(), src.getPlotId(), p.getProduceNo(), userId, now);

        // Step 6：UPSERT location_stock += weight（不存在则建新行，WMS-PACK-UPSERT-001）
        upsertLocationStock(locationId, p, bo.getProductWeight(), userId);

        // Step 7：软删来源 inhouse（V1 整 row 消耗）
        consumeInhouse(src, bo.getProductWeight());

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
        // 入库库位（前端收银台不采集，可空 → 默认取礼盒产品配置库位/首个可用库位兜底）
        Long locationId = resolveLocationId(bo.getLocationId(), giftBoxProduct);
        // D-2：目标库位盘点锁定中 → 拒绝出入库（组件扣减 + 礼盒入库均落此库位）
        stockCheckService.assertLocationUnlocked(locationId);

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
                locationId, c.getComponentProductId(), needQty, userId);
            if (affected == 0) {
                throw new ServiceException(
                    "组件库存不足 component_product_id=" + c.getComponentProductId()
                    + " 需要=" + needQty + c.getComponentUnit()
                    + "（库位=" + locationId + "）");
            }
            // 组件消耗流水
            insertPackConsumeFlow(c.getComponentProductId(), locationId,
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
        p.setProduceLocation(locationId);
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setDeliverDest(bo.getDeliverDest());
        p.setProofOssIds(bo.getProofOssIds());
        p.setRemark(bo.getRemark());
        baseMapper.insert(p);

        // Step 5：INSERT stock_flow 礼盒入库 + UPSERT location_stock += packBoxCount（WMS-PACK-UPSERT-001）
        insertPackInFlow(giftBoxProduct.getId(), locationId, giftWeight,
            null, null, p.getProduceNo(), userId, now);
        upsertLocationStock(locationId, p, giftWeight, userId);

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
        requireInhouseEnough(src, bo.getProductWeight());
        // 入库库位（前端收银台不采集，可空 → 默认取产品配置库位/首个可用库位兜底）
        Long locationId = resolveLocationId(bo.getLocationId(), product);
        // D-2：目标库位盘点锁定中 → 拒绝入库
        stockCheckService.assertLocationUnlocked(locationId);

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
        p.setProduceLocation(locationId);
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setDeliverDest(bo.getDeliverDest());
        p.setProofOssIds(bo.getProofOssIds());
        p.setRemark(bo.getRemark());
        baseMapper.insert(p);

        insertPackInFlow(product.getId(), locationId, bo.getProductWeight(),
            src.getEarNo(), src.getPlotId(), p.getProduceNo(), userId, now);
        upsertLocationStock(locationId, p, bo.getProductWeight(), userId);
        consumeInhouse(src, bo.getProductWeight());

        // 肉品打包原材料库存校验 + 扣减（猪肉全闭环 Part I P8）：
        // 仅 belong_type=pork 且目标产品配了 product_material（关联原材料）时校验，
        // 生产重量 > 原材料库存 → 抛 ServiceException（前端弹窗禁止）。未配 product_material 降级跳过（不阻塞）。
        // 分支隔离：仅 pork + 配料命中，干货/果蔬/其他 dry 打包现状不受影响。
        deductPorkMaterialIfConfigured(product, bo.getProductWeight(), userId, now);

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
        requireInhouseEnough(src, bo.getProductWeight());
        requireLocation(bo.getLocationId());
        // D-2：目标库位盘点锁定中 → 拒绝入库
        stockCheckService.assertLocationUnlocked(bo.getLocationId());

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
        p.setProduceLocation(bo.getLocationId());
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setProofOssIds(bo.getProofOssIds());
        p.setRemark(bo.getRemark());
        baseMapper.insert(p);

        insertPackInFlow(product.getId(), bo.getLocationId(), bo.getProductWeight(),
            null, src.getPlotId(), p.getProduceNo(), userId, now);
        upsertLocationStock(bo.getLocationId(), p, bo.getProductWeight(), userId);
        consumeInhouse(src, bo.getProductWeight());

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
        // 发货月台出库即发往某门店：校验门店存在（BO 已 @NotNull 兜空，此处兜「门店已删/不存在」）
        Store store = storeMapper.selectById(bo.getStoreId());
        if (store == null) {
            throw new ServiceException("发货门店不存在或已删除：" + bo.getStoreId());
        }
        requireInhouseEnough(src, bo.getProductWeight());
        // D-2：来源 inhouse 所在库位盘点锁定中 → 拒绝出库（白条整只 inhouse 常无 locationId，
        // assertLocationUnlocked 对 null 安全跳过）
        stockCheckService.assertLocationUnlocked(src.getLocationId());

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
        p.setProduceLocation(src.getLocationId());
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
        consumeInhouse(src, bo.getProductWeight());

        // 发货月台领用回写 bar 出库基础数据（猪肉全闭环 Part I P6）：来源为燎毛白条整只（whiteBarId 非空）时，
        // 同事务把对应 t_warehouse_bar_info 推进到 cut_done + out_method=1 + out_time/out_weight。
        // 独立于上方 inhouse 是否有 location（白条整只 inhouse 常无 locationId，但 bar 出库基础数据仍需补齐）。
        // affectedRows==0（bar 不在 in_stock 态：已被领用/出库）→ 静默跳过（基础数据补写，非主链路硬阻塞）。
        Long whiteBarId = src.getWhiteBarId();
        if (whiteBarId != null) {
            int barAffected = barInfoMapper.updateStatusToShipOut(whiteBarId, now, bo.getProductWeight(), userId);
            log.info("[WHITEBAR-SHIP-P6] bar ship-out writeback barId={} affected={} (0=不在in_stock态,跳过)",
                whiteBarId, barAffected);
        }

        // 生成追溯码回填 + 写 in_stock 事件（TRC-CORE-001 pork 链首个 genCode 入口）
        fillTraceCode(p, src.getEarNo(), null);

        log.info("[WMS-WHITEBAR-SHIP-001] white_bar/pork out done id={} produceNo={} belongType={} weight={} store={} traceCode={}",
            p.getId(), p.getProduceNo(), belongType, bo.getProductWeight(), bo.getStoreId(), p.getTraceCode());
        return p.getId();
    }

    @Override
    public TableDataInfo<ProductProductionGroupVo> queryGroupPageList(ProductProductionQuery query, PageQuery pageQuery) {
        String produceNo = query == null ? null : query.getProduceNo();
        String productName = query == null ? null : query.getProductName();
        String belongType = query == null ? null : query.getBelongType();
        Integer productType = query == null ? null : query.getProductType();
        Date from = query == null ? null : query.getProduceDateFrom();
        Date to = query == null ? null : query.getProduceDateTo();
        // belongType（产品品类，product_info 维度）/ productType（组内同值）/ productName(LIKE) 均下推 mapper WHERE 过滤
        List<ProductProductionGroupVo> all =
            baseMapper.selectProductionGroupList(produceNo, productName, belongType, productType, from, to);
        // 聚合后内存分页（分组行数小，范式同 DemandManageServiceImpl.queryGroupList）
        int total = all.size();
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 10 : pageQuery.getPageSize();
        int fromIdx = Math.max(0, (pageNum - 1) * pageSize);
        int toIdx = Math.min(total, fromIdx + pageSize);
        List<ProductProductionGroupVo> pageRows = fromIdx >= total ? List.of() : all.subList(fromIdx, toIdx);
        TableDataInfo<ProductProductionGroupVo> rsp = new TableDataInfo<>();
        rsp.setRows(pageRows);
        rsp.setTotal(total);
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        return rsp;
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
            // 产品序号模糊搜索（int 列 CAST 成字符串 LIKE %kw%；row115-n1）
            .apply(StringUtils.isNotBlank(query.getProductSort()),
                "CAST(product_sort AS CHAR) LIKE CONCAT('%', {0}, '%')", query.getProductSort())
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
        // 果蔬打包来源 = belong_type='vegetable' 且 product_attr=2(原料) 的「今天领用」活动 inhouse
        // （doc/14 §5：只显今天领用待打包）。物资领用果蔬原材料经 MatFlowServiceImpl.bridgeMaterialInhouse
        // 桥接产生（produce_date=今天）；product 维度 plot_id 可空、为冗余追溯字段。
        // G8：补 product_attr=2 守门——只把原料 inhouse 当来源，防成品（attr=1）误混入打包来源。
        // 与 listSourceForMeat（belong_type='pork'）同范式。
        List<Long> productIds = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId)
                    .eq(ProductInfo::getBelongType, BELONG_TYPE_VEGETABLE)
                    .eq(ProductInfo::getProductAttr, PRODUCT_ATTR_MATERIAL))
            .stream().map(ProductInfo::getId).toList();
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .in(ProductInhouse::getProductId, productIds)
                .eq(ProductInhouse::getSource, SOURCE_WAREHOUSE)
                .gt(ProductInhouse::getProductWeight, BigDecimal.ZERO)
                .apply("DATE(produce_date) = CURDATE()")
                .orderByDesc(ProductInhouse::getId)
                .last("LIMIT 50"));
    }

    @Override
    public List<ProductInhouse> listSourceForDry() {
        // 其他产品（egg/dry_good/other）打包来源 = belong_type ∈ {egg,dry_good,other} 且 product_attr=2(原料)
        // 的「今天领用」活动 inhouse（统一目标模型 G5）。镜像 listSourceForMeat/Veg：先按业态白名单 + attr=2
        // 解析 product_info id 集，再 inhouse WHERE product_id IN(...) AND DATE(produce_date)=今天 AND weight>0。
        // 修复旧实现「裸捞全表 inhouse」= 跨业态污染源（把猪肉/果蔬/白条 inhouse 也混进其他产品打包来源）。
        // 今天没领用对应原料 → 空（须先 mp 领用）。
        List<Long> productIds = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId)
                    .in(ProductInfo::getBelongType, BELONG_TYPES_OTHER_PACK)
                    .eq(ProductInfo::getProductAttr, PRODUCT_ATTR_MATERIAL))
            .stream().map(ProductInfo::getId).toList();
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .in(ProductInhouse::getProductId, productIds)
                .eq(ProductInhouse::getSource, SOURCE_WAREHOUSE)
                .gt(ProductInhouse::getProductWeight, BigDecimal.ZERO)
                .apply("DATE(produce_date) = CURDATE()")
                .orderByDesc(ProductInhouse::getId)
                .last("LIMIT 50"));
    }

    @Override
    public List<ProductInhouse> listSourceForMeat() {
        // 肉品打包来源 = belong_type='pork' 的「今天领用」活动 inhouse（doc/14 §5：只显今天领用待打包，
        // ear_no = 篮子标签 = 来源猪只耳号）；排除白条/蔬菜，避免混入肉品耳号去重条。今天没领用 → 空（须先 mp 领用）。
        List<Long> productIds = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId)
                    .eq(ProductInfo::getBelongType, BELONG_TYPE_PORK))
            .stream().map(ProductInfo::getId).toList();
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .in(ProductInhouse::getProductId, productIds)
                .eq(ProductInhouse::getSource, SOURCE_WAREHOUSE)
                .gt(ProductInhouse::getProductWeight, BigDecimal.ZERO)
                .apply("DATE(produce_date) = CURDATE()")
                .orderByDesc(ProductInhouse::getId)
                .last("LIMIT 50"));
    }

    @Override
    public List<ProductInhouse> listSourceForCelery() {
        // 同 listSourceForVeg：belong_type='vegetable' + attr=2 原料维度（G8 同守门，防成品混入）+「今天领用」过滤（doc/14 §5）。
        // 重口味/保鲜库差异在库位，不在 belong_type。
        List<Long> productIds = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId)
                    .eq(ProductInfo::getBelongType, BELONG_TYPE_VEGETABLE)
                    .eq(ProductInfo::getProductAttr, PRODUCT_ATTR_MATERIAL))
            .stream().map(ProductInfo::getId).toList();
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .in(ProductInhouse::getProductId, productIds)
                .eq(ProductInhouse::getSource, SOURCE_WAREHOUSE)
                .gt(ProductInhouse::getProductWeight, BigDecimal.ZERO)
                .apply("DATE(produce_date) = CURDATE()")
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
                .eq(ProductInhouse::getSource, SOURCE_WAREHOUSE)
                .gt(ProductInhouse::getProductWeight, BigDecimal.ZERO)
                // P3-21：白条排酸后可跨天出库，故不限今天（区别于果蔬/肉品当天处理——其余 4 个 picker 带
                // DATE(produce_date)=CURDATE()）。此处刻意省略今日过滤，非漏写。
                .orderByDesc(ProductInhouse::getId)
                .last("LIMIT 50"));
    }

    @Override
    public List<StoreDemandCopiesVo> listStoreDemandCopies(Long productId) {
        if (productId == null) {
            return List.of();
        }
        return demandManageMapper.selectStoreDemandCopies(productId);
    }

    @Override
    public VegDailyLossVo queryVegDailyLoss(Date statDate) {
        // statDate 为空 → 当天；mapper 用 COALESCE(DATE(#{flowDate}), CURDATE()) 兜底，这里仅算回显日期
        LocalDate day = statDate == null
            ? LocalDate.now()
            : statDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();

        // 领用 = pick_out（物资领用出库，MatFlowServiceImpl.pick 写）
        BigDecimal picked = nullSafeStock(
            stockFlowMapper.sumVegFlowByTypeAndDate(FLOW_TYPE_PICK_OUT, statDate));
        // 打包 = pack_in（果蔬打包 submitVegPack 写入库流水，不写 pack_consume）
        BigDecimal packed = nullSafeStock(
            stockFlowMapper.sumVegFlowByTypeAndDate(FLOW_TYPE_PACK_IN, statDate));
        // 退回 = return_in（物资领用退回）
        BigDecimal returned = nullSafeStock(
            stockFlowMapper.sumVegFlowByTypeAndDate(FLOW_TYPE_RETURN_IN, statDate));
        // 饲喂：物资领用 V1 无果蔬饲喂操作，该项恒 0（毛菜处理间饲喂是步9 另一阶段，不在步14 重复扣）；
        // 待客户确认是否需新增物资领用饲喂来源（findings 步14）。
        BigDecimal feed = BigDecimal.ZERO;

        // 日损耗 = 领用 − 打包 − 退回 − 饲喂；负值（录入未配齐）归零
        BigDecimal loss = picked.subtract(packed).subtract(returned).subtract(feed);
        if (loss.signum() < 0) {
            loss = BigDecimal.ZERO;
        }

        VegDailyLossVo vo = new VegDailyLossVo();
        vo.setStatDate(day.toString());
        vo.setPickedWeight(picked);
        vo.setPackedWeight(packed);
        vo.setReturnedWeight(returned);
        vo.setFeedWeight(feed);
        vo.setLossWeight(loss);
        return vo;
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
        // 追溯时间轴每节点重量：入库节点重量 = 该生产记录 productWeight
        traceService.recordEvent(produceCode, TraceContentConst.IN_STOCK, p.getProductWeight());
    }

    /**
     * 校验来源 inhouse 存在 + 未被消耗（V1 软删模型：del_flag='1' 视为已消耗）+ 跨域隔离。
     *
     * <p>P1-2：仓库打包/出库只认仓库分割产（{@code source='warehouse'}），拒绝门店现场分割
     * （{@code source='store'}，STR-SPLIT-001）；防前端绕过 picker 直接传 store 行 id 跨域取库存。</p>
     */
    protected ProductInhouse requireActiveInhouse(Long inhouseId) {
        ProductInhouse src = productInhouseMapper.selectById(inhouseId);
        if (src == null) {
            throw new ServiceException("来源过程产品不存在或已消耗：" + inhouseId);
        }
        if (!SOURCE_WAREHOUSE.equals(src.getSource())) {
            throw new ServiceException("来源不是仓库分割产，不可在仓库打包/出库（来源=" + src.getSource() + "）：" + inhouseId);
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
     * 解析入库库位（FIX-WMS-PACK-CASHIER 决策 2）：前端打包收银台不再采集入库库位。
     *
     * <p>优先级：① 入参 {@code boLocationId} 非空 → 校验存在后用；
     * ② 否则取该产品配置的入库库位（{@code product_info.store_location_id} 逗号分隔列表首个有效项）；
     * ③ 仍无 → 取首个可用库位（{@code t_warehouse_location_info} 未软删按 id 升序第一条）兜底。
     * 全部落空（系统未建任何库位）→ 抛 ServiceException。</p>
     *
     * @param boLocationId 前端传入的库位 id（可空）
     * @param product      目标打包产品（取其 store_location_id 配置）
     * @return 解析出的有效库位 id（非空）
     */
    protected Long resolveLocationId(Long boLocationId, ProductInfo product) {
        // ① 前端显式传了库位 → 校验存在后用
        if (boLocationId != null) {
            requireLocation(boLocationId);
            return boLocationId;
        }
        // ② 产品配置的入库库位（store_location_id 逗号分隔，取首个存在的有效项）
        if (product != null && StringUtils.isNotBlank(product.getStoreLocationId())) {
            for (String token : product.getStoreLocationId().split(",")) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                Long candidate;
                try {
                    candidate = Long.valueOf(trimmed);
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (locationInfoMapper.selectById(candidate) != null) {
                    return candidate;
                }
            }
        }
        // ③ 首个可用库位兜底
        LocationInfo first = locationInfoMapper.selectList(
                new LambdaQueryWrapper<LocationInfo>()
                    .orderByAsc(LocationInfo::getId)
                    .last("LIMIT 1"))
            .stream().findFirst().orElse(null);
        if (first == null) {
            throw new ServiceException("系统未配置任何入库库位，无法打包入库（请先在仓库管理 → 库位维护新建库位）");
        }
        return first.getId();
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
        // 生产领用去向（FIX-WMS-FLOWDICT-001）：礼盒打包组件消耗 = 仓库内部生产消耗，自动回填 prod_pick
        flow.setStockOutDest(STOCK_OUT_DEST_PROD_PICK);
        flow.setChangeNum(qty);
        flow.setChangeQuantity(qty);
        flow.setOperatorId(userId);
        flow.setRemark("礼盒打包组件消耗 gift_box_product_id=" + giftBoxProductId);
        stockFlowMapper.insert(flow);
    }

    /**
     * 打包前置 fail-fast：选中来源 inhouse 余量 ≥ 本次打包实重，否则抛（在任何写操作前拦截，
     * 避免超扣产生 orphan production；与 {@link #consumeInhouse} 的行锁原子扣减互为内外两道闸）。
     */
    private void requireInhouseEnough(ProductInhouse src, BigDecimal packWeight) {
        BigDecimal remain = src.getProductWeight() == null ? BigDecimal.ZERO : src.getProductWeight();
        if (packWeight != null && remain.compareTo(packWeight) < 0) {
            throw new ServiceException("来源待打包库存不足：当前 " + remain.stripTrailingZeros().toPlainString()
                + "，本次打包 " + packWeight.stripTrailingZeros().toPlainString());
        }
    }

    /**
     * 消耗来源 inhouse：按本次打包实重 {@code consumeWeight} 从该行待打包重量中部分扣减。
     *
     * <p>支持「一行领用 WIP 分多次打包」，让打包卡「原材料库存 = Σ 活动 inhouse」随打包逐份递减
     * （doc/14 §1 不变式 5）。规则：</p>
     * <ul>
     *   <li>{@code consumeWeight} 缺失/≤0 → 兜底整行软删（旧全量语义）</li>
     *   <li>打包量 &gt; 来源余量 → 抛 {@link ServiceException} 拒绝（防超扣；事务回滚）</li>
     *   <li>正好用尽 → 整行软删</li>
     *   <li>未用尽 → {@link ProductInhouseMapper#deductWeightById} 行锁原子扣减（affected=0=并发抢占→回滚）</li>
     * </ul>
     */
    protected void consumeInhouse(ProductInhouse src, BigDecimal consumeWeight) {
        BigDecimal current = src.getProductWeight() == null ? BigDecimal.ZERO : src.getProductWeight();
        if (consumeWeight == null || consumeWeight.signum() <= 0) {
            productInhouseMapper.deleteById(src.getId());
            return;
        }
        int cmp = current.compareTo(consumeWeight);
        if (cmp < 0) {
            throw new ServiceException("来源待打包库存不足：当前 " + current.stripTrailingZeros().toPlainString()
                + "，本次打包 " + consumeWeight.stripTrailingZeros().toPlainString());
        }
        if (cmp == 0) {
            productInhouseMapper.deleteById(src.getId());
            return;
        }
        int affected = productInhouseMapper.deductWeightById(src.getId(), consumeWeight);
        if (affected == 0) {
            throw new ServiceException("来源待打包库存不足或已被占用，请刷新后重试");
        }
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
     * 肉品打包原材料库存校验 + 原子扣减 + 写消耗流水（猪肉全闭环 Part I P8）。
     *
     * <p>触发条件（同时满足）：</p>
     * <ul>
     *   <li>目标产品 {@code belong_type='pork'}（仅肉品打包；干货/果蔬/其他业态 dry 打包不进此分支）</li>
     *   <li>目标产品配了 {@code product_material}（自引用 FK → 原材料产品 id）；未配则降级跳过校验，不阻塞</li>
     * </ul>
     *
     * <p>校验：生产重量（{@code produceWeight}）> 原材料当前库存合计 → 抛 {@link ServiceException}（前端弹窗禁止）。
     * 扣减：取原材料库存最多的库位（{@link LocationStockMapper#selectDefaultLocationByProduct}），
     * 用 {@link LocationStockMapper#deductByProductLocation}（{@code product_stock>=deductQty} 行锁原子扣减）。
     * 行锁 affected==0（并发抢扣 / 单库位不足）→ 抛 ServiceException 触发整事务回滚。
     * 同步写 {@code pack_consume} 出库流水（参 {@code submitGiftPack} 组件消耗范式）。</p>
     *
     * @param product       目标打包产品（肉品 SKU）
     * @param produceWeight 本次生产重量（即原材料消耗量，kg）
     * @param userId        操作人
     * @param now           操作时间
     */
    protected void deductPorkMaterialIfConfigured(ProductInfo product, BigDecimal produceWeight,
                                                  Long userId, Date now) {
        if (!BELONG_TYPE_PORK.equals(product.getBelongType()) || product.getProductMaterial() == null) {
            return;
        }
        // doc/14 §1：肉品打包消耗 = 来源 inhouse（consumeInhouse 按实重部分扣减，与果蔬同口径），
        // 不再二次扣原材料 location_stock——原材料经分割/领用进 inhouse，本就不在 location_stock，
        // 旧的 location_stock 扣减对「成品配 product_material」的肉品会双扣或误拦。仅留观测性 warn。
        log.warn("[PORK-PACK] 肉品成品配了 product_material，消耗走来源 inhouse（不扣原材料 location_stock）productId={} material={}",
            product.getId(), product.getProductMaterial());
    }

    /**
     * 果蔬打包来源原材料库存校验（V4，果疏产品全流程处理.docx；仅校验、不扣减）。
     *
     * <p>触发条件（同时满足）：</p>
     * <ul>
     *   <li>目标产品 {@code belong_type='vegetable'}（仅果蔬打包；其他业态走各自分支不进此校验）</li>
     *   <li>目标产品配了 {@code product_material}（FK → 来源原材料果蔬产品 id）；未配则降级跳过校验，不阻塞（log.warn）</li>
     * </ul>
     *
     * <p>校验规则（doc 原文「领用果蔬重量 &lt; 打包成品重量 → 拦截禁止」）：来源原材料当前库存合计
     * &lt; 本次打包成品重量（{@code produceWeight}）→ 抛 {@link ServiceException}（前端弹窗禁止）。</p>
     *
     * <p>与 pork 链 {@link #deductPorkMaterialIfConfigured} 的区别：果蔬<b>只拦截不扣减</b>——
     * 来源消耗沿用 {@link #consumeInhouse}（整 row 软删 inhouse），此处再扣会重复扣减。</p>
     *
     * @param product       目标打包产品（果蔬成品 SKU）
     * @param produceWeight 本次打包成品重量 kg
     */
    protected void checkVegMaterialIfConfigured(ProductInfo product, BigDecimal produceWeight) {
        if (!BELONG_TYPE_VEGETABLE.equals(product.getBelongType()) || product.getProductMaterial() == null) {
            if (BELONG_TYPE_VEGETABLE.equals(product.getBelongType()) && product.getProductMaterial() == null) {
                log.warn("[VEG-PACK-V4] 果蔬成品未配 product_material（关联原材料），跳过库存校验 productId={} name={}",
                    product.getId(), product.getProductName());
            }
            return;
        }
        // doc/14 §1：领用已把原材料从冷库(location_stock)转入待打包(inhouse)，故此处不再以冷库余额硬拦截
        // （否则会错误拦下"已领用但冷库已扣减"的合法打包）。真正的防超扣口径 = 来源 inhouse 余量，
        // 由 consumeInhouse 的 deductWeightById 行锁原子保证；此处仅留观测性 warn。
        Long materialProductId = product.getProductMaterial();
        BigDecimal materialStock = sumProductStock(materialProductId);
        if (materialStock.compareTo(produceWeight) < 0) {
            log.warn("[VEG-PACK] 原材料冷库余额({}) < 打包成品重量({})，按 inhouse 余量口径继续（doc/14 §1）materialProductId={}",
                materialStock.stripTrailingZeros().toPlainString(),
                produceWeight.stripTrailingZeros().toPlainString(), materialProductId);
        }
    }

    /**
     * BigDecimal 空兜底为 0（mapper COALESCE 已兜，叠加防御保证日损耗减法不 NPE）。
     */
    private static BigDecimal nullSafeStock(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    public Map<String, BigDecimal> listMaterialStock(List<Long> productIds) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return result;
        }
        // 去重防重复查；逐成品取 product_material 指向的原材料库存合计（口径同打包校验/扣减）
        for (Long productId : productIds.stream().filter(Objects::nonNull).collect(Collectors.toSet())) {
            ProductInfo product = productInfoMapper.selectById(productId);
            // 未配 product_material 的成品不进 Map（前端展示 '—'，不参与校验）；产品库为空时整体返空 Map
            if (product == null || product.getProductMaterial() == null) {
                continue;
            }
            result.put(String.valueOf(productId), sumProductStock(product.getProductMaterial()));
        }
        return result;
    }

    @Override
    public Map<String, Integer> listPackedCount(List<Long> productIds) {
        Map<String, Integer> result = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return result;
        }
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : baseMapper.selectTodayPackedCount(ids)) {
            Object pid = row.get("productId");
            Object cnt = row.get("cnt");
            if (pid != null && cnt != null) {
                result.put(String.valueOf(pid), ((Number) cnt).intValue());
            }
        }
        return result;
    }

    @Override
    public Set<String> listPackedDoneProductIds(List<Long> productIds) {
        Set<String> done = new java.util.HashSet<>();
        if (productIds == null || productIds.isEmpty()) {
            return done;
        }
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return done;
        }
        // 今天「按门店已打包份数」：productId -> (storeId -> packedCnt)
        Map<Long, Map<Long, Integer>> packedByStore = new HashMap<>();
        for (Map<String, Object> row : baseMapper.selectTodayPackedCountByStore(ids)) {
            Object pid = row.get("productId");
            Object sid = row.get("storeId");
            Object cnt = row.get("cnt");
            if (pid == null || sid == null || cnt == null) {
                continue;
            }
            packedByStore
                .computeIfAbsent(((Number) pid).longValue(), k -> new HashMap<>())
                .put(((Number) sid).longValue(), ((Number) cnt).intValue());
        }
        // 逐产品判定：每个「未发货需求份数>0」的门店都被打满 → 完成；无未发货门店需求 → 不完成
        for (Long productId : ids) {
            List<StoreDemandCopiesVo> demands = demandManageMapper.selectStoreDemandCopies(productId);
            if (demands == null || demands.isEmpty()) {
                continue;
            }
            Map<Long, Integer> packed = packedByStore.getOrDefault(productId, Map.of());
            boolean allStoresDone = true;
            for (StoreDemandCopiesVo d : demands) {
                if (d.getStoreId() == null || d.getCopies() == null) {
                    continue;
                }
                // 需求份数：份数为份（整数语义），向上取整防 2.0001 类边界把整门店判漏（与前端 Math.round 展示口径对齐）
                int needCopies = (int) Math.ceil(d.getCopies().doubleValue());
                if (needCopies <= 0) {
                    continue;
                }
                int packedToStore = packed.getOrDefault(d.getStoreId(), 0);
                if (packedToStore < needCopies) {
                    allStoresDone = false;
                    break;
                }
            }
            if (allStoresDone) {
                done.add(String.valueOf(productId));
            }
        }
        return done;
    }

    @Override
    public Map<String, String> listPackedWeight(List<Long> productIds) {
        Map<String, String> result = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return result;
        }
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : baseMapper.selectPackedWeight(ids)) {
            Object pid = row.get("productId");
            Object weight = row.get("weight");
            if (pid == null || weight == null) {
                continue;
            }
            // SUM(product_weight) DECIMAL → BigDecimal；以字符串透传保留精度（key 雪花 id 也用 String 防丢精度）
            BigDecimal w = weight instanceof BigDecimal bd ? bd : new BigDecimal(weight.toString());
            result.put(String.valueOf(pid), w.stripTrailingZeros().toPlainString());
        }
        return result;
    }

    /**
     * 聚合某产品当前库存合计（未软删行 SUM(product_stock)；无行返 BigDecimal.ZERO）。
     */
    protected BigDecimal sumProductStock(Long productId) {
        return locationStockMapper.selectList(
                new LambdaQueryWrapper<LocationStock>()
                    .select(LocationStock::getProductStock)
                    .eq(LocationStock::getProductId, productId))
            .stream()
            .map(LocationStock::getProductStock)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 生成 produce_no：{@code yyMMdd + 4 位每日序号}（生产编码规则，例 2605120001）。
     *
     * <p>全部打包/出库生码共用一个每日计数器（{@link BizCodeType#PRODUCE_NO} 规则 daily_reset=1，
     * 当日从 0001 起算），不再按业态前缀分桶。{@code belongType} 参数保留兼容各调用方，当前不参与编码。
     * Redisson 锁 + 序号表 UNIQUE 双保护。</p>
     */
    protected String generateProduceNo(String belongType) {
        return bizCodeGenerator.generate(BizCodeType.PRODUCE_NO, Map.of());
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
            .apply(StringUtils.isNotBlank(query.getProductSort()),
                "CAST(product_sort AS CHAR) LIKE CONCAT('%', {0}, '%')", query.getProductSort())
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
