package org.dromara.djs.warehouse.vegreceive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.domain.vo.CropProductVo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.crop.service.ICropProductService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.vegreceive.domain.VegReceive;
import org.dromara.djs.warehouse.vegreceive.domain.bo.VegInboundBo;
import org.dromara.djs.warehouse.vegreceive.domain.bo.VegPurchaseBo;
import org.dromara.djs.warehouse.vegreceive.domain.vo.VegInboundPlotVo;
import org.dromara.djs.warehouse.vegreceive.domain.vo.VegReceiveItemVo;
import org.dromara.djs.warehouse.vegreceive.mapper.VegReceiveMapper;
import org.dromara.djs.warehouse.vegreceive.service.IVegReceiveService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 果蔬月台收货 Service 实现（FIX-WMS-VEGRECEIVE-001）。
 *
 * <h3>跨表事务一致性（照 {@link org.dromara.djs.warehouse.flow.service.impl.PackingFlowServiceImpl} 范式）</h3>
 * <ul>
 *   <li>{@link #inbound} 自产：本表 INSERT（receiveType=1，落 product_id）+ {@code location_stock} 按
 *       <b>{@code productId + plotId} 双键</b> UPSERT（行锁增量 / 无行 INSERT）+ {@code stock_flow} INSERT
 *       （{@code veg_receive_in / IN}）。入库前用 {@link VegReceiveMapper#selectRemainInboundWeight} 按
 *       (作物, 产品, 地块) 校验剩余可入量，超量抛 {@link ServiceException}。</li>
 *   <li>{@link #purchase} 外购：resolve supplier（业务短码 → id+name）+ 本表 INSERT（receiveType=2）+
 *       {@code location_stock} 按 {@code productId} 维度 UPSERT（复用
 *       {@link LocationStockMapper#addByProductLocation}）+ {@code stock_flow} INSERT（{@code veg_purchase_in / IN}）。</li>
 *   <li>任一 RuntimeException / ServiceException 触发整体回滚（{@code @Transactional(rollbackFor = Exception.class)}）。</li>
 * </ul>
 *
 * <h3>库存增量行锁（防超扣 / 并发）</h3>
 * <p>plot+product 双键走 {@link VegReceiveMapper#addStockByPlotLocation}、纯 product 维度走
 * {@link LocationStockMapper#addByProductLocation}；UPDATE 行锁 + {@code del_flag} 限定，affectedRows=0
 * 时兜底 INSERT 新库存行。入库是"加库存"无上限校验，超量约束在收货记录侧（剩余可入量）。</p>
 *
 * @author djs
 * @since FIX-WMS-VEGRECEIVE-001
 */
@Slf4j
@Service
public class VegReceiveServiceImpl implements IVegReceiveService {

    /**
     * 出入库方向：IN=入（DDL CHAR(3)；果蔬收货仅入库）。
     */
    private static final String INOUT_IN = "IN";

    /**
     * 收货类型 {@code djs_veg_receive_type}：1=自产 / 2=外购。
     */
    private static final int RECEIVE_TYPE_SELF = 1;
    private static final int RECEIVE_TYPE_PURCHASE = 2;

    /**
     * 收货状态 {@code djs_veg_receive_status}：自产标记入库完成时 done，否则 processing；外购单次入库恒 done。
     */
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_DONE = "done";

    /**
     * 是否入库完成 {@code djs_yes_no}：1=是 / 2=否。
     */
    private static final int FINISH_YES = 1;
    private static final int FINISH_NO = 2;

    /**
     * {@code djs_flow_type} 字典 value（V202606200900 DDL 已 seed）。
     */
    private static final String FLOW_VEG_RECEIVE_IN = "veg_receive_in";
    private static final String FLOW_VEG_PURCHASE_IN = "veg_purchase_in";

    /**
     * 果蔬缩略图 L2 兜底分类键（IMG-LIB-001；自产 / 外购果蔬统一蔬菜默认图）。
     */
    private static final String CROP_BELONG_TYPE = "vegetable";

    /**
     * 自产月台入库允许的库位业务码白名单（spec 步10：仅蔬菜保鲜库 / 重口味蔬菜库）。
     *
     * <p>按 {@code location_code} 校验（不硬编码 snowflake id；staging 验证：L0003 id=…676306 /
     * L0004 id=…676307）。{@link #inbound} 入库前查库位 code 是否命中，否则拒绝。</p>
     */
    private static final Set<String> INBOUND_ALLOWED_LOCATION_CODES = Set.of("L0003", "L0004");

    private final VegReceiveMapper vegReceiveMapper;
    private final LocationStockMapper locationStockMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final StockFlowMapper stockFlowMapper;
    private final ProductInfoMapper productInfoMapper;
    private final SupplierMapper supplierMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ImageUrlResolver imageUrlResolver;
    /** 作物 mapper：自产月台入库解析 {@code crop.related_product} → 果蔬原料 product_id（双键篮，G2）。 */
    private final CropInfoMapper cropInfoMapper;
    /** 统一损耗门面（WMS-LOSS-001，行59）：地块入库完成时双写一条 {@code transport_loss} 运输损耗。 */
    private final ILossFlowService lossFlowService;
    /** 作物-产品配置（row55）：判断该作物是不是多产品，决定老客户端的收货请求放行还是拒绝。 */
    private final ICropProductService cropProductService;

    public VegReceiveServiceImpl(VegReceiveMapper vegReceiveMapper,
                                 LocationStockMapper locationStockMapper,
                                 LocationInfoMapper locationInfoMapper,
                                 StockFlowMapper stockFlowMapper,
                                 ProductInfoMapper productInfoMapper,
                                 SupplierMapper supplierMapper,
                                 IBizCodeGenerator bizCodeGenerator,
                                 ImageUrlResolver imageUrlResolver,
                                 CropInfoMapper cropInfoMapper,
                                 ILossFlowService lossFlowService,
                                 ICropProductService cropProductService) {
        this.vegReceiveMapper = vegReceiveMapper;
        this.locationStockMapper = locationStockMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.productInfoMapper = productInfoMapper;
        this.supplierMapper = supplierMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.imageUrlResolver = imageUrlResolver;
        this.cropInfoMapper = cropInfoMapper;
        this.lossFlowService = lossFlowService;
        this.cropProductService = cropProductService;
    }

    @Override
    public List<VegReceiveItemVo> listSelf() {
        return fillThumb(vegReceiveMapper.selectSelfPending());
    }

    @Override
    public List<VegReceiveItemVo> listPurchased(String productName, String productType) {
        // productType V1 不参与过滤（外购果蔬恒「果蔬产品」文案，预留扩展），仅按产品名模糊
        return fillThumb(vegReceiveMapper.selectPurchasedPending(productName));
    }

    /**
     * 批量回填 thumbUrl（IMG-LIB-001：L1 作物/产品 image_oss_id → L2 蔬菜默认图 → L3 全局），禁 N+1。
     */
    private List<VegReceiveItemVo> fillThumb(List<VegReceiveItemVo> list) {
        if (list.isEmpty()) {
            return list;
        }
        List<ImageUrlResolver.Item> items = list.stream()
            .map(v -> new ImageUrlResolver.Item(v.getImageOssId(), CROP_BELONG_TYPE))
            .toList();
        List<String> urls = imageUrlResolver.resolveList(items);
        if (urls.size() == list.size()) {
            for (int i = 0; i < list.size(); i++) {
                list.get(i).setThumbUrl(urls.get(i));
            }
        }
        return list;
    }

    @Override
    public List<VegInboundPlotVo> listInboundPlots(Long cropId, Long productId) {
        if (cropId == null) {
            throw new ServiceException("作物 ID 不能为空");
        }
        // row55：月台按产品聚合后，详情页收窄到该产品。
        // mp 没传 productId（已发布的老版本小程序）时**不按产品过滤**，返回该作物全部地块——
        // 这才真等价于改动前的整作物口径。曾经回落成 crop.related_product，实测是错的：
        // 红薯只返 1 块地（50kg），而改动前是 3 块地（200kg），老客户端会少看到货。
        List<VegInboundPlotVo> plots = vegReceiveMapper.selectInboundPlots(cropId, productId);
        // row3 方案B：按作物解析「默认入库库位」（同作物所有地块行一致），有值则回填给每行，mp 打开弹层预填、仍可改
        if (!plots.isEmpty()) {
            DefaultLocation def = resolveDefaultLocationByCrop(cropId);
            if (def != null) {
                for (VegInboundPlotVo p : plots) {
                    p.setDefaultLocationId(def.id());
                    p.setDefaultLocationName(def.name());
                }
            }
        }
        return plots;
    }

    /**
     * 默认入库库位解析结果（row3 方案B）：id + 名称。
     */
    private record DefaultLocation(Long id, String name) {
    }

    /**
     * 按作物解析「默认存储库位」（row3 方案B）：{@code crop.related_product} → 果蔬原料 product →
     * {@code product_info.store_location_id}（逗号分隔取第一个）→ {@code location_info.location_name}。
     *
     * <p>容错（任一缺失返 null，前端留空退回手选，不抛、不阻塞列表）：
     * 作物未配 related_product / 产品非果蔬原料（复用 {@link #resolveProductIdByCrop} 守门）/
     * {@code store_location_id} 为空 / 拆出的库位 id 非法 / 库位记录不存在或已软删。</p>
     */
    private DefaultLocation resolveDefaultLocationByCrop(Long cropId) {
        Long productId = resolveProductIdByCrop(cropId);
        if (productId == null) {
            return null;
        }
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null) {
            return null;
        }
        String storeLocationId = product.getStoreLocationId();
        if (storeLocationId == null || storeLocationId.isBlank()) {
            return null;
        }
        // store_location_id 逗号分隔多值，取第一个非空段
        String first = storeLocationId.split(",")[0].trim();
        if (first.isEmpty()) {
            return null;
        }
        Long locationId;
        try {
            locationId = Long.valueOf(first);
        } catch (NumberFormatException e) {
            log.warn("月台入库默认库位：产品 store_location_id 首段非法 — productId={} raw={}", productId, storeLocationId);
            return null;
        }
        LocationInfo location = locationInfoMapper.selectById(locationId);
        if (location == null) {
            return null;
        }
        return new DefaultLocation(locationId, location.getLocationName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long inbound(VegInboundBo bo) {
        Long userId = resolveOperator(bo.getOperatorId());

        // 0a. 校验入库库位口径（spec 步10：自产月台入库仅限蔬菜保鲜库 L0003 / 重口味蔬菜库 L0004）
        requireInboundLocation(bo.getLocationId());

        // row55：本次入的是哪个产品。写入侧必须落到一个确定的产品，见 resolveReceiveProductId 的说明。
        Long receiveProductId = resolveReceiveProductId(bo.getCropId(), bo.getProductId());

        // 1. 校验真实剩余可入量（月台量 − 已入 − 已结算损耗），超量拒绝（不凭空入库）。
        //    row66：不再「该地块有 is_finish 行就整地块锁死」——同地块当天可多趟送达、待入库叠加，
        //    某趟标记完成只结算当趟剩余为损耗；剩余可入=0 时本处自然拒绝，新送达使剩余>0 则可继续入。
        //    row55：额度按 (作物, 地块, 产品) 收窄——同地块红薯与红薯杆各有各的额度，不能互相吃。
        BigDecimal remain = vegReceiveMapper.selectRemainInboundWeight(
            bo.getCropId(), bo.getPlotId(), receiveProductId);
        BigDecimal remainSafe = remain != null ? remain : BigDecimal.ZERO;
        if (bo.getWeight().compareTo(remainSafe) > 0) {
            throw new ServiceException(
                "入库重量超过该地块剩余可入量（剩余 " + remainSafe.stripTrailingZeros().toPlainString()
                    + "kg，本次 " + bo.getWeight().stripTrailingZeros().toPlainString() + "kg）");
        }

        boolean finished = bo.getIsFinish() != null && bo.getIsFinish() == FINISH_YES;
        // row21：标记入库完成时，把本次入库后该地块剩余待入库量结算为损耗、待入库归 0；未完成损耗恒 0。
        BigDecimal loss = BigDecimal.ZERO;
        if (finished) {
            loss = remainSafe.subtract(bo.getWeight());
            if (loss.signum() < 0) {
                loss = BigDecimal.ZERO;
            }
        }
        String cropName = vegReceiveMapper.selectCropName(bo.getCropId());
        // 果蔬原料 product_id：篮子（step3）+ 流水（step4）都带它，与收货记录（step2）必须是同一个产品。
        //
        // row55：**不再在解析失败时回落成「按作物解析出来的另一个产品」**。
        // 回落过：收的是 A、篮子和流水却落 B（作物默认产品）——台账说 A、实物库存变成 B，
        // 而且 A、B 两笔还会因为 materialProductId 相同而并进同一个篮子，等于把第一轮修掉的串味换个姿势复活。
        // 现在的口径：本次收的产品过了「果蔬原料」守门就用它；没过就保持 null（退化成 plot 单键篮）+ 告警，
        // 宁可篮子少一个标签，也不把货记到别的产品名下。
        Long materialProductId = resolveVegMaterialProductId(receiveProductId);
        if (materialProductId == null && receiveProductId != null) {
            // 既不回落成别的产品（那是「台账说 A、库存变 B」），也不建无名篮（那更糟，见下），直接拒。
            //
            // 试过「退化成 product_id=NULL 的 plot 单键篮」，实测更坏：同一 (库位,地块) 上两个不同产品
            // 都过不了守门时，双双退化成 NULL，而增量 UPDATE 的 `product_id <=> #{productId}` 在 NULL 侧
            // 互相匹配 —— 两笔货并进同一张无名篮；更致命的是下游领用 consumeVegBaskets 用 `eq(product_id, ?)`
            // 永远匹配不到 NULL，这批货**领不出去、永久卡死**。
            // 能走到这里说明该产品已经在作物的产品配置里（上面 resolveReceiveProductId 校验过），
            // 却不是果蔬原料 —— 那是配置脏了，当场报出来让人去改，比默默造一笔死库存强。
            throw new ServiceException("产品「" + receiveProductId + "」不是果蔬原材料（需 product_attr=2 且 belong_type=vegetable），"
                + "不能走果蔬月台收货；请到 admin 作物管理 →「产品配置」检查该作物关联的产品", 400);
        }

        // 2. INSERT 收货记录（自产）
        String flowNo = generateFlowNo();
        VegReceive receive = new VegReceive();
        receive.setReceiveNo(flowNo);
        receive.setReceiveType(RECEIVE_TYPE_SELF);
        receive.setCropId(bo.getCropId());
        receive.setCropName(cropName);
        receive.setPlotId(bo.getPlotId());
        // row55：收货记录必须落产品，否则下一次列表算「已入库」时认不出这笔是收的哪个产品，
        // 待入库量会在两个产品之间串（红薯杆收了 50，红薯的待入库也跟着少 50）。
        receive.setProductId(receiveProductId);
        receive.setWeight(bo.getWeight());
        receive.setLossWeight(loss);
        receive.setLocationId(bo.getLocationId());
        receive.setIsFinish(finished ? FINISH_YES : FINISH_NO);
        receive.setReceiveStatus(finished ? STATUS_DONE : STATUS_PROCESSING);
        receive.setOperatorId(userId);
        receive.setReceiveTime(new Date());
        vegReceiveMapper.insert(receive);

        // 3. UPSERT location_stock（product+plot 双键行锁增量；无行兜底 INSERT 建账）
        //    row55：增量必须按 product_id 收窄，否则同一块地收的第二个产品会加到第一个产品的篮子上。
        int affected = vegReceiveMapper.addStockByPlotLocation(
            bo.getLocationId(), bo.getPlotId(), materialProductId, bo.getWeight(), userId);
        if (affected == 0) {
            // row55：篮子的展示名跟着 product_id 走。原来一律写作物名，红薯杆的篮子会显示成「红薯」
            // ——同一地块两个产品各建一行、名字却一样，库存页面根本分不出谁是谁。
            insertPlotStockRow(bo.getLocationId(), materialProductId, bo.getPlotId(),
                stockDisplayName(materialProductId, cropName), bo.getWeight(), userId);
        }

        // 4. INSERT stock_flow（veg_receive_in / IN，plot + product 关联）。
        //    带 product_id（果蔬原料）让 admin「果蔬月台入库」入库记录的「产品」列能显示原材料名（#3 修复：
        //    StockFlowServiceImpl.fillNames 按 product_id JOIN product_info 回填 productName，此前漏 set 故列空）。
        StockFlow flow = new StockFlow();
        flow.setFlowNo(flowNo);
        flow.setFlowDate(new Date());
        flow.setProductId(materialProductId);
        flow.setWarehouseId(bo.getLocationId());
        flow.setPlotId(bo.getPlotId());
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_VEG_RECEIVE_IN);
        flow.setChangeNum(bo.getWeight());
        flow.setChangeQuantity(bo.getWeight());
        flow.setOperatorId(userId);
        stockFlowMapper.insert(flow);

        // 5. 双写运输损耗（行59）：仅在地块标记入库完成（is_finish=1）时结算一次。
        //    运输损耗 = Σ该地块发往月台重(send_platform_weight) − Σ该地块已接收重(veg_receive.weight)。
        //    loss 变量上面已按 remainSafe(=月台量−已入量) − 本次入量 算出 = 本次终结后的剩余 = 运输损耗。
        //    正值才记（record 内部对 <=0 已跳过，此处再守一层显式表达意图）。
        if (finished && loss.signum() > 0) {
            LossFlow lossFlow = new LossFlow();
            lossFlow.setLossType("transport_loss");
            lossFlow.setLossWeight(loss);
            lossFlow.setProductId(materialProductId);   // 解析出果蔬原料才回填快照；为 null 时门面跳过回填，属可空
            lossFlow.setLocationId(bo.getLocationId());
            lossFlow.setPlotId(bo.getPlotId());
            lossFlow.setBelongType(CROP_BELONG_TYPE);
            lossFlow.setOperatorId(userId);
            lossFlow.setSourceBizType("transport");
            lossFlow.setSourceBizId(receive.getId());
            lossFlow.setSourceFlowId(flow.getId());
            // 产品未配 related_product 时 productCode/Name 留空，用作物名兜底标识来源
            if (materialProductId == null) {
                lossFlow.setProductName(cropName);
            }
            lossFlowService.record(lossFlow);
        }

        return receive.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long purchase(VegPurchaseBo bo) {
        Long userId = resolveOperator(bo.getOperatorId());

        // 1. 校验自产食材原料（product_type=1 且 product_attr=2）存在
        ProductInfo product = requirePurchaseProduct(bo.getCropId());

        // 2. resolve supplier（业务短码 → id+name；查不到只存名称、id 置 null，不阻塞入库）
        Supplier supplier = resolveSupplier(bo.getSupplier());

        // 3. INSERT 收货记录（外购）
        String flowNo = generateFlowNo();
        VegReceive receive = new VegReceive();
        receive.setReceiveNo(flowNo);
        receive.setReceiveType(RECEIVE_TYPE_PURCHASE);
        receive.setCropId(bo.getCropId());
        receive.setCropName(product.getProductName());
        receive.setProductId(bo.getCropId());
        receive.setWeight(bo.getWeight());
        receive.setSupplierId(supplier != null ? supplier.getId() : null);
        receive.setSupplierName(supplier != null ? supplier.getSupplierName() : bo.getSupplier());
        receive.setLocationId(bo.getLocationId());
        receive.setIsFinish(FINISH_NO);
        receive.setReceiveStatus(STATUS_DONE);
        receive.setOperatorId(userId);
        receive.setReceiveTime(new Date());
        vegReceiveMapper.insert(receive);

        // 4. UPSERT location_stock（product 维度行锁增量；无行兜底 INSERT 建账）
        int affected = locationStockMapper.addByProductLocation(
            bo.getLocationId(), bo.getCropId(), bo.getWeight(), userId);
        if (affected == 0) {
            insertProductStockRow(bo.getLocationId(), product, bo.getWeight(), userId);
        }

        // 5. INSERT stock_flow（veg_purchase_in / IN，product + supplier 关联）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(flowNo);
        flow.setFlowDate(new Date());
        flow.setProductId(bo.getCropId());
        flow.setWarehouseId(bo.getLocationId());
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_VEG_PURCHASE_IN);
        flow.setChangeNum(bo.getWeight());
        flow.setChangeQuantity(bo.getWeight());
        flow.setSupplierId(supplier != null ? supplier.getId() : null);
        flow.setOperatorId(userId);
        stockFlowMapper.insert(flow);

        return receive.getId();
    }

    // ============================ 内部辅助 ============================

    /**
     * 解析操作人：bo 传了用传的；为空取当前登录用户（端点已 {@code @SaCheckLogin}）。
     *
     * <p>protected 便于单测 stub。</p>
     */
    protected Long resolveOperator(Long operatorId) {
        return operatorId != null ? operatorId : LoginHelper.getUserId();
    }

    /**
     * 校验自产月台入库库位口径：按 {@code location_code} 命中白名单
     * {@link #INBOUND_ALLOWED_LOCATION_CODES}（L0003 蔬菜保鲜库 / L0004 重口味蔬菜库），否则拒绝。
     *
     * <p>按 code 校验（不硬编码 snowflake id）；库位不存在 / 已软删 / code 不在白名单均抛
     * {@link ServiceException}。protected 便于单测 stub。</p>
     */
    protected void requireInboundLocation(Long locationId) {
        if (locationId == null) {
            throw new ServiceException("月台入库库位只能是蔬菜保鲜库(L0003)或重口味蔬菜库(L0004)");
        }
        LocationInfo location = locationInfoMapper.selectById(locationId);
        if (location == null
            || !INBOUND_ALLOWED_LOCATION_CODES.contains(location.getLocationCode())) {
            throw new ServiceException("月台入库库位只能是蔬菜保鲜库(L0003)或重口味蔬菜库(L0004)");
        }
    }

    /**
     * 校验外购收货产品（{@code product_type=1 且 product_attr=2 且 is_buy_out=1}）存在，否则抛异常。
     *
     * <p>口径与蔬菜月台外购列表 {@link VegReceiveMapper#selectPurchasedPending} 一致：外购收货只认
     * 「原材料（{@code product_attr=2}）+ 是否支持外购=是（{@code is_buy_out=1}）」的产品
     * （{@code product_type=1}）；纯外购商品（{@code product_type=2}，饲料/药品/肥料/农药/包材等生产资料）
     * 走 admin 采购入库、不在此现场收货。是否支持外购由 admin 产品配置显式决定——未开外购的食材（如丝瓜
     * {@code is_buy_out=0}）不进外购收货选品。protected 便于单测 stub。</p>
     */
    protected ProductInfo requirePurchaseProduct(Long productId) {
        ProductInfo p = productInfoMapper.selectOne(
            new LambdaQueryWrapper<ProductInfo>().eq(ProductInfo::getId, productId).last("LIMIT 1"));
        if (p == null) {
            throw new ServiceException("外购产品不存在或已删除：" + productId);
        }
        boolean purchasable = p.getProductType() != null && p.getProductType() == 1
            && p.getProductAttr() != null && p.getProductAttr() == 2
            && p.getIsBuyOut() != null && p.getIsBuyOut() == 1;
        if (!purchasable) {
            throw new ServiceException("该产品未开启「支持外购」，不能走蔬菜月台外购收货：" + p.getProductName());
        }
        return p;
    }

    /**
     * 按业务短码 resolve 供应商；查不到 / 短码空返 null（service 容忍，只存名称串到 supplierName）。
     *
     * <p>protected 便于单测 stub。</p>
     */
    protected Supplier resolveSupplier(String supplierCode) {
        if (supplierCode == null || supplierCode.isBlank()) {
            return null;
        }
        return supplierMapper.selectOne(
            new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getSupplierCode, supplierCode)
                .last("LIMIT 1"));
    }

    /**
     * (库位, 地块, 产品) 三者上无库存行时 INSERT 新篮（自产果蔬月台中转再入库首次入某库位 / 地块 / 产品）。
     *
     * <p><b>G2 双键篮</b>：与直接入库（{@code VegetableHandleServiceImpl.insertVegStockInFlow}）口径对齐——
     * 同时 set {@code product_id} + {@code plot_id}（篮子标签），下游
     * {@code consumeVegBaskets(WHERE product_id=#{id})} 才领得到月台中转入库的库存，两池合一（G3）。</p>
     *
     * <p><b>row55</b>：{@code productId} 由调用方给出<b>本次实收的那个产品</b>（已过果蔬原料守门），
     * 不再由 {@code resolveProductIdByCrop} 按作物反解 —— 一块地可以先后收红薯和红薯杆，按作物反解
     * 会把两笔并进同一张篮。过不了守门的已在 {@link #inbound} 里被拒，不会走到这里建无名篮
     * （无名篮之间会因 {@code product_id <=> NULL} 互相合并，且下游 {@code eq(product_id, ?)} 永远领不出去）。</p>
     */
    private void insertPlotStockRow(Long locationId, Long productId, Long plotId, String cropName,
                                    BigDecimal stockQty, Long userId) {
        LocationStock stock = new LocationStock();
        stock.setLocationId(locationId);
        // G2：双键篮 = product_id + plot_id（篮子标签）。row55 起 product_id 由调用方给出本次实收的产品，
        // 且已在 inbound 里过完果蔬原料守门 —— 走到这里必非空，不会建无名篮。
        stock.setProductId(productId);
        stock.setPlotId(plotId);
        stock.setProductName(cropName);
        stock.setProductStock(stockQty);
        stock.setProductUnit("kg");
        stock.setIsEnd(0);
        stock.setOperatorId(userId);
        locationStockMapper.insert(stock);
    }

    /**
     * 按作物 {@code crop.related_product}（FK → {@code t_warehouse_product_info.id}）解析果蔬原料 product_id。
     *
     * <p>与 {@link org.dromara.djs.warehouse.veg.service.impl.VegetableHandleServiceImpl#resolveProductIdByCrop}
     * 同规则：作物→产品转换走 {@code t_plant_crop_info.related_product}，重量不变。客户未在 admin 作物录入页填
     * related_product（现网多为 NULL）时返 {@code null} 并 {@code log.warn}，不抛、不阻塞入库（月台中转篮退化为
     * plot 单键篮兜底）。protected 便于单测 stub。</p>
     *
     * <p>守门：解析出的产品必须是「果蔬原料」（{@code product_attr=2 且 belong_type='vegetable'}）。
     * {@code crop.related_product} 仅逻辑关联、无 DB FK 约束，可能脏值（指向不存在产品 / 误配成品 attr=1）。
     * 非果蔬原料一律返 {@code null}（流水 product_id 兜底空、入库记录「产品」列空），与下游
     * {@code MatFlowServiceImpl.bridgeMaterialInhouse / isVegSelfMaterial}（同 attr=2 门）行为一致，
     * 避免成品 id 漏到 {@code veg_receive_in} 流水。</p>
     *
     * @param cropId 作物 id（veg_receive.crop_id）
     * @return 解析出的果蔬原料 product_id；无映射 / 非果蔬原料时返 null
     */
    protected Long resolveProductIdByCrop(Long cropId) {
        if (cropId == null) {
            return null;
        }
        CropInfo crop = cropInfoMapper.selectById(cropId);
        if (crop == null || crop.getRelatedProduct() == null) {
            log.warn("月台默认库位解析：作物 related_product 未配置，弹层不预填库位、由工人手选 — cropId={}"
                + "（可在 admin 作物录入页填「关联产品」以启用预填）", cropId);
            return null;
        }
        Long relatedProductId = crop.getRelatedProduct();
        Long checked = resolveVegMaterialProductId(relatedProductId);
        if (checked == null) {
            log.warn("月台默认库位解析：作物 related_product={} 非果蔬原料（脏值 / 误配成品 / 产品已删），不预填库位"
                + " — cropId={}（请在 admin 作物录入页把「关联产品」改为 attr=2 的果蔬原料 SKU）", relatedProductId, cropId);
        }
        return checked;
    }

    /**
     * 新建库存篮行的展示名（row55）：能解析出产品就用产品名，否则回落作物名。
     *
     * <p>同一地块可能同时有红薯篮和红薯杆篮，名字必须跟 {@code product_id} 一致，否则两行同名无法分辨。</p>
     */
    protected String stockDisplayName(Long productId, String cropName) {
        if (productId == null) {
            return cropName;
        }
        ProductInfo p = productInfoMapper.selectById(productId);
        return p != null && p.getProductName() != null ? p.getProductName() : cropName;
    }

    /**
     * 收货落哪个产品（row55）——<b>写入侧一律收窄到一个确定的产品，不做「不按产品过滤」的兼容分支</b>。
     *
     * <p><b>为什么不能兼容</b>：曾经试过「mp 没传 productId 就不按产品过滤」，结果是
     * <b>写入侧不收窄、台账侧却收窄</b>——额度按整块地算（可入 100kg），而收货记录 product_id 落 NULL、
     * 列表用 {@code COALESCE(product_id, crop.related_product)} 把整笔挂到单一产品头上。
     * 实测后果：plot20 物理已收满 100kg，系统仍认为红薯杆还剩 30kg 可入（凭空多出 30kg 额度），
     * 同时红薯那张卡因为 pending 变成 −30 被 {@code WHERE ... &gt; 0} 静默吞掉、整张卡消失。
     * 这正是 CLAUDE.md §0 禁止的「两边兼容」。</p>
     *
     * <p><b>二选一的结果</b>：写入侧跟台账侧对齐（都按产品）。<b>放弃的是</b>「已发布的老版本小程序
     * 还能收多产品作物的货」——这类请求会被显式拒掉并提示更新，而不是悄悄记成一笔说不清是什么的货。
     * 单产品作物（现网 103 个作物里 102 个）没有歧义，照常放行、自动补上那个唯一产品，不受影响。
     * 读路径（{@code /plots} 不带 productId 返回整作物地块）保持宽松：只读，不会造假账。</p>
     */
    protected Long resolveReceiveProductId(Long cropId, Long requested) {
        List<CropProductVo> configured = cropProductService.listByCrop(cropId);
        if (requested != null) {
            // 传进来的产品必须真属于这个作物。不校验的话，客户端传任意 product_id 都会被原样落进收货记录，
            // 而库存篮走的是另一套解析，结果是「台账说 A、库存变 B」（QA 实测复现过）。
            //
            // 与毛菜处理侧 resolveRecordProductId 的差别（有意为之，别改成一样）：
            // 那边在「作物一条产品配置都没有」时会忽略 requested、回落 related_product；
            // 这边保留 requested —— 收货是把实物记进库存，调用方明确说了收的是什么就按什么记，
            // 没有配置可比对时没有依据去否定它。现网 103 个作物全部有配置，这一支不可达。
            boolean belongs = configured.isEmpty()
                || configured.stream().anyMatch(c -> requested.equals(c.getProductId()));
            if (!belongs) {
                throw new ServiceException("所选产品不在该作物的产品配置中，请刷新后重试", 400);
            }
            return requested;
        }
        if (configured.size() > 1) {
            throw new ServiceException("该作物配置了多个产品，请更新小程序到最新版本后再收货"
                + "（本次收的是哪个产品说不清，记下去会算错待入库量）", 400);
        }
        if (configured.size() == 1) {
            return configured.get(0).getProductId();
        }
        // 作物没配产品配置 → 回落旧的单映射 related_product（多产品出现之前就是这个口径）
        CropInfo crop = cropId == null ? null : cropInfoMapper.selectById(cropId);
        return crop != null ? crop.getRelatedProduct() : null;
    }

    /**
     * 「果蔬原料」守门（row55 抽出）：产品必须存在且 {@code product_attr=2}（原材料）+
     * {@code belong_type='vegetable'}，否则返 null。
     *
     * <p>下游 {@code MatFlowServiceImpl.bridgeMaterialInhouse / isVegSelfMaterial} 用同一道门，
     * 让成品 id 漏不进 {@code veg_receive_in} 流水和果蔬库存篮。
     * {@link #resolveProductIdByCrop}（按作物）和入库时按实收产品判定共用这一份规则，避免两处判定漂移。</p>
     */
    protected Long resolveVegMaterialProductId(Long productId) {
        if (productId == null) {
            return null;
        }
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null
            || product.getProductAttr() == null
            || product.getProductAttr() != 2
            || !CROP_BELONG_TYPE.equals(product.getBelongType())) {
            return null;
        }
        return productId;
    }

    /**
     * product 维度无库存行时 INSERT 新行（外购果蔬首次入某库位，照 PackingFlowServiceImpl 建账范式）。
     */
    private void insertProductStockRow(Long locationId, ProductInfo product,
                                       BigDecimal stockQty, Long userId) {
        LocationStock stock = new LocationStock();
        stock.setLocationId(locationId);
        stock.setProductId(product.getId());
        stock.setProductName(product.getProductName());
        stock.setProductStock(stockQty);
        stock.setProductUnit(product.getProductUnit());
        stock.setIsEnd(0);
        stock.setOperatorId(userId);
        locationStockMapper.insert(stock);
    }

    /**
     * 生成入库流水号（复用 SYS-INFRA-004 BizCodeService，果蔬收货恒入库 ioCode=IN）。
     *
     * <p>protected 便于单测固定返值。</p>
     */
    protected String generateFlowNo() {
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_IN);
        return bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx);
    }

}
