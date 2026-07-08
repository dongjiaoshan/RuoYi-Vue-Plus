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
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.domain.bo.CeleryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.DryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.GiftPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.MarkDamageBo;
import org.dromara.djs.warehouse.pack.domain.bo.VegPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.WarehouseOutBo;
import org.dromara.djs.warehouse.pack.domain.bo.WhiteBarOutBo;
import org.dromara.djs.warehouse.pack.domain.query.ProductProductionQuery;
import org.dromara.djs.warehouse.pack.domain.query.WhiteBarShipmentQuery;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionGroupVo;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionVo;
import org.dromara.djs.warehouse.pack.domain.vo.StoreDemandCopiesVo;
import org.dromara.djs.warehouse.pack.domain.vo.VegDailyLossVo;
import org.dromara.djs.warehouse.pack.domain.vo.WhiteBarShipmentVo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
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
import java.time.ZoneId;
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
 * <h3>礼盒打包（礼盒为独立成品）</h3>
 * <p>礼盒不再有组件清单（BOM）：打 N 盒只产出 N 盒礼盒成品（{@code product_production} 1 行 +
 * 入库 stock_flow 1 行 + location_stock += N）并扣减门店礼盒需求，不查/不消耗任何组件库存。</p>
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

    /** stock_flow.inout_type CHAR(3) IN=入库（沿 PigCutRecordServiceImpl 范式）。 */
    private static final String INOUT_IN = "IN";
    /** stock_flow.inout_type OUT=出库。 */
    private static final String INOUT_OUT = "OUT";
    /** 白条出库 flow_type（邓博 row13：白条离白条库统一「白条出库」，去向区分白条分割 / 发货月台 / 仓库出库）。 */
    private static final String FLOW_TYPE_CUT_OUT = "cut_out";
    /** 出库去向：发货月台。 */
    private static final String STOCK_OUT_DEST_SHIP_DOCK = "ship_dock";

    /**
     * 预冷损耗类型（字典 {@code djs_loss_type}）：白条入白条库重 − 出白条库重（口径同 cut 模块
     * {@code PigCutRecordServiceImpl.LOSS_TYPE_PRECOOL}）。发货月台 / 仓库出库离白条库时补记。
     */
    private static final String LOSS_TYPE_PRECOOL = "precool_loss";
    /** 白条出库损耗来源业务标识（loss_flow.source_biz_type）。 */
    private static final String LOSS_SOURCE_BIZ_WHITEBAR_OUT = "whitebar_out";

    /** stock_flow.flow_type 业务类型。 */
    private static final String FLOW_TYPE_PACK_IN = "pack_in";

    /** djs_pack_status 字典码值。 */
    private static final String PACK_STATUS_PACKED = "packed";

    /** V1 单租户常量（未启全局 MP 多租户拦截器；原生 @Update / @Select 需显式带 tenant_id）。 */
    private static final String TENANT_V1 = "1001";

    /**
     * product_inhouse.source 值：warehouse=仓库分割产（WMS-PIG-002 / WMS-VEG-001）。
     *
     * <p>5 个打包/出库来源 picker 与 {@link #requireActiveInhouse} 只认 warehouse 来源 inhouse，
     * 排除门店现场分割（source='store'，STR-SPLIT-001），实现跨域库存隔离。</p>
     */
    private static final String SOURCE_WAREHOUSE = "warehouse";

    /** djs_yes_no 字典码值。 */
    private static final Integer YN_NO = 0;

    /** 礼盒归属类型（belong_type）：礼盒 = product_type=1 自产 + belong_type=gift_box（djs_product_type 已废弃 3）。 */
    private static final String BELONG_TYPE_GIFT_BOX = "gift_box";

    /** 发送位置=礼盒：该打包成品是礼盒组件，预留给礼盒打包消耗，不进发货月台、不扣门店直接需求。 */
    private static final String DELIVER_DEST_GIFT = "gift";

    /** 白条/猪肉业态（WMS-WHITEBAR-SHIP-001 出库发货来源校验）。 */
    private static final String BELONG_TYPE_WHITE_BAR = "white_bar";
    private static final String BELONG_TYPE_PORK = "pork";

    /** 果蔬业态（V4 果蔬打包 product_material 校验 + 日损耗聚合）。 */
    private static final String BELONG_TYPE_VEGETABLE = "vegetable";

    /** 打包台「当天」时区：与发货月台 {@code SHIP_TODAY_ZONE} 一致，避免非 UTC+8 实例跨日归错天。 */
    private static final ZoneId PACK_TODAY_ZONE = ZoneId.of("Asia/Shanghai");

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

    /**
     * 白条领用收口（FIX-WMS-CUTPICKUP-SPLIT-001）：发货掉一个燎毛产出行后，若该白条所有行已处理完且有
     * 分割领用行，委托 cut service 推 pending_cut + 建整猪 cut_record。@Lazy 字段注入（非构造器）——
     * 避免改构造器签名破坏既有单测 + 防潜在初始化次序问题；仅 whiteBarOut 用，按需触发。
     */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private org.dromara.djs.warehouse.cut.service.IPigCutRecordService pigCutService;

    /**
     * 白条离白条库补记预冷损耗（bug5：发货月台 / 仓库出库漏记预冷损耗）。@Lazy 字段注入（非构造器）——
     * 同 pigCutService，避免改构造器签名破坏既有单测。{@code record} 内部对 lossWeight&lt;=0 自动跳过。
     */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private org.dromara.djs.warehouse.loss.service.ILossFlowService lossFlowService;

    /**
     * DENGBO-R16：打包 / 生产定格展示名（果蔬按原材料作物有机证书取产品名 / 别名）。字段注入（非构造器）——
     * 同 pigCutService / lossFlowService，避免改构造器签名破坏既有单测。
     */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private org.dromara.djs.warehouse.product.service.IProductDisplayNameResolver displayNameResolver;

    /**
     * DENGBO-R16：产出记录定格展示名 = 果蔬按原材料作物有机证书取产品名 / 别名，其余业态产品名。
     * 冗余列 {@code product_production.product_name} 一次定格（生产列表 / 果蔬打包间读该冗余列 MAX(product_name)），
     * 产品事后改名 / 证书过期不影响历史产出记录。resolver 内部兜底 productName，绝不返回空。
     *
     * <p>{@code displayNameResolver} 为 {@code @Lazy} 字段注入；无 Spring 上下文（单测直接 new）时为 null，
     * 此时兜底产品当前名（不 NPE，等价旧行为）。</p>
     */
    private String resolveProductionName(ProductInfo product) {
        if (displayNameResolver == null) {
            return product.getProductName();
        }
        return displayNameResolver.resolveDisplayName(product.getId(), product.getProductName());
    }

    public ProductProductionServiceImpl(ProductProductionMapper baseMapper,
                                        ProductInhouseMapper productInhouseMapper,
                                        ProductInfoMapper productInfoMapper,
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
        p.setProductName(resolveProductionName(product));
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
        // row161/162：原材料溯源列后端自动回填（前端 bo 从不传 material_id/material_consume，旧写法恒 NULL）。
        // material_id = 来源 inhouse 产品 id（=领用进 inhouse 的原料 productId）；material_consume = 本次消耗重量（=打包重量）。
        fillMaterialTrace(p, src, bo.getProductWeight());
        p.setProduceLocation(locationId);
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setDeliverDest(bo.getDeliverDest());
        p.setProofOssIds(bo.getProofOssIds());
        p.setRemark(bo.getRemark());
        baseMapper.insert(p);

        // row42：生产产品不入库（仓库按门店需求打包 → 直送发货月台 → 门店，不进 location_stock / 不写 pack_in 入库流水）。
        // 来源原材料仍消耗（软删 inhouse 整 row）。
        consumeInhouse(src, bo.getProductWeight());

        // Step 8：生成追溯码回填 + 写 in_stock 事件（TRC-CORE-001）
        fillTraceCode(p, src.getEarNo(), src.getPlotId());

        // Step 9：履约门店需求（需求 C）。发送位置=礼盒 → 该成品是礼盒组件（预留给礼盒打包消耗、不进发货
        // 月台），不扣门店直接需求、不强制选门店；其余（发货月台）= 直接履约，须选门店 + 打包即扣需求（发货不再扣）。
        // 扣减量按需求单位口径（果蔬份-单位：1 次打包 = 1 份，与重量无关）。
        fulfillDirectDemandOnPack(product.getId(), bo.getStoreId(),
            resolveDemandDeductQty(product, bo.getProductWeight()), bo.getDeliverDest());

        log.info("[WMS-PACK-001] veg pack done id={} produceNo={} weight={} traceCode={}",
            p.getId(), p.getProduceNo(), bo.getProductWeight(), p.getTraceCode());
        return p.getId();
    }

    /**
     * 礼盒打包（礼盒为独立成品）：打 N 盒只产出 N 盒礼盒成品 + 扣门店礼盒需求，不查/不消耗任何组件（BOM）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitGiftPack(GiftPackBo bo) {
        Long userId = currentUserIdSafe();
        Date now = new Date();

        // Step 1：校验礼盒 SKU 存在 + 是礼盒（belong_type=gift_box；djs_product_type 已废弃 3，礼盒走 type=1 自产）
        ProductInfo giftBoxProduct = requireDeliveryProduct(bo.getGiftBoxProductId());
        if (!BELONG_TYPE_GIFT_BOX.equals(giftBoxProduct.getBelongType())) {
            throw new ServiceException("产品不是礼盒类型（belong_type != gift_box）：" + bo.getGiftBoxProductId());
        }
        // 入库库位（前端收银台不采集，可空 → 默认取礼盒产品配置库位/首个可用库位兜底）
        Long locationId = resolveLocationId(bo.getLocationId(), giftBoxProduct);
        // D-2：目标库位盘点锁定中 → 拒绝入库
        stockCheckService.assertLocationUnlocked(locationId);

        // Step 2：INSERT product_production（礼盒主记录）
        BigDecimal giftWeight = new BigDecimal(bo.getPackBoxCount());
        ProductProduction p = new ProductProduction();
        p.setProduceDate(java.sql.Date.valueOf(LocalDate.now()));
        p.setProduceNo(generateProduceNo(giftBoxProduct.getBelongType()));
        p.setProductId(giftBoxProduct.getId());
        p.setProductName(resolveProductionName(giftBoxProduct));
        // 产出记录沿用礼盒源产品的 product_type（礼盒走 type=1 自产；djs_product_type 已废弃 3）
        p.setProductType(giftBoxProduct.getProductType() != null ? giftBoxProduct.getProductType() : 1);
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

        // row42：生产产品不入库（礼盒打包成品直送发货月台，不进 location_stock / 不写入库流水）。

        // 生成追溯码回填 + 写 in_stock 事件（TRC-CORE-001）；礼盒无 earNo / plotId
        fillTraceCode(p, null, null);

        // 打包即扣需求（需求 C）——礼盒 storeId 可空，helper 内对 null 安全跳过
        deductDemandOnPack(giftBoxProduct.getId(), bo.getStoreId(), giftWeight);

        log.info("[WMS-PACK-001] gift pack done id={} produceNo={} packBoxCount={} traceCode={}",
            p.getId(), p.getProduceNo(), bo.getPackBoxCount(), p.getTraceCode());
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
        p.setProductName(resolveProductionName(product));
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
        // row161/162：原材料溯源列后端自动回填（来源 inhouse 产品 id + 本次消耗重量）。
        fillMaterialTrace(p, src, bo.getProductWeight());
        p.setProduceLocation(locationId);
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setDeliverDest(bo.getDeliverDest());
        p.setProofOssIds(bo.getProofOssIds());
        p.setRemark(bo.getRemark());
        baseMapper.insert(p);

        // row42：生产产品不入库（直送发货月台，不进 location_stock / 不写入库流水）。
        consumeInhouse(src, bo.getProductWeight());

        // 肉品打包原材料库存校验 + 扣减（猪肉全闭环 Part I P8）：
        // 仅 belong_type=pork 且目标产品配了 product_material（关联原材料）时校验，
        // 生产重量 > 原材料库存 → 抛 ServiceException（前端弹窗禁止）。未配 product_material 降级跳过（不阻塞）。
        // 分支隔离：仅 pork + 配料命中，干货/果蔬/其他 dry 打包现状不受影响。
        deductPorkMaterialIfConfigured(product, bo.getProductWeight(), userId, now);

        // 生成追溯码回填 + 写 in_stock 事件（TRC-CORE-001）
        fillTraceCode(p, src.getEarNo(), src.getPlotId());

        // 履约门店需求（需求 C）——肉品/干货/其他打包均走此口。发送位置=礼盒 → 礼盒组件，不扣门店直接需求；
        // 其余（发货月台）= 直接履约，须选门店 + 打包即扣需求（发货不再扣）。扣减量按需求单位口径
        // （肉品 kg 单位=按重量；其他产品份数模式=按份数；份-单位无计量规则=1 次 1 份）。
        fulfillDirectDemandOnPack(product.getId(), bo.getStoreId(),
            resolveDemandDeductQty(product, bo.getProductWeight()), bo.getDeliverDest());

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
        p.setProductName(resolveProductionName(product));
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
        // row161/162：原材料溯源列后端自动回填（来源 inhouse 产品 id + 本次消耗重量）。
        fillMaterialTrace(p, src, bo.getProductWeight());
        p.setProduceLocation(bo.getLocationId());
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setProofOssIds(bo.getProofOssIds());
        p.setRemark(bo.getRemark());
        baseMapper.insert(p);

        // row42：生产产品不入库（直送发货月台，不进 location_stock / 不写入库流水）。
        consumeInhouse(src, bo.getProductWeight());

        // 生成追溯码回填 + 写 in_stock 事件（TRC-CORE-001）；芹菜无 earNo
        fillTraceCode(p, null, src.getPlotId());

        // 打包即扣需求（需求 C）——按门店最早未完成需求扣减（发货不再扣）。芹菜打包无礼盒发送位置、恒直接履约。
        // 扣减量按需求单位口径（份-单位：1 次打包 = 1 份）。
        deductDemandOnPack(product.getId(), bo.getStoreId(), resolveDemandDeductQty(product, bo.getProductWeight()));

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
        p.setProductName(resolveProductionName(product));
        p.setProductType(product.getProductType() != null ? product.getProductType() : 1);
        p.setProductUnit(StringUtils.isNotBlank(product.getProductUnit()) ? product.getProductUnit() : "kg");
        p.setProductSpec(product.getProductSpec());
        p.setEarNo(src.getEarNo());
        p.setWhiteBarNo(src.getWhiteBarNo());
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

        // row42：生产产品不入库（直送发货月台，不进 location_stock / 不写入库流水）。
        // DENGBO row28：发货月台出库 = 整条产出行离库——称重出库 + 差额（产出重−称重）= 预冷损耗（下方
        // writePrecoolLossOnBarOut 已记）。按「整行重量」消耗软删，不留零头残行在「分割白条领用」列表
        //（原按称重量 bo.getProductWeight() 消耗，产出 80kg 称 78kg 会残 2kg pickup_status=0 卡）。
        consumeInhouse(src, src.getProductWeight());

        // P3（邓博 row13）：白条离白条库 = 白条出库。补白条库出库流水（去向=发货月台）+ 扣白条库存行
        // （P2 燎毛按 product_id+ear_no+burn_id 建的白条行）。修「白条库出库记录缺失致库存不准」。
        writeWhiteBarOutFlow(src, bo.getProductWeight(), STOCK_OUT_DEST_SHIP_DOCK, userId);

        // bug5：发货月台出库漏记预冷损耗。补记预冷损耗（入白条库重 − 本次出库重，非负；为 0 时 record 自动跳过）。
        writePrecoolLossOnBarOut(src, bo.getProductWeight(), p.getId(), userId);

        // 发货月台领用回写 bar 出库基础数据（猪肉全闭环 Part I P6 + FIX-WMS-CUTPICKUP-SPLIT-001 按产出行）：
        // 本行已 consumeInhouse（满发=软删 / 部分=扣减）。白条按燎毛产出行处理——**发货一个半只不连坐整 bar**：
        //   · 该白条还有未领产出行 → 不转态，留 in_stock 让剩余半只继续可领/可分割（修「另一半凭空消失」）；
        //   · 全部处理完 + 有分割领用行 → 委托 cut service 推 pending_cut + 建整猪 cut_record；
        //   · 全部处理完 + 全发货（无分割行）→ 整 bar 转 ship_out（沿用原写回）。
        // affectedRows==0（bar 已不在 in_stock）→ 静默跳过，幂等。
        Long whiteBarId = src.getWhiteBarId();
        if (whiteBarId != null) {
            Long remaining = productInhouseMapper.selectCount(
                new LambdaQueryWrapper<ProductInhouse>()
                    .eq(ProductInhouse::getWhiteBarId, whiteBarId)
                    .and(w -> w.eq(ProductInhouse::getPickupStatus, 0).or().isNull(ProductInhouse::getPickupStatus)));
            if (remaining == null || remaining == 0) {
                Long cutRows = productInhouseMapper.selectCount(
                    new LambdaQueryWrapper<ProductInhouse>()
                        .eq(ProductInhouse::getWhiteBarId, whiteBarId)
                        .eq(ProductInhouse::getPickupStatus, 1));
                if (cutRows != null && cutRows > 0) {
                    // 尚有分割领用行 → cut service 收口（pending_cut + cut_record），不转 ship_out
                    Long cutRecordId = pigCutService.finalizeBarPickupIfComplete(whiteBarId, userId);
                    log.info("[WHITEBAR-SHIP-P6] bar 含分割领用行，发货后委托收口 barId={} cutRecordId={}", whiteBarId, cutRecordId);
                } else {
                    int barAffected = barInfoMapper.updateStatusToShipOut(whiteBarId, now, bo.getProductWeight(), userId);
                    log.info("[WHITEBAR-SHIP-P6] bar ship-out writeback barId={} affected={} (0=不在in_stock态,跳过)",
                        whiteBarId, barAffected);
                }
            } else {
                log.info("[WHITEBAR-SHIP-P6] bar 尚有未领产出行({})，发货不连坐整 bar，留 in_stock barId={}", remaining, whiteBarId);
            }
        }

        // row205（邓博 2026-07-05）：发货月台出库补记「白条领用表」cut_record（out_type=ship，领用即终态）。
        // 记出库位置 + 预冷损耗 + 排酸时长 + 目标门店/需求。target_demand_id 与门店下拉同口径（含已完成需求、优先未完成）
        // ——门店因当天有该产品需求才可选，选中即回填该需求；无匹配需求 → null。分割统计只算 out_type='cut'，本行不计入。
        BarInfo shipBar = whiteBarId != null ? barInfoMapper.selectById(whiteBarId) : null;
        DemandManage shipDemand = demandManageMapper.selectShipTargetDemand(product.getId(), bo.getStoreId());
        Long shipDemandId = shipDemand != null ? shipDemand.getId() : null;
        Long shipCutRecordId = pigCutService.insertOutRecord("ship", shipBar, src, bo.getProductWeight(),
            bo.getStoreId(), shipDemandId, userId, null);
        log.info("[WHITEBAR-SHIP-OUT-RECORD] ship cut_record id={} storeId={} demandId={}",
            shipCutRecordId, bo.getStoreId(), shipDemandId);

        // 生成追溯码回填 + 写 in_stock 事件（TRC-CORE-001 pork 链首个 genCode 入口）
        fillTraceCode(p, src.getEarNo(), null);

        // 白条/猪肉整只「出库即发货」= 该门店需求的履约动作（白条无打包步骤）。需求 C 把需求扣减
        // 前移到履约源头：打包品在 submit*Pack 扣，白条在此出库扣；发货确认（CROSS-FLOW-003）不再扣，
        // 三者互斥不双扣。store_id 上方已校验门店存在（必填）。扣减量按需求单位口径
        // （白条整只=只-单位 → 1 次出库 = 1 只；按重量白条/猪肉 → 按重量）。
        deductDemandOnPack(product.getId(), bo.getStoreId(), resolveDemandDeductQty(product, bo.getProductWeight()));

        log.info("[WMS-WHITEBAR-SHIP-001] white_bar/pork out done id={} produceNo={} belongType={} weight={} store={} traceCode={}",
            p.getId(), p.getProduceNo(), belongType, bo.getProductWeight(), bo.getStoreId(), p.getTraceCode());
        return p.getId();
    }

    /**
     * 白条/猪肉「仓库出库」（row17）。
     *
     * <p>白条领用页第 3 个出库位置「仓库出库」：把白条整只(燎毛产)/猪肉部位(分割产)的 inhouse 出库，
     * 但 <b>不发往门店</b>（区别于发货月台）、<b>不进分割</b>（区别于分割车间）。出库去向记
     * {@code bo.outDest}（字典 {@code djs_bar_out_dest}），出库方式=后台出库（记流水 remark）。
     * 复用 {@link #submitWhiteBarOut} 收尾范式（insertProduction + consumeInhouse + writeWhiteBarOutFlow +
     * 补预冷损耗 + fillTraceCode），但 <b>不校验门店、不扣门店需求、不回写 bar 状态机</b>
     * （bar 状态机转态口径待邓博确认，见 report 待确认项③）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitWarehouseOut(WarehouseOutBo bo) {
        Long userId = currentUserIdSafe();
        Date now = new Date();

        ProductInhouse src = requireActiveInhouse(bo.getSourceInhouseId());
        ProductInfo product = productInfoMapper.selectById(src.getProductId());
        if (product == null) {
            throw new ServiceException("来源产品主数据不存在：" + src.getProductId());
        }
        String belongType = product.getBelongType();
        if (!BELONG_TYPE_WHITE_BAR.equals(belongType) && !BELONG_TYPE_PORK.equals(belongType)) {
            throw new ServiceException("仓库出库仅限白条/猪肉，当前来源业态=" + belongType);
        }
        requireInhouseEnough(src, bo.getProductWeight());
        // D-2：来源 inhouse 所在库位盘点锁定中 → 拒绝出库（白条整只 inhouse 常无 locationId，对 null 安全跳过）
        stockCheckService.assertLocationUnlocked(src.getLocationId());

        ProductProduction p = new ProductProduction();
        p.setProduceDate(java.sql.Date.valueOf(LocalDate.now()));
        p.setProduceNo(generateProduceNo(belongType));
        p.setProductId(product.getId());
        p.setProductName(resolveProductionName(product));
        p.setProductType(product.getProductType() != null ? product.getProductType() : 1);
        p.setProductUnit(StringUtils.isNotBlank(product.getProductUnit()) ? product.getProductUnit() : "kg");
        p.setProductSpec(product.getProductSpec());
        p.setEarNo(src.getEarNo());
        p.setWhiteBarNo(src.getWhiteBarNo());
        p.setProductSort(1);
        p.setProductWeight(bo.getProductWeight());
        p.setProduceQuantity(bo.getProductWeight());
        p.setProduceTime(now);
        p.setIsDeliveryCheck(YN_NO);
        p.setIsArrivalConfirm(YN_NO);
        p.setProduceLocation(src.getLocationId());
        p.setPackStatus(PACK_STATUS_PACKED);
        p.setProofOssIds(bo.getProofOssIds());
        // 出库方式=后台出库 + 出库去向记入备注（双语义落库口径待邓博确认，见 report 待确认项①）
        p.setRemark(buildWarehouseOutRemark(bo.getOutDest(), bo.getRemark()));
        baseMapper.insert(p);

        // 消耗来源 inhouse。DENGBO row28：仓库出库同发货月台——整条产出行离库（称重出 + 差额=预冷损耗），
        // 按「整行重量」消耗软删，不留「产出重−称重」零头残行在「分割白条领用」列表。
        consumeInhouse(src, src.getProductWeight());

        // 白条离白条库 = 白条出库：写「白条出库」流水（去向=用户选的 outDest）+ 扣白条库存行（与发货月台同范式）
        writeWhiteBarOutFlow(src, bo.getProductWeight(), bo.getOutDest(), userId);

        // bug5 同因：仓库出库同样补记预冷损耗（入白条库重 − 本次出库重，非负；为 0 时 record 自动跳过）
        writePrecoolLossOnBarOut(src, bo.getProductWeight(), p.getId(), userId);

        // 生成追溯码回填 + 写 in_stock 事件（与发货月台同）
        fillTraceCode(p, src.getEarNo(), null);

        // 仓库出库不发往门店，不扣门店需求（区别于发货月台的 deductDemandOnPack）。
        // 后台出库 = 终态（邓博 2026-07-02：矿山/厨房直接来仓库拿走，拿走即终结，不发货、不走门店逻辑）。
        // 沿发货月台同款收口（本行已 consumeInhouse）：该白条所有产出行处理完 → 有分割领用行则 cut 路径接管、
        // 不转终态；否则整 bar 转终态 cut_done + out_method=3（后台出库）。尚有未领行 → 不连坐整 bar，留 in_stock。
        Long whiteBarId = src.getWhiteBarId();
        if (whiteBarId != null) {
            Long remaining = productInhouseMapper.selectCount(
                new LambdaQueryWrapper<ProductInhouse>()
                    .eq(ProductInhouse::getWhiteBarId, whiteBarId)
                    .and(w -> w.eq(ProductInhouse::getPickupStatus, 0).or().isNull(ProductInhouse::getPickupStatus)));
            if (remaining == null || remaining == 0) {
                Long cutRows = productInhouseMapper.selectCount(
                    new LambdaQueryWrapper<ProductInhouse>()
                        .eq(ProductInhouse::getWhiteBarId, whiteBarId)
                        .eq(ProductInhouse::getPickupStatus, 1));
                if (cutRows != null && cutRows > 0) {
                    Long cutRecordId = pigCutService.finalizeBarPickupIfComplete(whiteBarId, userId);
                    log.info("[DENGBO-R17] warehouse-out bar 含分割领用行，交 cut 路径收口 barId={} cutRecordId={}", whiteBarId, cutRecordId);
                } else {
                    int barAffected = barInfoMapper.updateStatusToWarehouseOut(whiteBarId, now, bo.getProductWeight(), userId);
                    log.info("[DENGBO-R17] warehouse-out bar 转终态 cut_done(out_method=3) barId={} affected={}", whiteBarId, barAffected);
                }
            } else {
                log.info("[DENGBO-R17] warehouse-out bar 尚有未领产出行({})，不连坐整 bar，留 in_stock barId={}", remaining, whiteBarId);
            }
        }

        // row205（邓博 2026-07-05）：仓库出库补记「白条领用表」cut_record（out_type=warehouse，领用即终态）。
        // 记出库位置 + 预冷损耗 + 排酸时长；不发往门店（无 target_store_id/target_demand_id）。分割统计只算 out_type='cut'。
        // admin row2（邓博 2026-07-06）：把页面所选出库去向 bo.getOutDest() 落到 cut_record.out_dest。
        BarInfo whBar = whiteBarId != null ? barInfoMapper.selectById(whiteBarId) : null;
        Long whCutRecordId = pigCutService.insertOutRecord("warehouse", whBar, src, bo.getProductWeight(),
            null, null, userId, bo.getOutDest());
        log.info("[WAREHOUSE-OUT-RECORD] warehouse cut_record id={} outDest={}", whCutRecordId, bo.getOutDest());

        log.info("[DENGBO-R17] warehouse out done id={} produceNo={} belongType={} weight={} outDest={} traceCode={}",
            p.getId(), p.getProduceNo(), belongType, bo.getProductWeight(), bo.getOutDest(), p.getTraceCode());
        return p.getId();
    }

    /**
     * 拼「后台出库」+ 出库去向 + 用户备注 → product_production.remark（双语义落库口径待邓博确认）。
     */
    private String buildWarehouseOutRemark(String outDest, String userRemark) {
        StringBuilder sb = new StringBuilder("后台出库 去向=").append(StringUtils.isBlank(outDest) ? "-" : outDest);
        if (StringUtils.isNotBlank(userRemark)) {
            sb.append(" ").append(userRemark);
        }
        return sb.toString();
    }

    /**
     * 白条离白条库 → 写「白条出库」流水 + 扣 P2 燎毛按 {@code (product_id, ear_no, burn_id)} 建的白条库存行。
     *
     * <p>邓博 row13：白条无论去分割间还是发货月台，都要从白条库正常出库（修「白条库出库记录缺失致库存不准」）。
     * {@code dest} 区分去向（{@code ship_dock} 发货月台 / {@code bar_cut} 白条分割）。
     * {@code burn_id} 为空（外购 / 旧数据）→ 跳过库存行扣减、流水仍按 ear_no 写（优雅降级，不阻断领用）。</p>
     */
    private void writeWhiteBarOutFlow(ProductInhouse src, BigDecimal weight, String dest, Long userId) {
        if (StringUtils.isNotBlank(src.getWhiteBarNo())) {
            LocationStock barStock = locationStockMapper.selectOne(
                new LambdaQueryWrapper<LocationStock>()
                    .eq(LocationStock::getProductId, src.getProductId())
                    .eq(LocationStock::getWhiteBarNo, src.getWhiteBarNo())
                    .gt(LocationStock::getProductStock, BigDecimal.ZERO)
                    .last("LIMIT 1"));
            if (barStock != null) {
                locationStockMapper.deductStockById(barStock.getId(), weight, userId);
            }
        }
        StockFlow out = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_OUT);
        out.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        out.setFlowDate(new Date());
        out.setProductId(src.getProductId());
        out.setWarehouseId(src.getLocationId());
        out.setInoutType(INOUT_OUT);
        out.setFlowType(FLOW_TYPE_CUT_OUT);
        out.setStockOutDest(dest);
        out.setChangeNum(weight);
        out.setChangeQuantity(weight);
        out.setEarNo(src.getEarNo());
        out.setWhiteBarNo(src.getWhiteBarNo());
        out.setWhiteBarId(src.getWhiteBarId());
        out.setOperatorId(userId);
        stockFlowMapper.insert(out);
    }

    /**
     * 白条离白条库补记「预冷损耗」（bug5：发货月台 / 仓库出库出库时漏记预冷损耗）。
     *
     * <p>口径同 cut 模块 {@code PigCutRecordServiceImpl.writeCutLossFlows} 的 precool：
     * 预冷损耗 = 白条入白条库重（{@code src.productWeight}，该半只燎毛入库重）− 本次出库重（{@code outWeight}），
     * 非负钳制。{@code lossFlowService.record} 内部对 {@code lossWeight<=0} 自动跳过（无需调用方判断）。</p>
     *
     * @param src           来源白条 inhouse（含 productId / earNo / productWeight 入库重）
     * @param outWeight     本次出库重量
     * @param sourceBizId   来源单据 id（发货 / 仓库出库对应的 product_production.id）
     * @param userId        操作人
     */
    private void writePrecoolLossOnBarOut(ProductInhouse src, BigDecimal outWeight, Long sourceBizId, Long userId) {
        BigDecimal inWeight = src.getProductWeight() == null ? BigDecimal.ZERO : src.getProductWeight();
        BigDecimal precool = inWeight.subtract(outWeight == null ? BigDecimal.ZERO : outWeight).max(BigDecimal.ZERO);
        LossFlow loss = new LossFlow();
        loss.setLossType(LOSS_TYPE_PRECOOL);
        loss.setLossWeight(precool);
        loss.setProductId(src.getProductId());
        loss.setEarNo(src.getEarNo());
        loss.setOperatorId(userId);
        loss.setSourceBizType(LOSS_SOURCE_BIZ_WHITEBAR_OUT);
        loss.setSourceBizId(sourceBizId);
        lossFlowService.record(loss);
    }

    @Override
    public TableDataInfo<ProductProductionGroupVo> queryGroupPageList(ProductProductionQuery query, PageQuery pageQuery) {
        String produceNo = query == null ? null : query.getProduceNo();
        String productName = query == null ? null : query.getProductName();
        String belongType = query == null ? null : query.getBelongType();
        List<String> belongTypes = query == null ? null : query.getBelongTypes();
        Integer productType = query == null ? null : query.getProductType();
        Date from = query == null ? null : query.getProduceDateFrom();
        Date to = query == null ? null : query.getProduceDateTo();
        Integer hasDamage = query == null ? null : query.getHasDamage();
        // belongType / belongTypes（产品品类多选，R70 优先 IN，product_info 维度）/ productType（组内同值）/
        // productName(LIKE) 下推 mapper WHERE；hasDamage（是否存在损坏）作用于组维度，下推 mapper HAVING（row50）
        List<ProductProductionGroupVo> all =
            baseMapper.selectProductionGroupList(produceNo, productName, belongType, belongTypes, productType, from, to, hasDamage);
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
    public TableDataInfo<WhiteBarShipmentVo> queryWhiteBarShipmentList(WhiteBarShipmentQuery query, PageQuery pageQuery) {
        List<WhiteBarShipmentVo> all = queryWhiteBarShipmentList(query);
        // 全量查 + 内存分页（白条出库记录行数小，范式同 queryGroupPageList）
        int total = all.size();
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 10 : pageQuery.getPageSize();
        int fromIdx = Math.max(0, (pageNum - 1) * pageSize);
        int toIdx = Math.min(total, fromIdx + pageSize);
        List<WhiteBarShipmentVo> pageRows = fromIdx >= total ? List.of() : all.subList(fromIdx, toIdx);
        TableDataInfo<WhiteBarShipmentVo> rsp = new TableDataInfo<>();
        rsp.setRows(pageRows);
        rsp.setTotal(total);
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        return rsp;
    }

    @Override
    public List<WhiteBarShipmentVo> queryWhiteBarShipmentList(WhiteBarShipmentQuery query) {
        Date beginDate = query == null ? null : query.getBeginDate();
        Date endDate = query == null ? null : query.getEndDate();
        String earNo = query == null ? null : query.getEarNo();
        List<String> outMethods = query == null ? null : query.getOutMethods();
        List<String> outDests = query == null ? null : query.getOutDests();
        return baseMapper.selectWhiteBarShipmentList(beginDate, endDate, earNo, outMethods, outDests);
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
        // 锁定范围二选一：① 主列表下钻 = 生产批次（生产日期 + 产品）；② 门店损耗页 = 某需求（demandId，契约 a）。
        // 两者全缺 → 返回空（避免误拉全表逐件）。
        boolean byBatch = query != null && query.getProductId() != null && query.getProduceDate() != null;
        boolean byDemand = query != null && query.getDemandId() != null;
        if (!byBatch && !byDemand) {
            return TableDataInfo.build(List.of());
        }
        LambdaQueryWrapper<ProductProduction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(byBatch, ProductProduction::getProductId, query.getProductId())
            .apply(byBatch, "DATE(produce_date) = DATE({0})", query.getProduceDate())
            // 门店需求「产品明细」：排除礼盒组件产出（deliver_dest='gift'，预留礼盒打包、不履约门店直接需求），
            // 否则明细行数比需求量多出礼盒组件行。NULL-safe：白条/猪肉产出 deliver_dest 为 NULL，须保留
            // （裸 <> 'gift' 会因三值逻辑把 NULL 行也排除，导致白条明细消失）。
            .and(Boolean.TRUE.equals(query.getExcludeGiftDeliver()),
                w -> w.isNull(ProductProduction::getDeliverDest)
                    .or().ne(ProductProduction::getDeliverDest, DELIVER_DEST_GIFT))
            // 门店损耗页：按需求过滤逐件（契约 a）
            .eq(byDemand, ProductProduction::getDemandId, query.getDemandId())
            // 是否损坏过滤（契约 a；空=全部）
            .eq(query.getIsDamaged() != null, ProductProduction::getIsDamaged, query.getIsDamaged())
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
    @Transactional(rollbackFor = Exception.class)
    public void markDamage(MarkDamageBo bo) {
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("缺少生产记录 id");
        }
        // 标损时间由后端取 now（前端不传，避免客户端时钟漂移）
        int affected = baseMapper.updateDamage(bo.getId(), bo.getEvidenceOssIds(), bo.getRemark(), new Date());
        if (affected == 0) {
            throw new ServiceException("生产记录不存在或已删除：" + bo.getId());
        }
        log.info("[DENGBO-DAMAGE-001] mark damage id={} evidenceOssIds={} remark={}",
            bo.getId(), bo.getEvidenceOssIds(), bo.getRemark());
    }

    @Override
    public long countDamagedByDemand(Long demandId) {
        if (demandId == null) {
            return 0L;
        }
        return baseMapper.countDamagedByDemand(demandId);
    }

    @Override
    public BigDecimal sumDeliveredWeightToStore(Long storeId, Long productId, LocalDate date) {
        if (storeId == null || productId == null || date == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal weight = baseMapper.sumDeliveredWeightToStore(storeId, productId, date);
        return weight == null ? BigDecimal.ZERO : weight;
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
        // 打包台只显示「当天」已确认门店需求（与发货月台 demand_date=today 一致，Kevin 2026-06-26）。
        return demandManageMapper.selectStoreDemandCopies(productId, LocalDate.now(PACK_TODAY_ZONE));
    }

    @Override
    public Map<String, List<StoreDemandCopiesVo>> listStoreDemandCopiesBatch(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<org.dromara.djs.warehouse.pack.domain.vo.StoreDemandCopiesRowVo> rows =
            demandManageMapper.selectStoreDemandCopiesBatch(productIds, LocalDate.now(PACK_TODAY_ZONE));
        Map<String, List<StoreDemandCopiesVo>> out = new HashMap<>();
        for (org.dromara.djs.warehouse.pack.domain.vo.StoreDemandCopiesRowVo r : rows) {
            StoreDemandCopiesVo vo = new StoreDemandCopiesVo();
            vo.setStoreId(r.getStoreId());
            vo.setStoreName(r.getStoreName());
            vo.setCopies(r.getCopies());
            out.computeIfAbsent(String.valueOf(r.getProductId()), k -> new java.util.ArrayList<>()).add(vo);
        }
        return out;
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
        // 追溯码仅猪肉链（pork/white_bar）+ 果蔬（vegetable）生成；礼盒 / 干货 / 鸡蛋 / 其他产品不需要追溯码
        // （Kevin 2026-06-26）。非追溯业态直接 return：不生码、不回填 trace_code、不写 in_stock 追溯事件。
        ProductInfo product = productInfoMapper.selectById(p.getProductId());
        String belongType = product == null ? null : product.getBelongType();
        if (!BELONG_TYPE_PORK.equals(belongType)
            && !BELONG_TYPE_WHITE_BAR.equals(belongType)
            && !BELONG_TYPE_VEGETABLE.equals(belongType)) {
            return;
        }
        // 追溯码归属门店 = 该生产记录 store_id（需求 C：打印追溯码记门店）
        String produceCode = traceService.genCode(p.getProductId(), earNo, plotId, p.getStoreId());
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
     * 回填打包产出记录的原材料溯源列（row161/162 修复：material_id/material_consume 打包时从未落库）。
     *
     * <p>读侧 mapper（{@link #fillJoinNames} 按 material_id 关联原料名/单位）与前端列早已就绪，
     * 但写侧此前从 {@code bo.getMaterialId()/getMaterialConsume()} 取值 —— 前端打包提交体从不传这两字段 →
     * 全表恒 NULL。改由后端自动算：</p>
     * <ul>
     *   <li>{@code material_id} = 来源 {@code product_inhouse} 的 {@code product_id}
     *       （= 物资领用 {@code bridgeMaterialInhouse} 时那个进 inhouse 的原料 productId，与 consumeInhouse 扣减的同一行）。</li>
     *   <li>{@code material_consume} = 本次从该来源实际消耗的重量 kg（= 打包重量，与 {@link #consumeInhouse}
     *       的扣减量同口径）。</li>
     * </ul>
     *
     * <p>仅果蔬/干货/芹菜等「领用原料 → 打包成品」路径调用（源 inhouse ≠ 成品）。礼盒打包无来源原料
     * （独立成品、不消耗任何 inhouse），故不调用、两列保持 NULL 为正确语义。</p>
     */
    private void fillMaterialTrace(ProductProduction p, ProductInhouse src, BigDecimal consumeWeight) {
        if (src != null) {
            p.setMaterialId(src.getProductId());
        }
        p.setMaterialConsume(consumeWeight);
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
     * 打包即扣需求（需求 C）：替代原「发货确认扣 shipped_count」，避免双扣。
     *
     * <p>每个打包业态产出 {@code product_production} 后调用：按 {@code productId + storeId} 查该门店
     * <b>最早一条未完成需求</b>（{@link DemandManageMapper#selectOldestUncompletedDemand}），对其
     * {@code shipped_count} 原子累加本次打包量 {@code packQty}（{@link DemandManageMapper#incrementShipped}）。
     * 「门店需求剩余 = demand_quantity − shipped_count」，读取端已有 {@code GREATEST(...,0)} 防负兜底。</p>
     *
     * <p>{@code storeId} 为空（如礼盒未绑门店）或查不到匹配未完成需求 → log.warn 跳过，<b>不报错</b>
     * （打包本身已成功，需求扣减是附带的反向更新，不阻塞主链路）。</p>
     *
     * <p>注意：此处只累加 {@code shipped_count}，<b>不</b>推进需求状态机（避免脱离 sa-token 上下文的
     * operator 问题）；状态机推进仍由发货确认链路 {@code ShipmentConfirmedEventListener} 在到量时处理。</p>
     *
     * @param productId 打包目标产品 id
     * @param storeId   门店 id（可空）
     * @param packQty   本次打包产出量（与 demand_quantity 同单位）
     */
    /**
     * 组件打包（果蔬/猪肉/其他）的门店需求履约：按发送位置分流。
     *
     * <ul>
     *   <li>发送位置=礼盒（{@code deliver_dest='gift'}）：该成品是<b>礼盒组件</b>，预留给礼盒打包消耗、
     *       不进发货月台（{@code findAvailableProductionsForDemand} 已排除），故<b>不强制选门店、不扣门店直接需求</b>
     *       （门店需求由礼盒打包按盒数扣减；若此处也扣，门店同时下「散装某品 + 礼盒」时该品会被超扣）。</li>
     *   <li>其余（发货月台 {@code platform}/默认）：直接履约门店需求，<b>须选门店</b>（需求 C），
     *       打包即扣门店最早未完成需求（发货确认不再扣，防双扣）。</li>
     * </ul>
     */
    /**
     * 本次打包应扣的「门店需求量」。门店需求一律按「份/盒/只/枚」等<b>计数</b>口径下单（无按重量需求），
     * 故打包扣减也按份计：
     *
     * <ul>
     *   <li><b>按份数</b>（配了计量规则 {@code material_num>0}，如鸡蛋一盒 30 枚）：前端把 份数×material_num
     *       发为重量 → 还原份数 = 重量 ÷ material_num，按份数扣（1 次打包 = N 份）。</li>
     *   <li><b>按重量</b>（每份重量，如腊肉 {@code unit=kg} 无计量规则）+ 计数单位无计量（果蔬份-单位/白条整只）：
     *       1 次打包成功 = 1 份/只，与录入重量无关（Kevin 2026-06-26：腊肉录入的是「每份重量」，仍按份扣；
     *       「打包成功就是打包了一份」）。</li>
     * </ul>
     *
     * <p>礼盒打包不走此口（{@code submitGiftPack} 直接按盒数 packBoxCount 扣）。</p>
     */
    protected BigDecimal resolveDemandDeductQty(ProductInfo product, BigDecimal packWeightKg) {
        BigDecimal materialNum = product == null ? null : product.getMaterialNum();
        if (materialNum != null && materialNum.signum() > 0 && packWeightKg != null) {
            // 按份数模式：重量 = 份数 × material_num（前端换算），还原份数
            return packWeightKg.divide(materialNum, 3, java.math.RoundingMode.HALF_UP);
        }
        // 按重量（每份重量）/ 计数单位无计量：1 次打包 = 1 份/只，与重量无关
        return BigDecimal.ONE;
    }

    protected void fulfillDirectDemandOnPack(Long productId, Long storeId, BigDecimal packQty, String deliverDest) {
        if (DELIVER_DEST_GIFT.equals(deliverDest)) {
            // 礼盒组件：不绑门店、不扣直接需求（履约在礼盒打包环节）
            return;
        }
        if (storeId == null) {
            throw new ServiceException("请选择门店");
        }
        deductDemandOnPack(productId, storeId, packQty);
    }

    protected void deductDemandOnPack(Long productId, Long storeId, BigDecimal packQty) {
        if (storeId == null || productId == null || packQty == null || packQty.signum() <= 0) {
            return;
        }
        DemandManage demand = demandManageMapper.selectOldestUncompletedDemand(productId, storeId);
        if (demand == null) {
            log.warn("[PACK-DEMAND-DEDUCT] 打包未匹配到未完成需求，跳过扣减 productId={} storeId={} packQty={}",
                productId, storeId, packQty);
            return;
        }
        // row53-BE：打包份数硬拦——本次打包量不得超过该需求剩余份数（剩余 = demand_quantity − 已发货）。
        // 前端「剩余可打包份数」只是软提示，后端在写入前 fail-fast 防绕过/并发超打。
        BigDecimal demandQty = demand.getDemandQuantity() == null ? BigDecimal.ZERO : demand.getDemandQuantity();
        BigDecimal shipped = demand.getShippedCount() == null ? BigDecimal.ZERO : demand.getShippedCount();
        BigDecimal remain = demandQty.subtract(shipped);
        if (remain.signum() < 0) {
            remain = BigDecimal.ZERO;
        }
        if (packQty.compareTo(remain) > 0) {
            throw new ServiceException("打包份数超过该需求剩余份数：剩余 "
                + remain.stripTrailingZeros().toPlainString()
                + "，本次 " + packQty.stripTrailingZeros().toPlainString());
        }
        int rows = demandManageMapper.incrementShipped(demand.getId(), TENANT_V1, packQty);
        log.info("[PACK-DEMAND-DEDUCT] 打包即扣需求 demandId={} productId={} storeId={} packQty={} affected={}",
            demand.getId(), productId, storeId, packQty, rows);
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
        LocalDate today = LocalDate.now(PACK_TODAY_ZONE);
        for (Long productId : ids) {
            // 与上方「今天已打包份数」同口径：只比当天确认门店需求（packedDone 判定整门店是否打满）。
            List<StoreDemandCopiesVo> demands = demandManageMapper.selectStoreDemandCopies(productId, today);
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
            .like(StringUtils.isNotBlank(query.getProductName()), ProductProduction::getProductName, query.getProductName())
            .eq(query.getProductId() != null,    ProductProduction::getProductId, query.getProductId())
            .eq(query.getProductType() != null,  ProductProduction::getProductType, query.getProductType())
            .apply(StringUtils.isNotBlank(query.getProductSort()),
                "CAST(product_sort AS CHAR) LIKE CONCAT('%', {0}, '%')", query.getProductSort())
            .eq(StringUtils.isNotBlank(query.getPackStatus()), ProductProduction::getPackStatus, query.getPackStatus())
            .eq(StringUtils.isNotBlank(query.getEarNo()), ProductProduction::getEarNo, query.getEarNo())
            .eq(query.getPlotId() != null, ProductProduction::getPlotId, query.getPlotId())
            .eq(query.getStoreId() != null, ProductProduction::getStoreId, query.getStoreId());
        // 产品品类（belong_type）不在 product_production 列上，经 product_id 子查询过滤 product_info 维度。
        // R70 多选：belongTypes 非空 → 子查询 IN；否则 fallback 单值 belongType = ...（mp / 其他单值调用方）。
        boolean hasBelongTypes = query.getBelongTypes() != null && !query.getBelongTypes().isEmpty();
        if (hasBelongTypes) {
            // 用 MP 占位符 {0},{1}... 防注入（与本文件其他 .apply 同范式，参数化下推）
            List<String> bts = query.getBelongTypes();
            String placeholders = java.util.stream.IntStream.range(0, bts.size())
                .mapToObj(i -> "{" + i + "}")
                .collect(Collectors.joining(","));
            w.apply("product_id IN (SELECT id FROM t_warehouse_product_info WHERE belong_type IN ("
                + placeholders + ") AND del_flag = '0')", bts.toArray());
        } else {
            w.apply(StringUtils.isNotBlank(query.getBelongType()),
                "product_id IN (SELECT id FROM t_warehouse_product_info WHERE belong_type = {0} AND del_flag = '0')",
                query.getBelongType());
        }
        w
            // 是否存在损坏（导出沿用主列表 hasDamage 口径：作用到逐件 is_damaged）
            .eq(query.getHasDamage() != null, ProductProduction::getIsDamaged, query.getHasDamage())
            .eq(query.getIsDamaged() != null, ProductProduction::getIsDamaged, query.getIsDamaged())
            .apply(query.getProduceDate() != null, "DATE(produce_date) = DATE({0})", query.getProduceDate())
            // 生产日期范围（主列表导出按 produceDateFrom/To 过滤逐件，与列表可见行口径一致）
            .apply(query.getProduceDateFrom() != null, "DATE(produce_date) >= DATE({0})", query.getProduceDateFrom())
            .apply(query.getProduceDateTo() != null, "DATE(produce_date) <= DATE({0})", query.getProduceDateTo())
            .ge(query.getProduceTimeFrom() != null, ProductProduction::getProduceTime, query.getProduceTimeFrom())
            .le(query.getProduceTimeTo() != null,   ProductProduction::getProduceTime, query.getProduceTimeTo())
            .orderByDesc(ProductProduction::getProduceTime)
            .orderByDesc(ProductProduction::getId);
        return w;
    }

    /**
     * 批量回填 plotName / storeName / materialName / materialUnit（按 store_id / plot_id / material_id 跨域 IN 查，无 N+1）。
     *
     * <p>所属门店 / 来源地块列把 ID 显示成名称：store_id → {@code t_md_store.store_name}
     * （StoreMapper 在 ruoyi-djs-common），plot_id → {@code t_plant_plot_info.plot_name}
     * （PlotInfoMapper 在 ruoyi-djs-plant，warehouse 模块已依赖）。原材料名称 / 单位：material_id →
     * {@code t_warehouse_product_info.product_name / product_unit}（同模块 ProductInfoMapper，一次 IN 查回填两列）。</p>
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
        Set<Long> materialIds = rows.stream()
            .map(ProductProductionVo::getMaterialId)
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
        // 原材料名称 + 单位：material_id → product_info.product_name / product_unit（同模块表，一次 IN 查回填两列）
        List<ProductInfo> materialInfos = materialIds.isEmpty() ? List.of()
            : productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId, ProductInfo::getProductName, ProductInfo::getProductUnit)
                    .in(ProductInfo::getId, materialIds));
        Map<Long, String> materialNameMap = materialInfos.stream()
            .filter(pi -> pi.getProductName() != null)
            .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getProductName, (a, b) -> a));
        Map<Long, String> materialUnitMap = materialInfos.stream()
            .filter(pi -> pi.getProductUnit() != null)
            .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getProductUnit, (a, b) -> a));

        for (ProductProductionVo vo : rows) {
            if (vo.getStoreId() != null) {
                vo.setStoreName(storeNameMap.get(vo.getStoreId()));
            }
            if (vo.getPlotId() != null) {
                vo.setPlotName(plotNameMap.get(vo.getPlotId()));
            }
            if (vo.getMaterialId() != null) {
                vo.setMaterialName(materialNameMap.get(vo.getMaterialId()));
                vo.setMaterialUnit(materialUnitMap.get(vo.getMaterialId()));
            }
        }
    }

}
